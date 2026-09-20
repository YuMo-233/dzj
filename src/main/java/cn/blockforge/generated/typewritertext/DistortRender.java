package cn.blockforge.generated.typewritertext;

import net.minecraft.Util;
import net.minecraft.network.chat.Style;

/**
 * 扭曲样式的逐字形位移计算（纯客户端，服务端不参与）。
 *
 * <p>挂载点是 {@code Font$StringRenderOutput#accept}（见 {@code FontStringRenderOutputMixin}）：
 * 每画一个字调一次，参数给到"这个字形生效的样式""它在文本段里的下标""它在行内的像素横坐标"。
 *
 * <p>两种相位基准的分工：
 * <ul>
 *   <li><b>波浪</b>用**像素横坐标**当空间相位，所以它沿着整行连续传播，不受组件被拆成几段影响；</li>
 *   <li><b>漂移/抖动</b>用**参数哈希 + 字符下标**取固定种子，同一段文字每次看到的摆动方式一致，
 *       不会一帧一个样地闪。</li>
 * </ul>
 *
 * <p>时间基准是 {@link Util#getMillis()}（毫秒），配置里的周期按 tick 记（1 tick = 50ms）。
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

    /** 计算某个字形该偏移多少；样式上没有扭曲参数时返回 {@link Offset#NONE}。 */
    public static Offset at(Style style, int index, float cursorX) {
        DistortSpec spec = DistortStyleHolder.of(style);
        if (spec == null) {
            return Offset.NONE;
        }
        double millis = Util.getMillis();
        long seed = spec.hashCode() * 31L + index;
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
        if (spec.jitter().isPresent()) {
            DistortSpec.Jitter jitter = spec.jitter().get();
            double omega = TAU / ticksToMillis(jitter.period());
            dx += jitter.radius() * smooth(millis, omega, seed);
            dy += jitter.radius() * smooth(millis, omega, seed + 0x9E3779B9L);
        }
        if (spec.shake().isPresent()) {
            DistortSpec.Shake shake = spec.shake().get();
            double omega = TAU / ticksToMillis(shake.period());
            dx += shake.amplitude() * rough(millis, omega, seed);
            dy += shake.amplitude() * rough(millis, omega, seed + 0x85EBCA6BL);
        }
        return dx == 0.0 && dy == 0.0 ? Offset.NONE : new Offset((float) dx, (float) dy);
    }

    private static double ticksToMillis(int ticks) {
        return Math.max(1, ticks) * MILLIS_PER_TICK;
    }

    /** 平滑游动：两个不同频率的正弦叠加，值域约 ±0.9，随种子改变相位。 */
    private static double smooth(double millis, double omega, long seed) {
        return 0.55 * Math.sin(millis * omega + phase(seed))
                + 0.35 * Math.sin(millis * omega * 0.37 + phase(seed * 3L + 1L));
    }

    /** 剧烈抖动：频率更高，并叠一个带折返的波形（{@code b·|b|}）做出"频闪"的毛躁感。 */
    private static double rough(double millis, double omega, long seed) {
        double a = Math.sin(millis * omega + phase(seed));
        double b = Math.sin(millis * omega * 2.3 + phase(seed * 7L + 3L));
        return 0.65 * a + 0.35 * b * Math.abs(b);
    }

    /** 把种子摊成 [0, 2π) 上的相位。 */
    private static double phase(long seed) {
        long mixed = seed * 0x9E3779B97F4A7C15L;
        return (mixed >>> 48) / 65536.0 * TAU;
    }
}
