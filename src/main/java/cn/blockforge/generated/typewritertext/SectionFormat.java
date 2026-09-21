package cn.blockforge.generated.typewritertext;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本里的 § 码扫描：在“纯字符串 → 可绘制文字”的路上，把 {@code §$}（波浪）、
 * {@code §^}（抖动）识别成效果开关，方便在存档名、书、告示牌、物品名这类只能用
 * 纯文本的地方直接开效果。
 *
 * <p>语义（沿用原版 § 格式码的直觉）：
 * <ul>
 *   <li>{@code §$} 波浪开，再遇一次 {@code §$} 波浪关；</li>
 *   <li>{@code §^} 抖动开，再遇一次 {@code §^} 抖动关；</li>
 *   <li>{@code §r} 全部关掉（回到这段文字原本的样式）；</li>
 *   <li>其它 {@code §x} 不解析——原版色码原样保留在文本里（JSON 里请直接用样式字段，
 *       别用 § 码，见 README 已知限制）。</li>
 * </ul>
 *
 * <p>它只做<b>扫描</b>（纯字符串进出，不碰 Minecraft 类，可脱离游戏跑确定性测试）；
 * 把每个 token 挂到哪个样式上由调用方决定（见 {@link TypewriterRender#plain}）。
 * § 码不产生可见字符，也不占打字机的逐字计数。
 */
final class SectionFormat {

    /** 节标记字符。 */
    static final char MARK = '\u00A7';

    /** 一个扫描结果：一段连续文本，或一个效果开关。 */
    enum Kind { TEXT, WAVE, JITTER, RESET }

    record Token(Kind kind, String text) {
    }

    private SectionFormat() {
    }

    /** 文本里是否可能出现 § 码（不含则整个解析器可以短路）。 */
    static boolean contains(String text) {
        return text.indexOf(MARK) >= 0;
    }

    /**
     * 扫描：连续普通字符合成一个 {@code TEXT} token；{@code §$ / §^ / §r} 单独成
     * 一个开关 token（不产生任何文本）；孤立的结尾 {@code §} 原样保留。
     */
    static List<Token> scan(String text) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder buffer = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == MARK && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == '$' || code == '^' || code == 'r') {
                    if (buffer != null) {
                        tokens.add(new Token(Kind.TEXT, buffer.toString()));
                        buffer = null;
                    }
                    tokens.add(new Token(switch (code) {
                        case '$' -> Kind.WAVE;
                        case '^' -> Kind.JITTER;
                        default -> Kind.RESET;
                    }, ""));
                    i++;
                    continue;
                }
            }
            if (buffer == null) {
                buffer = new StringBuilder();
            }
            buffer.append(c);
        }
        if (buffer != null) {
            tokens.add(new Token(Kind.TEXT, buffer.toString()));
        }
        return tokens;
    }
}