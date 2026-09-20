package cn.blockforge.generated.typewritertext;

import net.minecraft.network.chat.Style;

/**
 * {@link Style} 上打字机字段的访问口。
 *
 * <p>原版 {@code Style} 是 final 的、字段全部私有，所以由 {@code StyleMixin} 给
 * 它加一个隐藏字段并实现本接口。运行时 {@code (style instanceof TypewriterStyleHolder)}
 * 恒为 true；万一 mixin 没挂上，这里的 {@code instanceof} 判断会安全地退化成
 * “没有打字机样式”，不会抛异常。
 */
public interface TypewriterStyleHolder {

    TypewriterState typewriter$state();

    void typewriter$setState(TypewriterState state);

    /** 取样式上的打字机数据；没有则返回 null。 */
    static TypewriterState of(Style style) {
        if (style instanceof TypewriterStyleHolder holder) {
            return holder.typewriter$state();
        }
        return null;
    }

    /** 给样式挂上打字机数据（样式必须是新建/独占的实例）。 */
    static void set(Style style, TypewriterState state) {
        if (style instanceof TypewriterStyleHolder holder) {
            holder.typewriter$setState(state);
        }
    }

    /**
     * 判等用的打字机身份：只看出字速度与逐字指令；一边有一边没有，判为不同。
     *
     * <p>{@code session} 与播放进度刻意不参与——session 是解码时随机生成的，同一段 JSON
     * 生成的两件物品 session 也不同，算进身份会让本该堆叠的物品永远堆不起来。
     */
    static boolean sameIdentity(TypewriterState a, TypewriterState b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.ticksPerChar() == b.ticksPerChar() && a.command().equals(b.command());
    }
}
