package cn.blockforge.generated.typewritertext.mixin;

import cn.blockforge.generated.typewritertext.FontRenderCharAccess;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给 {@link Font} 实现 {@link FontRenderCharAccess} 桥：{@code renderChar} 是包私有方法，
 * {@code Font$StringRenderOutput} 那个 mixin 跨包访问不了，只能透过这边实现的接口转发。
 * 转发只是原样调用，不修改任何行为。
 */
@Mixin(Font.class)
public abstract class FontRenderCharMixin implements FontRenderCharAccess {

    @Shadow
    abstract void renderChar(BakedGlyph glyph, boolean italic, boolean bold, float boldOffset,
                             float x, float y, Matrix4f pose, VertexConsumer buffer,
                             float r, float g, float b, float a, int packedLight);

    @Override
    @Unique
    public void typewriter$renderChar(BakedGlyph glyph, boolean italic, boolean bold, float boldOffset,
                                      float x, float y, Matrix4f pose, VertexConsumer buffer,
                                      float r, float g, float b, float a, int packedLight) {
        this.renderChar(glyph, italic, bold, boldOffset, x, y, pose, buffer, r, g, b, a, packedLight);
    }
}