package cn.blockforge.generated.typewritertext.mixin;

import cn.blockforge.generated.typewritertext.TypewriterRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 世界列表等一批界面用 {@code GuiGraphics.drawString(Font, String, ...)}（<b>String 重载</b>）
 * 渲染纯文本——它不走 {@code Component.visit}，因此 § 码（{@code §$} / {@code §^} / {@code §r}）
 * 在那里不会被解析成扭曲效果。这里在三个 String 重载入口拦截：文本含 § 码时，先按
 * {@link TypewriterRender#visitText} 拆成带样式的组件序列，再转发到对应的
 * {@code FormattedCharSequence} 重载绘制。无 § 码的文本保持原路，零额外开销。
 *
 * <p>转发用反射而不是 {@code @Shadow}：这个项目的 mixin 注解处理器会校验 {@code @Shadow}
 * 签名是否命中目标类（无 refmap 环境下偶尔误报"找不到符号"），反射最稳，且只在含 § 的
 * 文本上发生（世界列表几行/帧的量级）。
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsStringMixin {

    /** 与 {@code SectionFormat.MARK} 一致（U+00A7）。 */
    private static final char SECTION_MARK = '\u00A7';

    private static final ConcurrentHashMap<String, Method> METHODS = new ConcurrentHashMap<>();

    private static boolean hasSectionMark(String text) {
        return text != null && text.indexOf(SECTION_MARK) >= 0;
    }

    private static int forward(GuiGraphics self, String target, Class<?>[] types, Object... args) {
        try {
            String cacheKey = target + "|" + types.length;
            Method method = METHODS.get(cacheKey);
            if (method == null) {
                method = GuiGraphics.class.getMethod(target, types);
                METHODS.put(cacheKey, method);
            }
            return (Integer) method.invoke(self, args);
        } catch (Throwable t) {
            throw new RuntimeException("typewriter § 转发失败", t);
        }
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I",
            at = @At("HEAD"), cancellable = true)
    private void typewriter$split5(Font font, String text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        if (hasSectionMark(text)) {
            cir.setReturnValue(forward((GuiGraphics) (Object) this, "drawString",
                    new Class[]{Font.class, FormattedCharSequence.class, int.class, int.class, int.class, boolean.class},
                    font, toSequence(text), x, y, color, true));
        }
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
            at = @At("HEAD"), cancellable = true)
    private void typewriter$split6(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (hasSectionMark(text)) {
            cir.setReturnValue(forward((GuiGraphics) (Object) this, "drawString",
                    new Class[]{Font.class, FormattedCharSequence.class, int.class, int.class, int.class, boolean.class},
                    font, toSequence(text), x, y, color, shadow));
        }
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I",
            at = @At("HEAD"), cancellable = true)
    private void typewriter$splitFloat(Font font, String text, float x, float y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (hasSectionMark(text)) {
            cir.setReturnValue(forward((GuiGraphics) (Object) this, "drawString",
                    new Class[]{Font.class, FormattedCharSequence.class, float.class, float.class, int.class, boolean.class},
                    font, toSequence(text), x, y, color, shadow));
        }
    }

    /** String 重载没有样式参数：§ 拆段在空样式上构造，效果段各自带默认扭曲参数。 */
    private static FormattedCharSequence toSequence(String text) {
        return TypewriterRender.visitText(text, Style.EMPTY).getVisualOrderText();
    }
}
