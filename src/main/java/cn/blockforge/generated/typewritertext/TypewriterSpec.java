package cn.blockforge.generated.typewritertext;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 打字机样式的 Java 构建入口（给写代码的服务端模组用）。
 *
 * <p>产出的就是普通的 {@link Component}——文字走原版内容类型，打字机效果挂在它的
 * {@link Style} 上，所以可以和任何原版组件、任何原版样式字段自由组合，也可以塞进
 * 任何原版接口：{@code player.sendMessage(...)}、
 * {@code ClientboundSetActionBarTextPacket}、物品的 {@code custom_name}/{@code lore}
 * 数据组件……
 *
 * <pre>{@code
 * Component msg = TypewriterSpec.builder()
 *         .text("任务开始……")
 *         .time(60)
 *         .command("playsound minecraft:block.note_block.hat master @s ~ ~ ~ 0.2 1.5")
 *         .withStyle(ChatFormatting.GOLD)
 *         .build();
 * }</pre>
 */
public final class TypewriterSpec {

    private TypewriterSpec() {
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 便捷构造：把一段文字包成带默认打字机样式的组件。 */
    public static MutableComponent of(String text) {
        return builder().text(text).build();
    }

    /** 把 {@code &a} 这类旧式颜色码转成原版 § 码。 */
    public static String applyColorCodes(String raw) {
        if (raw == null || raw.indexOf('&') < 0) {
            return raw == null ? "" : raw;
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '&' && i + 1 < raw.length()) {
                ChatFormatting format = ChatFormatting.getByCode(raw.charAt(++i));
                if (format != null) {
                    out.append('\u00A7').append(format.getChar());
                    continue;
                }
            }
            out.append(ch);
        }
        return out.toString();
    }

    public static final class Builder {
        private String text = "";
        private String translateKey = "";
        private final List<Component> translateArgs = new ArrayList<>();
        private int time;
        private int interval;
        private String command = "";
        private final List<Component> extra = new ArrayList<>();
        private Style style = Style.EMPTY;

        /** 逐字显示的文字（与 translate 二选一，给了 text 则优先）。 */
        public Builder text(String value) {
            this.text = value == null ? "" : value;
            return this;
        }

        /** 用原版翻译键取词（参数组件会展平成文字参与逐字显示）。 */
        public Builder translate(String key, Component... args) {
            this.translateKey = key == null ? "" : key;
            translateArgs.clear();
            translateArgs.addAll(List.of(args));
            return this;
        }

        /** 总时长（tick）。0 表示未指定，缺省按每字符 2 tick。 */
        public Builder time(int ticks) {
            this.time = Math.max(0, Math.min(ticks, TypewriterState.MAX_TIME));
            return this;
        }

        /** 每字符 tick 数。0 表示未指定（与 time 二选一）。 */
        public Builder interval(int ticksPerChar) {
            this.interval = Math.max(0, Math.min(ticksPerChar, TypewriterState.MAX_INTERVAL));
            return this;
        }

        /** 每打出一个字符触发一次的指令；{@code %c} 当前字符、{@code %i} 序号、{@code %n} 总数。 */
        public Builder command(String value) {
            this.command = value == null ? "" : value;
            return this;
        }

        /** 追加原版子组件（与 JSON 的 {@code extra} 等价，随打字机主体一起逐字显示）。 */
        public Builder append(Component child) {
            if (child != null) {
                extra.add(child);
            }
            return this;
        }

        /** 组件样式（颜色、加粗、点击/悬停事件等，走原版 Style 体系）。 */
        public Builder withStyle(Style value) {
            this.style = value == null ? Style.EMPTY : value;
            return this;
        }

        /** 组件样式（旧式颜色枚举）。 */
        public Builder withStyle(ChatFormatting value) {
            return value == null ? this : withStyle(Style.EMPTY.applyFormat(value));
        }

        /** 产出组件：打字机数据挂在样式上，内容与子组件完全走原版通道。 */
        public MutableComponent build() {
            TypewriterState state = new TypewriterState(time, interval, command, UUID.randomUUID());
            Style styled = TypewriterStyleCodec.attach(style, state);
            MutableComponent component = translateKey.isEmpty()
                    ? Component.literal(text)
                    : Component.translatable(translateKey, translateArgs.toArray(new Component[0]));
            extra.forEach(component::append);
            return component.setStyle(styled);
        }
    }
}
