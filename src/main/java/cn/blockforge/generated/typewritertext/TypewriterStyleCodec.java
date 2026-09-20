package cn.blockforge.generated.typewritertext;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.function.Function;

/**
 * 把 {@code typewriter} 字段接进原版样式编解码。
 *
 * <p>做法是给原版 {@code Style.Serializer.MAP_CODEC} 套一层：用
 * {@link RecordCodecBuilder} 把原版样式的全部字段原样摊平，再额外加一个可选的
 * {@code typewriter} 字段。编解码、网络流（原版 {@code TRUSTED_STREAM_CODEC} 由这个
 * MAP_CODEC 派生）以及 NBT 数据组件因此都会自动带上它。组件 JSON 里样式字段与内容是
 * 同一层（{@code ComponentSerialization} 以
 * {@code Style.Serializer.MAP_CODEC.forGetter(Component::getStyle)} 摊平），
 * 所以写法就是 {@code {"text":"你好","typewriter":{...}}}。
 *
 * <p><b>为什么要 {@link #copyOf}：</b>原版 {@code Style.create} 在字段全空时返回
 * {@code Style.EMPTY} 这个全局单例，而 {@code Style} 没有任何公开的构造入口——每个
 * {@code withXxx(null)} 都会因为 {@code checkEmptyAfterChange} 再次折回单例
 * （它判断的是 {@code equals(EMPTY)}，不是 {@code isEmpty()}）。把打字机数据直接写到
 * {@code Style.EMPTY} 上会污染全局（游戏里所有文本都会变成打字机），所以挂数据前一律
 * 先反射出一份“字段全空但不是单例”的样式副本。
 */
public final class TypewriterStyleCodec {

    private static final Logger LOGGER = LoggerFactory.getLogger("typewriter_text");

    /** 原版 {@code Style} 的私有构造器（十个参数全是可空引用类型）；拿不到时为 null。 */
    private static final Constructor<Style> STYLE_CONSTRUCTOR = findStyleConstructor();

    /** {@link #wrap} 是否被调用过：说明 StyleSerializerMixin 确实接管了原版样式 MapCodec。 */
    private static volatile boolean installed;

    private TypewriterStyleCodec() {
    }

    /** 包装原版样式 MapCodec，附加 {@code typewriter} 字段。 */
    public static MapCodec<Style> wrap(MapCodec<Style> original) {
        installed = true;
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                original.forGetter(Function.identity()),
                TypewriterState.MAP_CODEC.codec().optionalFieldOf(TypewriterState.FIELD)
                        .forGetter(style -> Optional.ofNullable(TypewriterStyleHolder.of(style)))
        ).apply(instance, TypewriterStyleCodec::apply));
    }

    /** 样式 MapCodec 是否已被本模组接管（供启动自检用）。 */
    public static boolean isInstalled() {
        return installed;
    }

    private static Style apply(Style style, Optional<TypewriterState> state) {
        return state.isEmpty() ? style : attach(style, state.get());
    }

    /**
     * 复制一份与 {@code base} 字段等价、但独立的样式实例，供挂载打字机数据用。
     * 连独立实例都拿不到时返回 null（宁可不动画，也不能污染全局样式）。
     */
    public static Style copyOf(Style base) {
        Style fresh = newEmptyStyle();
        if (fresh != null) {
            return base.applyTo(fresh);
        }
        // 反射被挡时的退路：base 自己不是全局单例的话，直接改它也无妨
        return base.isEmpty() ? null : base;
    }

    /** 在独立副本上挂载打字机数据，绝不改动传入的样式对象。 */
    public static Style attach(Style base, TypewriterState state) {
        Style target = copyOf(base);
        if (target == null) {
            return base;
        }
        TypewriterStyleHolder.set(target, state);
        return target;
    }

    /** 反射构造一份字段全空的样式：与原版 {@code EMPTY} 字段等价，但不是那个单例。 */
    private static Style newEmptyStyle() {
        Constructor<Style> constructor = STYLE_CONSTRUCTOR;
        if (constructor == null) {
            return null;
        }
        try {
            return constructor.newInstance(new Object[constructor.getParameterCount()]);
        } catch (Throwable t) {
            LOGGER.debug("[typewriter_text] 新建空白 Style 失败", t);
            return null;
        }
    }

    /** 找出 {@code Style} 那个“十个可空引用参数”的构造器（对版本差异尽量宽容）。 */
    private static Constructor<Style> findStyleConstructor() {
        try {
            for (Constructor<?> candidate : Style.class.getDeclaredConstructors()) {
                Class<?>[] parameters = candidate.getParameterTypes();
                boolean usable = parameters.length == 10;
                for (Class<?> parameter : parameters) {
                    usable = usable && !parameter.isPrimitive();
                }
                if (usable) {
                    candidate.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Constructor<Style> constructor = (Constructor<Style>) candidate;
                    return constructor;
                }
            }
            LOGGER.warn("[typewriter_text] 未找到原版 Style 构造器，打字机样式将退化为普通文本");
        } catch (Throwable t) {
            LOGGER.warn("[typewriter_text] 读取原版 Style 构造器失败，打字机样式将退化为普通文本", t);
        }
        return null;
    }
}
