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
 * <p>打字机不是“内容类型”，而是组件样式上的一个字段：
 *
 * <pre>{@code
 * {"text":"你好，世界","typewriter":{"time":"0.25s","command":"playsound ... %c"}}
 * }</pre>
 *
 * <p>因为是样式，它可以套在任何原版组件上（{@code text}、{@code translate}、
 * {@code score}、{@code keybind}、{@code selector}、{@code nbt}……），并且按样式的
 * 语义向下继承：父组件带上 {@code typewriter} 后，它和它的 {@code extra} 子组件
 * 会连成一整段文字逐字打出。
 *
 * <p>{@code time} 控制的是**出字速度**（相邻两个字符间隔多久），不是整段总时长：
 * 数字按 tick 计且必须是整数，字符串可以带单位——{@code "0.25s"}（秒，可小数）或
 * {@code "4t"}（tick）；省略时每字符 {@value #DEFAULT_TICKS_PER_CHAR} tick。
 *
 * <p>这个类既保存序列化字段（{@link #MAP_CODEC}），也保存纯客户端的播放进度
 * （起笔时刻、已揭示字数、已上报字数）。进度不参与序列化；客户端解码样式时会
 * 用一个同 session 的新实例重建进度。
 */
public final class TypewriterState {

    /** 组件样式里承载打字机参数的字段名。 */
    public static final String FIELD = "typewriter";

    /** {@code time} 省略时，每个字符占用的默认 tick 数。 */
    public static final int DEFAULT_TICKS_PER_CHAR = 2;

    /** 出字速度上限：每字符 1 tick（20 字/秒）。 */
    public static final int MIN_TICKS_PER_CHAR = 1;
    /** 出字速度下限：每字符 200 tick（10 秒一个字）。 */
    public static final int MAX_TICKS_PER_CHAR = 200;

    /** 触发指令长度上限。 */
    public static final int MAX_COMMAND = 512;
    /** 单段文字可参与逐字的最大字符数（防御异常组件）。 */
    public static final int MAX_CHARS = 65536;

    private static final Codec<String> CAPPED_COMMAND = Codec.STRING.comapFlatMap(
            s -> s.length() <= MAX_COMMAND ? DataResult.success(s)
                    : DataResult.error(() -> "typewriter command too long (max " + MAX_COMMAND + ")"),
            s -> s);

    /**
     * {@code time} 字段：出字速度，归一化后存的是“每字符多少 tick”。
     * 写法（数字 / {@code "4t"} / {@code "0.25s"}）与报错口径见 {@link StyleTicks}。
     */
    private static final Codec<Integer> TICKS_PER_CHAR =
            StyleTicks.bounded("typewriter time", MIN_TICKS_PER_CHAR, MAX_TICKS_PER_CHAR);

    /** {@code typewriter} 字段的对象形态；三个字段都可省略。 */
    public static final MapCodec<TypewriterState> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            TICKS_PER_CHAR.optionalFieldOf("time").forGetter(s -> s.ticksPerChar == DEFAULT_TICKS_PER_CHAR
                    ? Optional.empty()
                    : Optional.of(s.ticksPerChar)),
            CAPPED_COMMAND.optionalFieldOf("command").forGetter(s -> s.command.isEmpty()
                    ? Optional.empty()
                    : Optional.of(s.command)),
            Codec.STRING.optionalFieldOf("session").forGetter(s -> Optional.of(s.session.toString()))
    ).apply(i, (time, command, session) -> new TypewriterState(
            time.orElse(DEFAULT_TICKS_PER_CHAR), command.orElse(""),
            session.map(TypewriterState::parseUuid).orElseGet(UUID::randomUUID))));

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return UUID.randomUUID();
        }
    }

    private final int ticksPerChar;
    private final String command;
    private final UUID session;

    // ---- 纯客户端运行期状态（不序列化） ----
    private long startNanos = -1L;
    private int revealed = -1;
    private int totalChars = 0;
    private int reported = 0;
    private boolean finished = false;

    public TypewriterState(int ticksPerChar, String command, UUID session) {
        this.ticksPerChar = Math.max(MIN_TICKS_PER_CHAR, Math.min(ticksPerChar, MAX_TICKS_PER_CHAR));
        this.command = command == null ? "" : command;
        this.session = session == null ? UUID.randomUUID() : session;
        TypewriterRuntime.markAny();
        if (!this.command.isEmpty()) {
            TypewriterServerStore.registerIfTrusted(this.session, this.command);
        }
    }

    /** 全默认参数的实例（{@code "typewriter":{}}）。 */
    public static TypewriterState defaults() {
        return new TypewriterState(DEFAULT_TICKS_PER_CHAR, "", UUID.randomUUID());
    }

    /** 出字速度：相邻两个字符间隔多少 tick，越小出字越快。 */
    public int ticksPerChar() {
        return ticksPerChar;
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
     * 每次渲染遍历前调用一次：按出字速度与这一遍统计到的总字数，算出“现在应该显示到第几个
     * 字符”。进度只增不减。
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
        // 起笔后的第 1 个 tick 内就吐出第一个字，之后每 ticksPerChar 个 tick 吐一个字
        double elapsedTicks = Math.max(0L, System.nanoTime() - startNanos) / 50_000_000.0;
        int target = (int) Math.min(total, Math.ceil(elapsedTicks / ticksPerChar));
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
        return "TypewriterState[ticksPerChar=" + ticksPerChar
                + ", command=" + (command.isEmpty() ? "-" : "yes") + "]";
    }
}
