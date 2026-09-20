package cn.blockforge.generated.typewritertext;

import net.minecraft.network.chat.Style;

/**
 * {@link Style} 上扭曲字段的访问口，做法与 {@link TypewriterStyleHolder} 一致：
 * 原版 {@code Style} 是 final 且字段全私有，所以由 {@code StyleMixin} 加隐藏字段并实现本接口。
 * mixin 没挂上时这里的 {@code instanceof} 判断会安全地退化成“没有扭曲样式”。
 */
public interface DistortStyleHolder {

    DistortSpec distort$spec();

    void distort$setSpec(DistortSpec spec);

    /** 取样式上的扭曲参数；没有则返回 null。 */
    static DistortSpec of(Style style) {
        if (style instanceof DistortStyleHolder holder) {
            return holder.distort$spec();
        }
        return null;
    }

    /** 给样式挂上扭曲参数（样式必须是新建/独占的实例）。 */
    static void set(Style style, DistortSpec spec) {
        if (style instanceof DistortStyleHolder holder) {
            holder.distort$setSpec(spec);
        }
    }

    /** 判等用的身份：扭曲参数按值比较（{@link DistortSpec} 是 record，天然值语义）。 */
    static boolean sameSpec(DistortSpec a, DistortSpec b) {
        return a == null ? b == null : a.equals(b);
    }
}
