package cn.blockforge.generated.typewritertext.mixin;

import cn.blockforge.generated.typewritertext.DistortSpec;
import cn.blockforge.generated.typewritertext.DistortStyleHolder;
import cn.blockforge.generated.typewritertext.TypewriterState;
import cn.blockforge.generated.typewritertext.TypewriterStyleHolder;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给原版 {@link Style} 加两个隐藏字段（打字机与扭曲，见 {@link TypewriterStyleHolder} 与
 * {@link DistortStyleHolder}），并让它们参与样式判等与样式合并。
 *
 * <p>为什么必须动判等：原版 {@code Style.equals} 只比那十个原版字段，隐藏字段不在其中，
 * 于是"只差打字机/扭曲参数"的两个样式会被判为相等——物品因此堆叠（堆叠时只保留其中一份数据，
 * 另一份静默丢失）、聊天栏的连续消息因此被并成一条 "2x"。这里在原版判等返回 true 时补一次
 * 身份比较：打字机看出字速度与逐字指令，扭曲按参数值整体比较。
 *
 * <p>为什么必须动合并：原版 {@code applyTo} 会按原版字段挑一个或新建一个 Style，
 * {@code withStyle(非空样式)}、{@code Component.visit} 里的样式合并都会把隐藏字段丢掉。
 *
 * <p>{@code hashCode} 不动：判等只会变得更严格，相等的两个样式必然仍满足原版的哈希一致要求
 * （不相等的两个样式哈希相撞是合法情形）。
 */
@Mixin(Style.class)
public abstract class StyleMixin implements TypewriterStyleHolder, DistortStyleHolder {

    @Unique
    private TypewriterState typewriter$state;

    @Unique
    private DistortSpec distort$spec;

    @Override
    public TypewriterState typewriter$state() {
        return this.typewriter$state;
    }

    @Override
    public void typewriter$setState(TypewriterState state) {
        this.typewriter$state = state;
    }

    @Override
    public DistortSpec distort$spec() {
        return this.distort$spec;
    }

    @Override
    public void distort$setSpec(DistortSpec spec) {
        this.distort$spec = spec;
    }

    /** 原版判等只看原版字段，这里补上打字机身份与扭曲参数。 */
    @Inject(method = "equals", at = @At("RETURN"), cancellable = true)
    private void typewriter$compareState(Object other, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || !(other instanceof Style otherStyle)) {
            return;
        }
        if (!TypewriterStyleHolder.sameIdentity(this.typewriter$state, TypewriterStyleHolder.of(otherStyle))
                || !DistortStyleHolder.sameSpec(this.distort$spec, DistortStyleHolder.of(otherStyle))) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 样式合并时把两个隐藏字段都带过去，优先级与原版字段一致：{@code this}（被应用的样式）
     * 胜过参数（被继承的样式）。
     *
     * <p>带隐藏字段的一侧必然不是 {@code EMPTY} 单例，此时原版返回的一定是新建实例；
     * 保留 {@code isEmpty()} 与"结果已有值就不覆盖"这两道判断，只为防止以后改坏
     * {@code copyOf} 把数据写进全局单例（那会让全游戏文字都变样）。
     */
    @Inject(method = "applyTo", at = @At("RETURN"), cancellable = true)
    private void typewriter$carryState(Style other, CallbackInfoReturnable<Style> cir) {
        Style result = cir.getReturnValue();
        if (result == null || result.isEmpty()) {
            return;
        }
        TypewriterState state = this.typewriter$state != null
                ? this.typewriter$state
                : TypewriterStyleHolder.of(other);
        if (state != null && TypewriterStyleHolder.of(result) == null) {
            TypewriterStyleHolder.set(result, state);
        }
        DistortSpec spec = this.distort$spec != null ? this.distort$spec : DistortStyleHolder.of(other);
        if (spec != null && DistortStyleHolder.of(result) == null) {
            DistortStyleHolder.set(result, spec);
        }
    }
}
