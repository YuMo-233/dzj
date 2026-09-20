package cn.blockforge.generated.typewritertext;

import cn.blockforge.generated.typewritertext.mixin.ChatComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;

/**
 * 打字机运行时的客户端那一半：每个客户端 tick 排空“字符已打出”队列发给服务端
 * （驱动 {@code command}），并在还有内容正在逐字播放时让聊天栏重新排版。
 *
 * <p>聊天栏的排版结果在原版里是按消息缓存的（{@code ChatComponent#refreshTrimmedMessages}
 * 才会重算），不主动刷新的话聊天栏里的打字机就不会动。标题/动作栏/bossbar/物品名
 * 这些通道每帧都会重新取排版结果，靠 {@code MutableComponentMixin} 的缓存旁路直接生效。
 *
 * <p>本类只允许在客户端加载（只有 {@link TypewriterClient} 引用它）。
 */
public final class TypewriterClientRuntime {

    private static volatile Method refreshTrimmedMessages;
    private static volatile boolean accessorBroken;

    private TypewriterClientRuntime() {
    }

    /** 每个客户端 tick 调用。 */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) {
            TypewriterRuntime.stopAll();
            return;
        }
        // 一次 tick 最多发 64 条，多余的留在队列里下 tick 继续，防突发
        for (TypewriterPayloads.CharTyped typed : TypewriterRuntime.drainQueue(64)) {
            PacketDistributor.sendToServer(typed);
        }
        if (TypewriterRuntime.hasActive()) {
            refreshChat(mc);
        }
    }

    private static void refreshChat(Minecraft mc) {
        ChatComponent chat = mc.gui != null ? mc.gui.getChat() : null;
        if (chat == null) {
            return;
        }
        if (!accessorBroken) {
            try {
                ((ChatComponentAccessor) chat).typewriter$refreshTrimmedMessages();
                return;
            } catch (Throwable t) {
                // 访问器不可用（被其他模组改写等）：退回反射，只试一次
                accessorBroken = true;
            }
        }
        try {
            Method method = refreshTrimmedMessages;
            if (method == null) {
                method = ChatComponent.class.getDeclaredMethod("refreshTrimmedMessages");
                method.setAccessible(true);
                refreshTrimmedMessages = method;
            }
            method.invoke(chat);
        } catch (Throwable ignored) {
            // 拿不到就放弃聊天栏动画，其余通道（标题/动作栏/bossbar/物品名）不受影响
        }
    }
}
