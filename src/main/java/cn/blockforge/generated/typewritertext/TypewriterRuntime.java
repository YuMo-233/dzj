package cn.blockforge.generated.typewritertext;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 打字机运行时（两端通用的那一半）：登记正在逐字播放的样式，并暂存
 * “哪个会话的第几个字符已经打出”的回报。
 *
 * <p>本类刻意不引用任何客户端类——{@link TypewriterRender#visit} 在专用服务端也会被
 * 走到（只是拿到全文），若把 {@code Minecraft}/{@code ChatComponent} 之类的引用混进来，
 * 服务端链接这个方法就会出问题。客户端那一半在 {@link TypewriterClientRuntime}。
 *
 * <p>{@link #shouldAnimate()} 与 {@link #queueChar} 可能被任意线程触到，内部保持线程安全；
 * 其余方法只在客户端主线程调用。
 */
public final class TypewriterRuntime {

    /** 只要实例化过任意一个打字机样式就置位：渲染钩子据此短路，普通组件零开销。 */
    private static volatile boolean any = false;

    /** 正在逐字播放中的样式（打完或停止时自行移除）。 */
    private static final Set<TypewriterState> ACTIVE = ConcurrentHashMap.newKeySet();

    /** 待上报的“字符已打出”事件队列（由客户端 tick 钩子排空发送）。 */
    private static final java.util.ArrayDeque<TypewriterPayloads.CharTyped> QUEUE = new java.util.ArrayDeque<>();

    private TypewriterRuntime() {
    }

    static void markAny() {
        any = true;
    }

    /** 全局标记：曾经实例化过打字机样式。 */
    public static boolean hasAny() {
        return any;
    }

    static void track(TypewriterState state) {
        ACTIVE.add(state);
    }

    static void untrack(TypewriterState state) {
        ACTIVE.remove(state);
    }

    /** 是否有内容正在逐字播放（客户端钩子据此决定要不要刷新聊天栏缓存）。 */
    static boolean hasActive() {
        return !ACTIVE.isEmpty();
    }

    /**
     * 是否处于“该显示动画”的上下文：必须是客户端，且不在（单机集成）服务端
     * 逻辑线程上——那条线程读组件要拿到全文，不能被逐字进度污染。
     * 专用服务端里恒为 false，且不会触碰任何客户端类。
     */
    static boolean shouldAnimate() {
        if (!FMLEnvironment.dist.isClient()) {
            return false;
        }
        MinecraftServer server = TypewriterServerStore.currentServer();
        return server == null || !server.isSameThread();
    }

    static void queueChar(UUID session, int index, int total, String ch) {
        synchronized (QUEUE) {
            if (QUEUE.size() < 4096) {
                QUEUE.add(new TypewriterPayloads.CharTyped(session, index, total, ch));
            }
        }
    }

    /** 取走最多 {@code max} 条待上报事件（保持产生顺序，服务端按序号连续性放行）。 */
    static List<TypewriterPayloads.CharTyped> drainQueue(int max) {
        List<TypewriterPayloads.CharTyped> batch = new ArrayList<>(Math.min(max, 16));
        synchronized (QUEUE) {
            while (!QUEUE.isEmpty() && batch.size() < max) {
                batch.add(QUEUE.poll());
            }
        }
        return batch;
    }

    /** 收到服务端的停止包：立刻收尾所有动画（不再补发指令）。 */
    public static void stopAll() {
        for (TypewriterState state : ACTIVE) {
            state.finishNow();
        }
        ACTIVE.clear();
        synchronized (QUEUE) {
            QUEUE.clear();
        }
    }
}
