package cn.blockforge.generated.typewritertext.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 取回 {@link ChatComponent} 里私有的重排入口。
 *
 * <p>打字机在聊天栏里逐字显示时，必须让聊天栏把每条消息的行切分结果重算一遍，
 * 否则屏幕上出现的仍是消息刚进来那一刻缓存的整行文本。原版这个方法是私有的，
 * 这里用 {@code @Invoker} 暴露出来——比反射更可靠：名字随映射一起重映射，
 * 挂载失败会在日志里明确报错，而不是让动画静默失效。
 */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {

    @Invoker("refreshTrimmedMessages")
    void typewriter$refreshTrimmedMessages();
}
