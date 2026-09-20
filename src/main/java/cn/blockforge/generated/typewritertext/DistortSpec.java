package cn.blockforge.generated.typewritertext;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * 扭曲样式（{@code distort}）的参数：让文字在**排版位置不变**的前提下，逐字符做位移形变。
 *
 * <p>{@code distort} 是组件样式上的独立字段，和 {@code typewriter} 各自能单独用，也能叠加：
 *
 * <pre>{@code
 * {"text":"别再看了","distort":{"wave":{"amplitude":2,"period":"0.8s"},
 *                               "jitter":{"radius":1.2}}}
 * }</pre>
 *
 * <p>幅度单位是 GUI 像素；周期沿用语料里的“带单位时间”（{@code "0.8s"} / {@code "16t"}，
 * 见 {@link StyleTicks}）。两个效果都可省略，全省略等同于没写这个字段。
 *
 * <p>纯客户端效果：服务端只当数据存/传，不参与计算；漂移用**参数哈希做种子**，
 * 所以同一段文字每次看到的摆动方式一致，不会闪烁。
 */
public record DistortSpec(Optional<Wave> wave, Optional<Jitter> jitter) {

    /** 组件样式里承载扭曲参数的字段名。 */
    public static final String FIELD = "distort";

    /** 波浪：位移 = amplitude × sin(2π × (时间/period + 像素横坐标/wavelength))。 */
    public record Wave(double amplitude, int period, int wavelength, boolean vertical) {
        static final MapCodec<Wave> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                DistortParams.pixels("typewriter wave amplitude").optionalFieldOf("amplitude", 2.0).forGetter(Wave::amplitude),
                StyleTicks.bounded("typewriter wave period", 1, 1200)
                        .optionalFieldOf("period", 20).forGetter(Wave::period),
                Codec.INT.validate(v -> v >= 1 && v <= 512
                                ? DataResult.success(v)
                                : DataResult.error(() -> "typewriter wave wavelength must be in 1..512 (got " + v + ")"))
                        .optionalFieldOf("wavelength", 24).forGetter(Wave::wavelength),
                DistortParams.direction().optionalFieldOf("direction", Boolean.TRUE).forGetter(Wave::vertical)
        ).apply(i, Wave::new));
    }

    /** 漂移：在自己半径内游动，随机但平滑；`period` 调小就变成快速抖动。 */
    public record Jitter(double radius, int period) {
        static final MapCodec<Jitter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                DistortParams.pixels("typewriter jitter radius").optionalFieldOf("radius", 1.5).forGetter(Jitter::radius),
                StyleTicks.bounded("typewriter jitter period", 1, 1200)
                        .optionalFieldOf("period", 50).forGetter(Jitter::period)
        ).apply(i, Jitter::new));
    }

    public static final MapCodec<DistortSpec> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Wave.CODEC.codec().optionalFieldOf("wave").forGetter(DistortSpec::wave),
            Jitter.CODEC.codec().optionalFieldOf("jitter").forGetter(DistortSpec::jitter)
    ).apply(i, DistortSpec::new));

    /** 两个效果都没写：等同没有扭曲样式。 */
    public boolean isEmpty() {
        return wave.isEmpty() && jitter.isEmpty();
    }
}
