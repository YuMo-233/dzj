package cn.blockforge.generated.typewritertext.mixin;

import cn.blockforge.generated.typewritertext.DistortRender;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 扭曲样式的挂载点：给"正在画的这一个字形"加一点位移。
 *
 * <p>为什么是这里：1.21.1 里 {@code Font$StringRenderOutput#accept} 是"一个字形被画出来"的
 * 唯一出口——普通绘制与 8 倍描边（{@code drawInBatch8xOutline}）都用它——而字形四角是在
 * {@code accept} 内部就地用包私有字段 {@code x}/{@code y} 算出来的。于是：
 *
 * <ul>
 *   <li>进入 {@code accept} 时给 {@code x}/{@code y} 加上这一字的位移；</li>
 *   <li>返回前再减掉。光标累加（{@code x += advance}）夹在中间，净值守恒，后续字符不会被带偏；</li>
 *   <li>阴影/描边字是在同一段里按 {@code x + 偏移} 画的，所以会跟着一起扭曲。</li>
 * </ul>
 *
 * <p>该内部类是包私有的，只能用 {@code targets} 字符串形式指定目标。
 */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class FontStringRenderOutputMixin {

    @Shadow
    float x;

    @Shadow
    float y;

    @Unique
    private float distort$dx;

    @Unique
    private float distort$dy;

    @Inject(method = "accept", at = @At("HEAD"))
    private void distort$apply(int index, Style style, int codePoint, CallbackInfo ci) {
        DistortRender.Offset offset = DistortRender.at(style, index, this.x);
        this.distort$dx = offset.x();
        this.distort$dy = offset.y();
        this.x += this.distort$dx;
        this.y += this.distort$dy;
    }

    @Inject(method = "accept", at = @At("RETURN"))
    private void distort$revert(int index, Style style, int codePoint, CallbackInfo ci) {
        this.x -= this.distort$dx;
        this.y -= this.distort$dy;
        this.distort$dx = 0.0f;
        this.distort$dy = 0.0f;
    }
}
