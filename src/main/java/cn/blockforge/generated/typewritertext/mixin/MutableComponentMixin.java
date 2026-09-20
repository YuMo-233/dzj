package cn.blockforge.generated.typewritertext.mixin;

import cn.blockforge.generated.typewritertext.TypewriterRender;
import cn.blockforge.generated.typewritertext.TypewriterRuntime;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * 打字机在客户端渲染侧的两个挂载点。
 *
 * <p>1) {@code visit(StyledContentConsumer, Style)}：原版这个默认实现是“组件 → 样式化文字”
 * 的唯一出口，标题/动作栏/聊天栏/工具提示/bossbar/物品名全部经由它。这里给
 * {@link MutableComponent} 补一个实现，把文字按打字机样式切片后再交给原版排版器，
 * 于是逐字效果覆盖全部文本通道。
 *
 * <p>2) {@code getVisualOrderText()}：原版会把排版结果缓存在组件实例上、一次计算终身复用，
 * 逐字效果需要每帧重算。含打字机样式时绕过缓存；不含的组件先查全局标记短路，零额外开销。
 */
@Mixin(MutableComponent.class)
public final class MutableComponentMixin {

    @Inject(method = "getVisualOrderText", at = @At("HEAD"), cancellable = true)
    private void typewriter$bypassCache(CallbackInfoReturnable<FormattedCharSequence> cir) {
        if (!TypewriterRuntime.hasAny()) {
            return;
        }
        MutableComponent self = (MutableComponent) (Object) this;
        if (TypewriterRender.contains(self)) {
            cir.setReturnValue(Language.getInstance().getVisualOrder(self));
        }
    }

    /** 覆盖 {@code Component} 的默认实现（原版没有在 MutableComponent 里声明它）。 */
    @SuppressWarnings("unused")
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style style) {
        return TypewriterRender.visit((Component) (Object) this, consumer, style);
    }
}
