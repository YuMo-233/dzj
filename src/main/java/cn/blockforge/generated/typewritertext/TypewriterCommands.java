package cn.blockforge.generated.typewritertext;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelSettings;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Field;
import java.util.Collection;

/**
 * 附加的便捷命令（打字机本身是组件样式，原版 {@code /tellraw}、{@code /title}、
 * {@code /bossbar} 等即可直接使用，不依赖这里的任何命令）：
 * <pre>
 * /tw show &lt;targets&gt; &lt;组件&gt;   把组件发到目标玩家的 action bar
 * /tw stop &lt;targets&gt;           让目标玩家正在播放的打字机立即收尾
 * </pre>
 * 两个命令都完全静默，不打断聊天栏（与 {@code /tellraw} 的行为一致）。
 * {@code /typewriter} 是 {@code /tw} 的同义长名。
 */
public final class TypewriterCommands {
    private TypewriterCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        registerRoot(event.getDispatcher(), event.getBuildContext(), "typewriter");
        registerRoot(event.getDispatcher(), event.getBuildContext(), "tw");
    }

    private static void registerRoot(CommandDispatcher<CommandSourceStack> dispatcher,
                                     net.minecraft.commands.CommandBuildContext buildContext,
                                     String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("show")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("component", ComponentArgument.textComponent(buildContext))
                                        .executes(TypewriterCommands::show))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(TypewriterCommands::stop)))
                .then(Commands.literal("worldname")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(TypewriterCommands::worldname)));
        dispatcher.register(root);
    }

    /**
     * 向 action bar 发送组件：等价于 {@code /title <targets> actionbar <component>}，
     * 组件里可以带打字机样式（{@code "typewriter":{...}}），逐字动画完全由客户端驱动。
     */
    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Component component = ComponentArgument.getComponent(context, "component");
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer player : targets) {
            player.connection.send(new ClientboundSetActionBarTextPacket(component));
        }
        return targets.size();
    }

    private static int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer player : targets) {
            PacketDistributor.sendToPlayer(player, new TypewriterPayloads.Stop());
        }
        return targets.size();
    }

    /**
     * 改当前存档在“世界列表”里显示的名字：写进 {@code level.dat → Data.LevelName}。
     *
     * <p>命令输入框和存档名输入框一样会过滤 {@code §}，所以用 {@code &} 当转义：
     * {@code &$}→{@code §$}（波浪）、{@code &^}→{@code §^}（抖动）、{@code &r}→{@code §r}（关闭），
     * 其余字符原样。名字只在渲染时被解析成效果，数据里保留 {@code §} 码。
     *
     * <p>{@code LevelName} 存在 {@code PrimaryLevelData → LevelSettings.levelName}（final 字段），
     * {@code WorldData} 接口只给 getter 没有 setter，所以这里用反射改字段后落盘。
     */
    private static int worldname(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        String stored = translate(StringArgumentType.getString(context, "name"));
        try {
            LevelSettings settings = server.getWorldData().getLevelSettings();
            Field field = LevelSettings.class.getDeclaredField("levelName");
            field.setAccessible(true);
            field.set(settings, stored);
            // 界面上世界名是客户端世界列表读 level.dat 渲染的，服务端改完直接存盘即可
            server.saveAllChunks(false, false, false);
        } catch (Throwable t) {
            source.sendFailure(Component.literal("改存档名失败：" + t));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("存档名称已改为：" + stored), true);
        return 1;
    }

    /** 命令文本里的 {@code &$ / &^ / &r} 转成真正的 § 码（命令输入框打不进 {@code §}）。 */
    private static String translate(String raw) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '&' && i + 1 < raw.length()) {
                char next = raw.charAt(i + 1);
                if (next == '$' || next == '^' || next == 'r' || next == 'R') {
                    out.append(SectionFormat.MARK).append(next);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
