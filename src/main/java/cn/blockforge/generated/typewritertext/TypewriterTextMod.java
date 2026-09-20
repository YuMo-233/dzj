package cn.blockforge.generated.typewritertext;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 打字机文本样式 —— 打字机不再是一种组件类型，而是原版组件样式上的一个字段。
 *
 * <p>任何原版组件都可以带这个样式，例如：
 * <pre>
 * /tellraw @a ["任务开始：", {"text":"寻找三块红石","color":"gold","typewriter":{"time":60}}]
 * /title @a title {"text":"BOSS 登场","color":"dark_red","bold":true,"typewriter":{"interval":3}}
 * </pre>
 *
 * <p>{@code typewriter} 对象里的字段（都可省略）：
 * <ul>
 *   <li>{@code time}：总时长（tick）；缺省按每字符 2 tick，也可用 {@code interval}
 *       直接指定每字符 tick 数；</li>
 *   <li>{@code command}：每打出一个字符触发一次的指令，占位符 {@code %c}（当前字符）、
 *       {@code %i}（序号）、{@code %n}（总字数）；以观看者身份、权限封顶 2 级、
 *       <b>完全静默</b>执行——指令自身给执行者的回显（如 {@code /playsound} 的
 *       “已播放声音”提示）会被抑制，不会出现突兀的系统提示；</li>
 *   <li>{@code session}：可省略；服务端解码时自动生成并随组件一起发往客户端。</li>
 * </ul>
 *
 * <p>样式按原版语义向下继承：父组件的 {@code typewriter} 会作用于它和它的
 * {@code extra} 子组件，整段文字连在一起逐字打出。颜色、加粗、点击/悬停事件等
 * 原版样式字段全部照常生效。
 *
 * <p>进度由客户端在渲染时刻驱动（首次渲染即起笔），服务端不参与打点；
 * {@code command} 只上报“第几个字符已打出 + 该字符”，指令文本始终留在服务端登记表里，
 * 且只有服务端线程解码出的样式才会登记（告示牌等客户端提交的组件无法借道注入命令）。
 */
@Mod(TypewriterTextMod.MOD_ID)
public final class TypewriterTextMod {
    public static final String MOD_ID = "typewriter_text";

    private static final Logger LOGGER = LoggerFactory.getLogger("typewriter_text");

    private static int tickCounter;

    public TypewriterTextMod(IEventBus modEventBus) {
        modEventBus.addListener(TypewriterNetworking::register);
        NeoForge.EVENT_BUS.addListener(TypewriterCommands::register);
        NeoForge.EVENT_BUS.addListener(TypewriterServerStore::onServerStarted);
        NeoForge.EVENT_BUS.addListener(TypewriterServerStore::onServerStopping);
        NeoForge.EVENT_BUS.addListener(TypewriterTextMod::onServerTick);

        if (isStylePatched()) {
            LOGGER.info("[typewriter_text] 打字机样式字段已挂载：组件样式 \"typewriter\"");
        } else {
            LOGGER.warn("[typewriter_text] 打字机样式挂载失败：组件样式编码可能被其他模组改写，逐字显示将退化为普通文本");
        }
    }

    /** 方便取本模组命名空间下的 ID。 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * 启动自检：先读一次 {@code Style.Serializer.CODEC} 触发该类初始化——mixin 正是在
     * 那一刻把原版样式 MapCodec 换成带 {@code typewriter} 字段的版本——再问它有没有被接管。
     *
     * <p>这里刻意不做“解码一个探针组件”式的检查：那会构造出真正的打字机样式，把
     * {@link TypewriterRuntime#hasAny()} 恒置为 true，使每个组件渲染都要多走一趟树遍历。
     */
    private static boolean isStylePatched() {
        try {
            Object codec = Style.Serializer.CODEC;
            return codec != null && TypewriterStyleCodec.isInstalled();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter >= 100) {
            tickCounter = 0;
            TypewriterServerStore.sweep(event.getServer());
        }
    }
}
