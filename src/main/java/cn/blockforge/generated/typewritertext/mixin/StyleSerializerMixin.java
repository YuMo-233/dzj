package cn.blockforge.generated.typewritertext.mixin;

import cn.blockforge.generated.typewritertext.TypewriterStyleCodec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Function;

/**
 * 在原版 {@code Style.Serializer} 的类初始化里，把它组装 {@code MAP_CODEC} 的那次
 * {@code RecordCodecBuilder.mapCodec(...)} 调用换成“原版字段 + typewriter 字段”的包装版。
 *
 * <p>这样做的原因：原版 {@code CODEC} 与网络用的 {@code TRUSTED_STREAM_CODEC} 都是在
 * 同一段 {@code <clinit>} 里由这个 {@code MAP_CODEC} 派生出来的，在构造点替换可以保证
 * 三者的字段集合完全一致，不需要事后反射改 final 字段。
 *
 * <p>挂载点在样式体系的最底层：组件编解码（JSON、NBT 数据组件）与网络包全都经由
 * {@code Style.Serializer}，因此打字机样式对所有文本通道天然可用。
 */
@Mixin(Style.Serializer.class)
public final class StyleSerializerMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/codecs/RecordCodecBuilder;mapCodec(Ljava/util/function/Function;)Lcom/mojang/serialization/MapCodec;"))
    private static MapCodec typewriter$addTypewriterField(Function builder) {
        MapCodec original = RecordCodecBuilder.mapCodec(builder);
        return TypewriterStyleCodec.wrap(original);
    }
}
