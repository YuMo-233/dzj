package cn.blockforge.generated.typewritertext;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 按打字机样式把文字“切片”后再交给原版排版器。
 *
 * <p>原版所有把组件变成可绘制文字的通路——{@code Language.getVisualOrder}
 * （SubStringSource）、聊天栏与工具提示用的 {@code Font.split}
 * （StringSplitter.splitLines）、{@code toFlatList}——最终都会调用
 * {@code Component.visit(StyledContentConsumer, Style)}。本类就在这一层工作：
 * 由 {@code MutableComponentMixin} 把它挂成 {@code MutableComponent#visit} 的实现，
 * 于是标题、动作栏、聊天栏、bossbar、物品名等所有通道一次性获得逐字效果。
 *
 * <p>一趟渲染分两步：
 * <ol>
 *   <li>统计：找到这棵组件树里出现的每个打字机样式，数出它覆盖的总字数，
 *       据此推进该样式的播放进度（{@link TypewriterState#beginPass(int)}）；</li>
 *   <li>发射：按顺序把每段文字按进度截断后交给下游，并把新打出的字符排队上报
 *       （用于触发 {@code command}）。</li>
 * </ol>
 * 样式按原版语义向下继承：父组件带了 {@code typewriter}，它和子组件会连成一段一起打。
 */
public final class TypewriterRender {

    private TypewriterRender() {
    }

    /** {@code MutableComponent#visit(StyledContentConsumer, Style)} 的实现。 */
    public static <T> Optional<T> visit(Component root, FormattedText.StyledContentConsumer<T> consumer, Style style) {
        if (!TypewriterRuntime.hasAny() || !TypewriterRuntime.shouldAnimate()) {
            return plain(root, consumer, style);
        }
        IdentityHashMap<TypewriterState, int[]> counts = new IdentityHashMap<>();
        count(root, style, null, counts);
        if (counts.isEmpty()) {
            return plain(root, consumer, style);
        }
        for (Map.Entry<TypewriterState, int[]> entry : counts.entrySet()) {
            entry.getKey().beginPass(entry.getValue()[0]);
        }
        return emit(root, consumer, style, null, new IdentityHashMap<>());
    }

    /** 与 {@code Component.visit(StyledContentConsumer, Style)} 的默认实现逐行等价，不做任何切片。 */
    public static <T> Optional<T> plain(Component component, FormattedText.StyledContentConsumer<T> consumer, Style inherited) {
        Style merged = component.getStyle().applyTo(inherited);
        Optional<T> result = component.getContents().visit(consumer, merged);
        if (result.isPresent()) {
            return result;
        }
        for (Component sibling : component.getSiblings()) {
            Optional<T> siblingResult = plain(sibling, consumer, merged);
            if (siblingResult.isPresent()) {
                return siblingResult;
            }
        }
        return Optional.empty();
    }

    /** 组件树里是否带打字机样式（决定渲染缓存要不要旁路）。 */
    public static boolean contains(Component component) {
        return contains(component, 0);
    }

    private static boolean contains(Component component, int depth) {
        if (component == null || depth > 32) {
            return false;
        }
        if (TypewriterStyleHolder.of(component.getStyle()) != null) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (contains(sibling, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private static void count(Component component, Style inherited, TypewriterState run,
                              IdentityHashMap<TypewriterState, int[]> counts) {
        TypewriterState own = TypewriterStyleHolder.of(component.getStyle());
        TypewriterState effective = own != null ? own : run;
        Style merged = component.getStyle().applyTo(inherited);
        if (effective != null) {
            int[] acc = counts.computeIfAbsent(effective, key -> new int[1]);
            component.getContents().visit((style, text) -> {
                acc[0] = Math.min(TypewriterState.MAX_CHARS, acc[0] + text.length());
                return Optional.empty();
            }, merged);
        }
        for (Component sibling : component.getSiblings()) {
            count(sibling, merged, effective, counts);
        }
    }

    private static <T> Optional<T> emit(Component component, FormattedText.StyledContentConsumer<T> consumer,
                                        Style inherited, TypewriterState run,
                                        IdentityHashMap<TypewriterState, int[]> offsets) {
        TypewriterState own = TypewriterStyleHolder.of(component.getStyle());
        TypewriterState effective = own != null ? own : run;
        Style merged = component.getStyle().applyTo(inherited);
        Optional<T> result = component.getContents().visit((style, text) -> {
            if (effective == null) {
                return consumer.accept(style, text);
            }
            int[] slot = offsets.computeIfAbsent(effective, key -> new int[1]);
            int start = slot[0];
            int end = start + text.length();
            slot[0] = end;
            int reveal = effective.revealed();
            int visible = reveal >= end ? text.length() : Math.max(0, reveal - start);
            // 不要把代理对（emoji 等）从中间切开
            if (visible > 0 && visible < text.length() && Character.isHighSurrogate(text.charAt(visible - 1))) {
                visible--;
            }
            if (!effective.command().isEmpty()) {
                int from = Math.max(effective.reported(), start);
                int to = Math.min(reveal, end);
                for (int i = from; i < to; i++) {
                    TypewriterRuntime.queueChar(effective.session(), i + 1, effective.totalChars(),
                            String.valueOf(text.charAt(i - start)));
                }
                if (to > effective.reported()) {
                    effective.noteReported(to);
                }
            }
            return consumer.accept(style, visible >= text.length() ? text : text.substring(0, visible));
        }, merged);
        if (result.isPresent()) {
            return result;
        }
        for (Component sibling : component.getSiblings()) {
            Optional<T> siblingResult = emit(sibling, consumer, merged, effective, offsets);
            if (siblingResult.isPresent()) {
                return siblingResult;
            }
        }
        return Optional.empty();
    }
}
