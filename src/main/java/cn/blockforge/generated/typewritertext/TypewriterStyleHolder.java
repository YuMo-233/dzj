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
}
