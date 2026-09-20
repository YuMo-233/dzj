package cn.blockforge.generated.typewritertext.mixin;

import cn.blockforge.generated.typewritertext.TypewriterState;
import cn.blockforge.generated.typewritertext.TypewriterStyleHolder;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 给原版 {@link Style} 加一个隐藏的打字机字段（见 {@link TypewriterStyleHolder}）。
 *
 * <p>只在样式对象上存数据，不改变原版任何字段或行为；没有打字机样式的组件
 * 读到的恒为 null。
 */
@Mixin(Style.class)
public abstract class StyleMixin implements TypewriterStyleHolder {

    @Unique
    private TypewriterState typewriter$state;

    @Override
    public TypewriterState typewriter$state() {
        return this.typewriter$state;
    }

    @Override
    public void typewriter$setState(TypewriterState state) {
        this.typewriter$state = state;
    }
}
