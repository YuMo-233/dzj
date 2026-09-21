package cn.blockforge.generated.typewritertext;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.joml.Matrix4f;

/**
 * 让 {@code Font$StringRenderOutput} 能调用 {@code Font.renderChar}（包私有方法）的桥：
 * 由 {@code mixin.FontRenderCharMixin} 在 {@link net.minecraft.client.gui.Font} 上实现。
 * <p>注意：这个接口<b>不能</b>放在 {code mixin} 包里——Mixin 禁止外部直接引用 mixin 包里的类。
 */
public interface FontRenderCharAccess {

    void typewriter$renderChar(BakedGlyph glyph, boolean italic, boolean bold, float boldOffset,
                               float x, float y, Matrix4f pose, VertexConsumer buffer,
                               float r, float g, float b, float a, int packedLight);
}