package cn.blockforge.generated.typewritertext;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * “带单位的时间”编解码：数字按 tick 计（整数），字符串可带单位后缀——{@code "4t"}（tick）、
 * {@code "0.25s"}（秒，可小数）；不带单位按 tick 计。归一化后一律是 tick 整数，写出时也是
 * {@code "<n>t"}。
 *
 * <p>打字机的出字速度与扭曲效果的周期共用它，写法与报错口径保持一致。
 */
final class StyleTicks {

    /** 1 秒是多少 tick。 */
    static final double PER_SECOND = 20.0;

    private static final String INTEGRAL_HINT =
            " as a number counts ticks and must be a whole number; "
                    + "write seconds for fractions, e.g. \"0.25s\"";

    private StyleTicks() {
    }

    /**
     * 生成字段编解码：接受数字或带 {@code t}/{@code s} 后缀的字符串，归一化到
     * {@code min..max} tick；超出范围、tick 写成小数、单位不认识都会给出明确错误。
     *
     * @param label 出错信息里的字段名，例如 {@code typewriter time}
     */
    static Codec<Integer> bounded(String label, int min, int max) {
        return Codec.either(Codec.DOUBLE, Codec.STRING)
                .comapFlatMap(value -> parse(value, label, min, max), StyleTicks::format);
    }

    private static Either<Double, String> format(int ticks) {
        return Either.right(ticks + "t");
    }

    private static DataResult<Integer> parse(Either<Double, String> raw, String label, int min, int max) {
        Double number = raw.left().orElse(null);
        if (number != null) {
            return fromTicks(number, label, min, max, true);
        }
        String text = raw.right().orElse("").trim();
        int split = 0;
        while (split < text.length() && !Character.isLetter(text.charAt(split))) {
            split++;
        }
        String amount = text.substring(0, split).trim();
        String unit = text.substring(split).trim().toLowerCase(Locale.ROOT);
        double value;
        try {
            value = Double.parseDouble(amount);
        } catch (NumberFormatException e) {
            return DataResult.error(() -> label + " is not a number: \"" + text + "\"");
        }
        if (isSeconds(unit)) {
            return fromTicks(value * PER_SECOND, label, min, max, false);
        }
        if (!isTicks(unit)) {
            return DataResult.error(() -> label + " unit must be t (ticks) or s (seconds), got \"" + unit + "\"");
        }
        return fromTicks(value, label, min, max, true);
    }

    /** 归一化并做范围校验；{@code integral} 为真时要求整数 tick（秒换算过来的值允许小数）。 */
    private static DataResult<Integer> fromTicks(double ticks, String label, int min, int max, boolean integral) {
        if (integral && ticks != Math.rint(ticks)) {
            return DataResult.error(() -> label + INTEGRAL_HINT);
        }
        long rounded = Math.round(ticks);
        if (rounded < min || rounded > max) {
            return DataResult.error(() -> label + " must be between " + min + " and " + max
                    + " ticks (got " + rounded + ")");
        }
        return DataResult.success((int) rounded);
    }

    private static boolean isSeconds(String unit) {
        return unit.equals("s") || unit.equals("sec") || unit.equals("second") || unit.equals("seconds");
    }

    private static boolean isTicks(String unit) {
        return unit.isEmpty() || unit.equals("t") || unit.equals("tick") || unit.equals("ticks");
    }
}
