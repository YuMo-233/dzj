package cn.blockforge.generated.typewritertext;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * 扭曲效果的参数编解码辅助。
 *
 * <p><b>为什么单独一个类、而不是 {@link DistortSpec} 的静态方法：</b>那会引入"外层类与嵌套类
 * 互相触发静态初始化"的循环——嵌套 record 的 {@code CODEC}（内层 clinit）调用外层的静态方法，
 * 而外层的 {@code MAP_CODEC}（外层 clinit）又引用嵌套 record 的 {@code CODEC}。一旦有人先碰到
 * 嵌套 record（例如 Java API 里的 {@code new DistortSpec.Wave(...)}），内层初始化进行中时外层
 * 才刚开始初始化，外层就会读到还是 null 的 {@code Wave.CODEC}，直接抛
 * {@link ExceptionInInitializerError}。放在这个独立类里就没有任何回指，初始化顺序是 DAG。
 */
final class DistortParams {

    /** 像素幅度：允许小数，范围 0.1–32。 */
    static Codec<Double> pixels(String label) {
        return Codec.DOUBLE.validate(v -> v >= 0.1 && v <= 32.0
                ? DataResult.success(v)
                : DataResult.error(() -> label + " must be in 0.1..32 (got " + v + ")"));
    }

    /** 倾斜角上限（度）：0（不倾斜）–45。 */
    static Codec<Double> tilt(String label) {
        return Codec.DOUBLE.validate(v -> v >= 0.0 && v <= 45.0
                ? DataResult.success(v)
                : DataResult.error(() -> label + " must be in 0..45 degrees (got " + v + ")"));
    }

    /** 波浪方向：{@code "y"}（默认，上下摆）/ {@code "x"}（左右摆），大小写不敏感。 */
    static Codec<Boolean> direction() {
        return Codec.STRING.comapFlatMap(raw -> switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "y", "vertical" -> DataResult.success(Boolean.TRUE);
            case "x", "horizontal" -> DataResult.success(Boolean.FALSE);
            default -> DataResult.error(() -> "typewriter wave direction must be \"x\" or \"y\", got \"" + raw + "\"");
        }, vertical -> vertical ? "y" : "x");
    }

    private DistortParams() {
    }
}
