package cn.blockforge.generated.typewritertext;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * 与 NBT Autocomplete（modid {@code nbtac}）的软兼容：把 {@code typewriter} / {@code distort}
 * 补进它给文本组件列出的字段里，于是写 {@code custom_name='{"text":"嗨","ty...'}}、
 * 告示牌、hover 文本时都能补全出来。
 *
 * <p>为什么是"每 tick 探一次"：它是客户端模组，内置建议表要等到
 * {@code FMLLoadCompleteEvent} 才解析（配置还可能让它开线程），所以启动阶段去写一定扑空。
 * 这里就每次客户端 tick 看一眼，装好之后每个 tick 只剩一次 boolean 判断。
 *
 * <p>为什么用反射：它公开的 API 只能<b>新增</b>条目、不能扩展现有条目
 * （{@code NBTacAPI.addCustomSuggestions} 明确禁止 minecraft/nbtac 命名空间），
 * 而文本组件的字段表是把 {@code text/nbtac:style} 整张并进去的。于是做法是：
 * 先用它的公开 API 把下面这份片段解析成一张新表（片段头用 {@code &:nbtac:style}
 * 继承原版样式字段，我们只往上加两个字段），再把这整张表换个键挂到
 * {@code text/nbtac:style} 上——这一步只能碰它的内部类，且只用来搬一个
 * {@code String -> NbtTagMap} 的引用。全部包在 try/catch 里：失败就只是没有补全提示，
 * 不影响打字机本身，也不影响别的模组。
 */
@EventBusSubscriber(modid = TypewriterTextMod.MOD_ID, value = Dist.CLIENT)
public final class NbtacCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("typewriter_text");

    private static final String NBTAC_ID = "nbtac";

    /** NBTac 里"文本组件样式字段"那张表的键（{@code TextCompoundType} 每次现取）。 */
    private static final String STYLE_MAP = "text/nbtac:style";

    /** 我们的片段解析后落在哪个键上；命名空间不能是 minecraft/nbtac。 */
    private static final String OWN_MAP = "text/typewriter_text:typewriter_style";

    /**
     * 补全片段：格式与 NBTac 内置建议文件一致（制表符表示层级）。
     * 首行 {@code &:nbtac:style} 表示"继承原版文本组件样式字段"，下面才是我们加的。
     * 字段名与取值必须与 {@link TypewriterState} / {@link DistortSpec} 的编解码保持一致。
     */
    private static final String SNIPPET = """
            typewriter_style &:nbtac:style
            +typewriter :compound @Recommended
            \t+time :either<int, string>
            \t+command :string
            +distort :compound @Recommended
            \t+wave :compound
            \t\t+amplitude :double
            \t\t+period :either<int, string>
            \t\t+wavelength :int
            \t\t+direction :Enum(x, y)
            \t+jitter :compound
            \t\t+radius :double
            \t\t+period :either<int, string>
            """;

    /** 已经处理完（装上了，或确认没有 nbtac / 结构对不上，不再重试）。 */
    private static boolean done;

    private NbtacCompat() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (done) {
            return;
        }
        done = tryInstall();
    }

    /** @return true 表示不用再试了（装好了、没有它、或它的内部结构变了）。 */
    private static boolean tryInstall() {
        if (!ModList.get().isLoaded(NBTAC_ID)) {
            return true;
        }
        try {
            Class<?> manager = Class.forName("net.mt1006.nbtac.autocomplete.NbtTagManager");
            Method get = manager.getMethod("get", String.class);
            if (get.invoke(null, STYLE_MAP) == null) {
                return false; // 它的内置建议表还没解析完，下一个 tick 再看
            }
            Class.forName("net.mt1006.nbtac.api.NBTacAPI")
                    .getMethod("addCustomSuggestions", String.class, String.class, String.class, boolean.class)
                    .invoke(null, "text", TypewriterTextMod.MOD_ID, SNIPPET, false);
            Object own = get.invoke(null, OWN_MAP);
            if (own == null) {
                LOGGER.warn("[typewriter_text] NBT Autocomplete 补全片段解析失败，文本组件里不会有 typewriter/distort 提示");
                return true;
            }
            manager.getMethod("add", String.class, Class.forName("net.mt1006.nbtac.autocomplete.NbtTagMap"))
                    .invoke(null, STYLE_MAP, own);
            LOGGER.info("[typewriter_text] 已向 NBT Autocomplete 注册文本组件补全字段：typewriter / distort");
        } catch (Throwable t) {
            LOGGER.warn("[typewriter_text] 与 NBT Autocomplete 对接失败（不影响其他功能）：{}", t.toString());
        }
        return true;
    }
}
