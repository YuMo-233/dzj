package cn.blockforge.generated.typewritertext.mixin;

import cn.blockforge.generated.typewritertext.TypewriterState;
import cn.blockforge.generated.typewritertext.TypewriterStyleHolder;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给原版 {@link Style} 加一个隐藏的打字机字段（见 {@link TypewriterStyleHolder}），
 * 并让这个字段参与样式判等。
 *
 * <p>为什么必须动判等：原版 {@code Style.equals} 只比那十个原版字段，隐藏字段不在其中，
 * 于是"只差打字机参数"的两个样式会被判为相等——物品因此堆叠（堆叠时只保留其中一份数据，
 * 另一份静默丢失）、聊天栏的连续消息因此被并成一条 "2x"。这里在原版判等返回 true 时补一次
 * 打字机身份比较，身份只看出字速度与逐字指令，不看 session 与播放进度。
 *
 * <p>{@code hashCode} 不动：判等只会变得更严格，相等的两个样式必然仍满足原版的哈希一致要求
 * （不相等的两个样式哈希相撞是合法情形）。
 */
@Mixin(Style.class)
public abstract class StyleMixin implements TypewriterStyleHolder {

    @Unique
    private TypewriterState typewriter$state;

    @Override
    public TypewriterState typewriter$state() {
        return this.typewriter$state;
    }

    @Override
    public void typewriter$setState(TypewriterState state) {
        this.typewriter$state = state;
    }

    /** 原版判等只看原版字段，这里补上打字机身份。 */
    @Inject(method = "equals", at = @At("RETURN"), cancellable = true)
    private void typewriter$compareState(Object other, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || !(other instanceof Style otherStyle)) {
            return;
        }
        if (!TypewriterStyleHolder.sameIdentity(this.typewriter$state, TypewriterStyleHolder.of(otherStyle))) {
            cir.setReturnValue(false);
        }
    }
}
