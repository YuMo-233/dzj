package cn.blockforge.generated.typewritertext;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

/**
 * 打字机样式携带的数据与运行期状态。
 *
 * <p>从 r8 起打字机不再是“内容类型”，而是组件样式上的一个字段：
 *
 * <pre>{@code
 * {"text":"你好，世界","typewriter":{"time":40,"command":"playsound ... %c"}}
 * }</pre>
 *
 * <p>因为是样式，它可以套在任何原版组件上（{@code text}、{@code translate}、
 * {@code score}、{@code keybind}、{@code selector}、{@code nbt}……），并且按样式的
 * 语义向下继承：父组件带上 {@code typewriter} 后，它和它的 {@code extra} 子组件
 * 会连成一整段文字逐字打出。
 *
 * <p>这个类既保存序列化字段（{@link #MAP_CODEC}），也保存纯客户端的播放进度
 * （起笔时刻、已揭示字数、已上报字数）。进度不参与序列化；客户端解码样式时会
 * 用一个同 session 的新实例重建进度。
 */
public final class TypewriterState {

    /** 组件样式里承载打字机参数的字段名。 */
    public static final String FIELD = "typewriter";

    /** time/interval 都未指定时，每个字符占用的默认 tick 数。 */
    public static final int DEFAULT_TICKS_PER_CHAR = 2;
    /** 触发指令长度上限。 */
    public static final int MAX_COMMAND = 512;
    /** 总时长上限（1 小时）。 */
    public static final int MAX_TIME = 72000;
    /** 每字符间隔上限（10 秒）。 */
    public static final int MAX_INTERVAL = 200;
    /** 单段文字可参与逐字的最大字符数（防御异常组件）。 */
    public static final int MAX_CHARS = 65536;

    private static final Codec<String> CAPPED_COMMAND = Codec.STRING.comapFlatMap(
            s -> s.length() <= MAX_COMMAND ? DataResult.success(s)
                    : DataResult.error(() -> "typewriter command too long (max " + MAX_COMMAND + ")"),
            s -> s);

    private static Codec<Integer> boundedTicks(int max, String name) {
        return Codec.INT.comapFlatMap(value -> {
            if (value < 0 || value > max) {
                return DataResult.error(() -> "typewriter " + name + " must be in 0.." + max + " (got " + value + ")");
            }
            return DataResult.success(value);
        }, value -> value);
    }

    /** {@code typewriter} 字段的对象形态；{@code time}/{@code interval} 的 0 一律表示“未指定”。 */
    public static final MapCodec<TypewriterState> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            boundedTicks(MAX_TIME, "time").optionalFieldOf("time", 0).forGetter(TypewriterState::time),
            boundedTicks(MAX_INTERVAL, "interval").optionalFieldOf("interval", 0).forGetter(TypewriterState::interval),
            CAPPED_COMMAND.optionalFieldOf("command", "").forGetter(TypewriterState::command),
            Codec.STRING.optionalFieldOf("session").forGetter(s -> Optional.of(s.session.toString()))
    ).apply(i, (time, interval, command, session) -> new TypewriterState(
            time, interval, command, session.map(TypewriterState::parseUuid).orElseGet(UUID::randomUUID))));

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return UUID.randomUUID();
        }
    }

    private final int time;
    private final int interval;
    private final String command;
    private final UUID session;

    // ---- 纯客户端运行期状态（不序列化） ----
    private long startNanos = -1L;
    private int revealed = -1;
    private int totalChars = 0;
    private int reported = 0;
    private boolean finished = false;

    public TypewriterState(int time, int interval, String command, UUID session) {
        this.time = Math.max(0, Math.min(time, MAX_TIME));
        this.interval = Math.max(0, Math.min(interval, MAX_INTERVAL));
        this.command = command == null ? "" : command;
        this.session = session == null ? UUID.randomUUID() : session;
        TypewriterRuntime.markAny();
        if (!this.command.isEmpty()) {
            TypewriterServerStore.registerIfTrusted(this.session, this.command);
        }
    }

    /** 全默认参数的实例（{@code "typewriter":{}}）。 */
    public static TypewriterState defaults() {
        return new TypewriterState(0, 0, "", UUID.randomUUID());
    }

    public int time() {
        return time;
    }

    public int interval() {
        return interval;
    }

    public String command() {
        return command;
    }

    public UUID session() {
        return session;
    }

    public int totalChars() {
        return totalChars;
    }

    // ------------------------------------------------------------------
    // 播放进度
    // ------------------------------------------------------------------

    /**
     * 每次渲染遍历前调用一次：按 {@code time}/{@code interval} 与这一遍统计到的
     * 总字数算出“现在应该显示到第几个字符”。进度只增不减。
     */
    synchronized void beginPass(int total) {
        this.totalChars = total;
        if (finished) {
            this.revealed = Integer.MAX_VALUE;
            TypewriterRuntime.untrack(this);
            return;
        }
        if (startNanos < 0L) {
            startNanos = System.nanoTime();
        }
        int ticks = time > 0
                ? time
                : Math.max(1, total * (interval > 0 ? interval : DEFAULT_TICKS_PER_CHAR));
        long elapsed = Math.max(0L, (System.nanoTime() - startNanos) / 50_000_000L);
        int target = elapsed >= ticks
                ? total
                : (int) Math.min(total, (elapsed * (long) total + ticks - 1L) / ticks);
        if (revealed < 0) {
            revealed = 0;
        }
        if (target > revealed) {
            revealed = target;
        }
        if (total <= 0 || revealed >= total) {
            TypewriterRuntime.untrack(this);
        } else {
            TypewriterRuntime.track(this);
        }
    }

    /** 当前已揭示的字符数；可能大于本遍总字数（收尾时表示“全部显示”）。 */
    synchronized int revealed() {
        return revealed < 0 ? 0 : revealed;
    }

    synchronized int reported() {
        return reported;
    }

    synchronized void noteReported(int index) {
        if (index > reported && index <= MAX_CHARS) {
            reported = index;
        }
    }

    /** /tw stop：立刻收尾，不再补发指令。 */
    synchronized void finishNow() {
        finished = true;
        revealed = Integer.MAX_VALUE;
        TypewriterRuntime.untrack(this);
    }

    @Override
    public String toString() {
        return "TypewriterState[time=" + time + ", interval=" + interval
                + ", command=" + (command.isEmpty() ? "-" : "yes") + "]";
    }
}
