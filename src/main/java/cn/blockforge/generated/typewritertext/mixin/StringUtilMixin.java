package cn.blockforge.generated.typewritertext.mixin;

import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版把 {@code §}（U+00A7）划为非法字符，存档名、书、告示牌、聊天、命令框的输入框都会
 * 在输入那一刻把它吞掉——所以分段效果码（{@code §$} / {@code §^}）在这些 UI 里根本打不进去。
 * 这里在**客户端**放行 {@code §}：只放开这一个字符（{@code 32 ≤ c < 127} 的原规则不变），
 * 让玩家能直接打出 {@code §$吊炸天} 存进名字。
 *
 * <p>为什么安全：本 mixin 在 {@code typewriter_text.mixins.json} 的 {@code client} 段——
 * 专用服务器不加载，跨服的聊天仍会被服务端过滤；单机集成服务器共享这个 JVM，放行对
 * 本地会话也生效。放行后文本里的 {@code §a} 等原版色码仍按字面显示（渲染层不解析它们），
 * 只有 {@code §$ / §^ / §r} 会被本模组渲染成扭曲效果。
 */
@Mixin(StringUtil.class)
public abstract class StringUtilMixin {

    /** 与 {@code cn.blockforge.generated.typewritertext.SectionFormat.MARK} 一致（U+00A7）。 */
    private static final char SECTION_MARK = '\u00A7';

    @Inject(method = "isAllowedChatCharacter", at = @At("HEAD"), cancellable = true)
    private static void typewriter$allowSectionMark(char c, CallbackInfoReturnable<Boolean> cir) {
        if (c == SECTION_MARK) {
            cir.setReturnValue(true);
        }
    }
}