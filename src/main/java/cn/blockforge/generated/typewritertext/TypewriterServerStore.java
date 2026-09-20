package cn.blockforge.generated.typewritertext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端打字机会话表：{@code session id -> (command, text)}。
 *
 * <p>安全模型：指令文本只在服务端解码打字机样式时登记，客户端上报的永远是
 * “会话 id + 第几个字符 + 该字符”，无法注入任意命令（字符会被消毒成单个可打印字符）。
 * 登记受线程信任约束——只有服务端线程上的解码（{@code /tellraw}、{@code /title}
 * 等命令路径，以及本模组的 Java API）会写入登记表；告示牌之类由客户端提交的组件
 * 在 Netty 线程解码，不会登记，杜绝“借打字机样式给自己悄悄执行命令”。
 *
 * <p>执行时以观看者身份、权限封顶 2 级、输出完全静默
 * （{@code withSuppressedOutput}：{@code /playsound} 之类不会再给玩家回显
 * “已播放声音”的提示）。
 */
public final class TypewriterServerStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("typewriter_text");

    /** 会话保留 10 分钟；单条目 command 已封顶 512 字符，总量有界。 */
    private static final long TTL_MS = 10 * 60 * 1000L;
    private static final int MAX_SESSIONS = 8192;
    /** 每名玩家每秒最多回报的字符触发数。 */
    private static final int MAX_CHARS_PER_SECOND = 100;
    // 这里不限制“跳字”幅度：掉帧、切标签页、聊天栏合并都会一次跳过很多字符，
    // 卡死跳字会让整段动画后半程的指令永久不再触发。指令文本始终留在服务端登记表里，
    // 客户端只能推进序号；重复/迟到的包由“序号必须严格递增”挡掉，越界刷量由速率上限兜住。

    private record Entry(String command, long createdAt) {
    }

    private static final Map<UUID, Entry> SESSIONS = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Entry> eldest) {
                    return size() > MAX_SESSIONS;
                }
            });

    /** 玩家 -> (会话 -> 最后回报序号)。 */
    private static final Map<UUID, Map<UUID, Integer>> LAST_INDEX = new ConcurrentHashMap<>();
    /** 玩家 -> [窗口起点, 窗口内计数]。 */
    private static final Map<UUID, long[]> RATE = new ConcurrentHashMap<>();

    /** 当前运行的服务器（两端都会设置；纯客户端为 null）。 */
    private static volatile MinecraftServer server;

    private TypewriterServerStore() {
    }

    /** 当前服务器实例（客户端集成服为主入口设置的实例；专用/加载完成时为事件里的实例）。 */
    public static MinecraftServer currentServer() {
        return server;
    }

    /** 由主入口在服务器启动/停止之外主动绑定服务器实例。 */
    public static void bindServer(MinecraftServer value) {
        server = value;
    }

    // ------------------------------------------------------------------
    // 登记
    // ------------------------------------------------------------------

    /**
     * 由 {@link TypewriterState} 构造时调用。仅当“此刻正处在服务端线程”
     * （即服务端逻辑自己解码组件）时登记，客户端提交的解码一律忽略。
     */
    static void registerIfTrusted(UUID session, String command) {
        try {
            MinecraftServer current = server;
            if (current == null || !current.isSameThread()) {
                return;
            }
            SESSIONS.put(session, new Entry(command, System.currentTimeMillis()));
        } catch (Throwable t) {
            // 登记表故障绝不能拖垮组件解码
            LOGGER.warn("[typewriter_text] 登记打字机会话失败", t);
        }
    }

    // ------------------------------------------------------------------
    // 执行
    // ------------------------------------------------------------------

    /** 处理客户端的“已打出第 index 个字符”回报（已在服务端主线程）。 */
    public static void handleCharTyped(ServerPlayer player, TypewriterPayloads.CharTyped payload) {
        Entry entry = SESSIONS.get(payload.session());
        if (entry == null || entry.command().isEmpty()) {
            return;
        }
        int index = payload.index();
        if (index < 1 || index > TypewriterState.MAX_CHARS) {
            return;
        }
        // 全局速率限制
        long now = System.currentTimeMillis();
        long[] rate = RATE.computeIfAbsent(player.getUUID(), k -> new long[] {now, 0});
        synchronized (rate) {
            if (now - rate[0] >= 1000L) {
                rate[0] = now;
                rate[1] = 0;
            }
            if (rate[1] >= MAX_CHARS_PER_SECOND) {
                return;
            }
            rate[1]++;
        }
        // 会话内单调递增，小步追赶容忍乱序
        Map<UUID, Integer> perSession =
                LAST_INDEX.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
        boolean[] accepted = new boolean[1];
        perSession.compute(payload.session(), (session, last) -> {
            if (last == null || index > last) {
                accepted[0] = true;
                return index;
            }
            return last;
        });
        if (!accepted[0]) {
            return;
        }
        // 组装指令文本：字符来自客户端回报，但已被消毒成单个可打印字符
        String typed = sanitizeChar(payload.ch());
        int total = Math.max(payload.total(), 1);
        String resolved = entry.command()
                .replace("%c", typed)
                .replace("%i", Integer.toString(index))
                .replace("%n", Integer.toString(total));
        if (resolved.isBlank() || resolved.length() > 1024) {
            return;
        }
        CommandSourceStack source = player.createCommandSourceStack()
                .withMaximumPermission(2)
                .withSuppressedOutput();
        try {
            player.server.getCommands().performPrefixedCommand(source, resolved);
        } catch (RuntimeException e) {
            // 指令不合法或执行异常：静默失败（不给玩家任何提示，也不刷屏）
            LOGGER.debug("[typewriter_text] 打字机指令执行异常: {}", e.getMessage());
        }
    }

    private static String sanitizeChar(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        char c = raw.charAt(0);
        if (c < 0x20 || c == 0x7F || c == '\u00A7') {
            return "";
        }
        return String.valueOf(c);
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        server = null;
        SESSIONS.clear();
        LAST_INDEX.clear();
        RATE.clear();
    }

    /** 周期性清理（由主入口每 100 tick 调一次）。 */
    public static void sweep(MinecraftServer server) {
        long cutoff = System.currentTimeMillis() - TTL_MS;
        SESSIONS.entrySet().removeIf(e -> e.getValue().createdAt() < cutoff);
        Iterator<UUID> players = LAST_INDEX.keySet().iterator();
        while (players.hasNext()) {
            if (server.getPlayerList().getPlayer(players.next()) == null) {
                players.remove();
            }
        }
        Iterator<UUID> ratePlayers = RATE.keySet().iterator();
        while (ratePlayers.hasNext()) {
            if (server.getPlayerList().getPlayer(ratePlayers.next()) == null) {
                ratePlayers.remove();
            }
        }
    }
}
