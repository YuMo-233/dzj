package cn.blockforge.generated.typewritertext;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 打字机网络包注册与处理。 */
public final class TypewriterNetworking {
    private TypewriterNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(TypewriterPayloads.CharTyped.TYPE,
                TypewriterPayloads.CharTyped.STREAM_CODEC,
                TypewriterNetworking::handleCharTyped);
        registrar.playToClient(TypewriterPayloads.Stop.TYPE,
                TypewriterPayloads.Stop.STREAM_CODEC,
                TypewriterNetworking::handleStop);
    }

    private static void handleCharTyped(TypewriterPayloads.CharTyped payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                TypewriterServerStore.handleCharTyped(player, payload);
            }
        });
    }

    private static void handleStop(TypewriterPayloads.Stop payload, IPayloadContext context) {
        context.enqueueWork(TypewriterRuntime::stopAll);
    }
}
