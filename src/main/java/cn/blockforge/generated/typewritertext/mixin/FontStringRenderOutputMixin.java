package cn.blockforge.generated.typewritertext.mixin;

import cn.blockforge.generated.typewritertext.DistortRender;
import cn.blockforge.generated.typewritertext.DistortSpec;
import cn.blockforge.generated.typewritertext.DistortStyleHolder;
import cn.blockforge.generated.typewritertext.FontRenderCharAccess;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 扭曲样式的挂载点：给"正在画的这一个字形"加位移，并（波浪开启时）绕笔位倾斜。
 *
 * <p>为什么是这里：1.21.1 里 {@code Font$StringRenderOutput#accept} 是"一个字形被画出来"的
 * 唯一出口——普通绘制与 8 倍描边（{@code drawInBatch8xOutline}）都用它——字形四角是在
 * {@code accept} 内部就地用包私有字段 {@code x}/{@code y} 算出来的。于是：
 *
 * <ul>
 *   <li>进入 {@code accept} 时给 {@code x}/{@code y} 加上这一字的位移；返回前再减掉。
 *       光标累加（{@code x += advance}）夹在中间，净值守恒，后续字符不会被带偏；</li>
 *   <li>字形本体与阴影都由同一处 {@code renderChar} 调用画出来——用 {@code @Redirect}
 *       把它的 {@code pose} 参数换成"绕当前笔位已旋转过的矩阵"，整个字形（含阴影）
 *       一起跟着波浪倾斜；</li>
 *   <li>{@code underline}/{@code strikethrough} 等 {@code BakedGlyph$Effect} 是另一条
 *       路径（{@code finish()} 按未旋转的 pose 画），不跟随倾斜——见 README 已知限制。</li>
 * </ul>
 *
 * <p>该内部类是包私有的，只能用 {@code targets} 字符串形式指定目标。{@code accept} 返回
 * {@code boolean}，所以注入器的最后一个参数必须是 {@code CallbackInfoReturnable}。
 */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class FontStringRenderOutputMixin {

    @Shadow
    float x;

    @Shadow
    float y;

    /** 包围字形的 {@code Font} 实例（{@code StringRenderOutput} 有包私有字段 {@code this$0}）。 */
    @Shadow
    private Font this$0;

    @Unique
    private float distort$dx;

    @Unique
    private float distort$dy;

    /** 这一字要绕笔位旋转的弧度（波浪倾斜）；0 = 不转。 */
    @Unique
    private float distort$rot;

    @Inject(method = "accept", at = @At("HEAD"))
    private void distort$apply(int index, Style style, int codePoint, CallbackInfoReturnable<Boolean> cir) {
        DistortSpec spec = DistortStyleHolder.of(style);
        double millis = Util.getMillis();
        DistortRender.Offset offset = DistortRender.at(spec, index, this.x, millis);
        this.distort$dx = offset.x();
        this.distort$dy = offset.y();
        this.distort$rot = DistortRender.tiltRadians(spec, this.x, millis);
        this.x += this.distort$dx;
        this.y += this.distort$dy;
    }

    @Inject(method = "accept", at = @At("RETURN"))
    private void distort$revert(int index, Style style, int codePoint, CallbackInfoReturnable<Boolean> cir) {
        this.x -= this.distort$dx;
        this.y -= this.distort$dy;
        this.distort$dx = 0.0f;
        this.distort$dy = 0.0f;
        this.distort$rot = 0.0f;
    }

    /** 字形绘制的唯一调用点：原样转发 {@code renderChar}，只把 {@code pose} 换成
     * 绕"当前笔位 {x, y}"旋转过 {@link #distort$rot} 的矩阵。旋转中心取的是
     * {@code accept} 传入的 {x, y}——即已经加上本字位移后的笔位，字形底部钉在笔位上摇。
     *
     * <p>注意 @Redirect 的 handler 首参是目标调用<b>接收者</b>（{@code Font}），随后才是
     * 目标方法的实参——与 @Inject 的"直接是目标方法参数"不同。
     */
    @Redirect(method = "accept", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;renderChar(Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;ZZFFFLorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFI)V"))
    private void distort$rotateGlyph(Font font, BakedGlyph glyph, boolean italic, boolean bold, float boldOffset,
                                     float gx, float gy, Matrix4f pose, VertexConsumer buffer,
                                     float r, float g, float b, float a, int packedLight) {
        FontRenderCharAccess access = (FontRenderCharAccess) (Object) font;
        if (this.distort$rot == 0.0f) {
            access.typewriter$renderChar(glyph, italic, bold, boldOffset, gx, gy, pose, buffer, r, g, b, a, packedLight);
            return;
        }
        Matrix4f rotated = new Matrix4f(pose);
        rotated.rotateAround(new Quaternionf().rotateAxis(this.distort$rot, 0.0f, 0.0f, 1.0f), gx, gy, 0.0f);
        access.typewriter$renderChar(glyph, italic, bold, boldOffset, gx, gy, rotated, buffer, r, g, b, a, packedLight);
    }
}