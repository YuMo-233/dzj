package cn.blockforge.generated.typewritertext;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 客户端 tick 钩子：驱动指令回报队列与聊天栏动画刷新。 */
@EventBusSubscriber(modid = TypewriterTextMod.MOD_ID, value = Dist.CLIENT)
public final class TypewriterClient {
    private TypewriterClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        TypewriterClientRuntime.clientTick();
    }
}
