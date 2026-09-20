package cn.blockforge.generated.typewritertext;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * 打字机的两个自定义网络包。
 *
 * <ul>
 *   <li>{@link CharTyped}（客户端 → 服务端）：报告“我这边第 index 个字符已经打出”，
 *       服务端据此静默执行该会话登记的 command。只带会话 id 和序号，
 *       指令文本始终留在服务端，客户端无法注入任意命令。</li>
 *   <li>{@link Stop}（服务端 → 客户端）：让该玩家立刻收尾所有正在播放的打字机。</li>
 * </ul>
 */
public final class TypewriterPayloads {

    private TypewriterPayloads() {
    }

    /** 本版本 ByteBufCodecs 未提供 UUID 常量，这里用 FriendlyByteBuf 的 UUID 读写自建。 */
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of((buf, id) -> buf.writeUUID(id), buf -> buf.readUUID());

    public record CharTyped(UUID session, int index, int total, String ch) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CharTyped> TYPE =
                new CustomPacketPayload.Type<>(TypewriterTextMod.id("char_typed"));

        public static final StreamCodec<RegistryFriendlyByteBuf, CharTyped> STREAM_CODEC =
                StreamCodec.composite(
                        UUID_CODEC, CharTyped::session,
                        ByteBufCodecs.VAR_INT, CharTyped::index,
                        ByteBufCodecs.VAR_INT, CharTyped::total,
                        ByteBufCodecs.STRING_UTF8, CharTyped::ch,
                        CharTyped::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Stop() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Stop> TYPE =
                new CustomPacketPayload.Type<>(TypewriterTextMod.id("stop"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Stop> STREAM_CODEC =
                StreamCodec.unit(new Stop());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
