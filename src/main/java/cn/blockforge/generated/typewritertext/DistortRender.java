package cn.blockforge.generated.typewritertext;

/**
 * 扭曲样式的逐字形位移计算（纯函数，服务端不参与）。
 *
 * <p>挂载点是 {@code Font$StringRenderOutput#accept}（见 {@code FontStringRenderOutputMixin}）：
 * 每画一个字调一次，参数给到"这个字形生效的样式""它在文本段里的下标""它在行内的像素横坐标"。
 *
 * <p>两种相位基准的分工：
 * <ul>
 *   <li><b>波浪</b>用**像素横坐标**当空间相位，所以它沿着整行连续传播，不受组件被拆成几段影响；</li>
 *   <li><b>漂移</b>用**参数哈希 + 字符下标**取固定种子，同一段文字每次看到的摆动方式一致，
 *       不会一帧一个样地闪。</li>
 * </ul>
 *
 * <p>时间基准是毫秒（配置里的周期按 tick 记，1 tick = 50ms），由调用方传入。
 */
public final class DistortRender {

    private static final double TAU = Math.PI * 2.0;
    private static final double MILLIS_PER_TICK = 50.0;

    /** 一个字形要加的位移，单位是 GUI 像素。 */
    public record Offset(float x, float y) {
        public static final Offset NONE = new Offset(0.0f, 0.0f);
    }

    private DistortRender() {
    }

    /**
     * 计算某个字形该偏移多少；{@code spec} 为 null（样式上没有扭曲参数）时返回 {@link Offset#NONE}。
     *
     * <p>{@code millis} 由调用方传入（客户端用 {@code Util.getMillis()}），这样本方法是纯函数、
     * 可以脱离游戏做确定性验证。
     *
     * @param index   字符在所在文本段里的下标，用于给随机类效果取固定种子
     * @param cursorX 字形在行内的像素横坐标，用于给波浪取空间相位
     */
    public static Offset at(DistortSpec spec, int index, float cursorX, double millis) {
        if (spec == null) {
            return Offset.NONE;
        }
        double dx = 0.0;
        double dy = 0.0;

        if (spec.wave().isPresent()) {
            DistortSpec.Wave wave = spec.wave().get();
            double phase = TAU * (millis / ticksToMillis(wave.period()) + cursorX / wave.wavelength());
            double value = wave.amplitude() * Math.sin(phase);
            if (wave.vertical()) {
                dy += value;
            } else {
                dx += value;
            }
        }
        // 种子只跟"这个效果自己的参数 + 字符下标"有关，不掺别的效果：这样叠加时每个效果
        // 的位移互不干扰，总位移严格等于各自位移相加。
        if (spec.jitter().isPresent()) {
            DistortSpec.Jitter jitter = spec.jitter().get();
            long seed = jitter.hashCode() * 31L + index;
            double omega = TAU / ticksToMillis(jitter.period());
            dx += jitter.radius() * smooth(millis, omega, seed);
            dy += jitter.radius() * smooth(millis, omega, seed + 0x9E3779B9L);
        }
        return dx == 0.0 && dy == 0.0 ? Offset.NONE : new Offset((float) dx, (float) dy);
    }

    /**
     * 波浪的倾斜角（弧度）：跟随位移沿行方向的斜率——字会顺着波形往凸起那一侧歪；
     * {@code tilt}（度）是角度上限，0 = 不倾斜。{@code spec} 无波浪时返回 0。
     */
    public static float tiltRadians(DistortSpec spec, float cursorX, double millis) {
        if (spec == null || spec.wave().isEmpty()) {
            return 0.0f;
        }
        DistortSpec.Wave wave = spec.wave().get();
        double cap = clamp(wave.tilt(), 0.0, 45.0);
        if (cap == 0.0) {
            return 0.0f;
        }
        // d(位移)/d(像素横坐标) = amplitude × 2π/wavelength × cos(phase)；倾斜角取其反正切
        double k = wave.amplitude() * TAU / Math.max(1, wave.wavelength());
        double phase = TAU * (millis / ticksToMillis(wave.period()) + cursorX / wave.wavelength());
        double angle = Math.atan(k * Math.cos(phase));
        double limit = Math.toRadians(clamp(cap, 0.0, 45.0));
        return (float) clamp(angle, -limit, limit);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static double ticksToMillis(int ticks) {
        return Math.max(1, ticks) * MILLIS_PER_TICK;
    }

    /**
     * 平滑游动：两个不同频率的正弦叠加，值域约 ±0.9，随种子改变相位。
     * 周期调小就是快速抖动——同一条曲线上取更密的一段，方向反转次数随之上去。
     */
    private static double smooth(double millis, double omega, long seed) {
        return 0.55 * Math.sin(millis * omega + phase(seed))
                + 0.35 * Math.sin(millis * omega * 0.37 + phase(seed * 3L + 1L));
    }

    /** 把种子摊成 [0, 2π) 上的相位。 */
    private static double phase(long seed) {
        long mixed = seed * 0x9E3779B97F4A7C15L;
        return (mixed >>> 48) / 65536.0 * TAU;
    }
}
