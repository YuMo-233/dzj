package cn.blockforge.generated.typewritertext;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;

/**
 * 附加的便捷命令（打字机本身是组件样式，原版 {@code /tellraw}、{@code /title}、
 * {@code /bossbar} 等即可直接使用，不依赖这里的任何命令）：
 * <pre>
 * /tw show &lt;targets&gt; &lt;组件&gt;   把组件发到目标玩家的 action bar
 * /tw stop &lt;targets&gt;           让目标玩家正在播放的打字机立即收尾
 * </pre>
 * 两个命令都完全静默，不打断聊天栏（与 {@code /tellraw} 的行为一致）。
 * {@code /typewriter} 是 {@code /tw} 的同义长名。
 */
public final class TypewriterCommands {
    private TypewriterCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        registerRoot(event.getDispatcher(), event.getBuildContext(), "typewriter");
        registerRoot(event.getDispatcher(), event.getBuildContext(), "tw");
    }

    private static void registerRoot(CommandDispatcher<CommandSourceStack> dispatcher,
                                     net.minecraft.commands.CommandBuildContext buildContext,
                                     String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("show")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("component", ComponentArgument.textComponent(buildContext))
                                        .executes(TypewriterCommands::show))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(TypewriterCommands::stop)));
        dispatcher.register(root);
    }

    /**
     * 向 action bar 发送组件：等价于 {@code /title <targets> actionbar <component>}，
     * 组件里可以带打字机样式（{@code "typewriter":{...}}），逐字动画完全由客户端驱动。
     */
    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Component component = ComponentArgument.getComponent(context, "component");
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer player : targets) {
            player.connection.send(new ClientboundSetActionBarTextPacket(component));
        }
        return targets.size();
    }

    private static int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer player : targets) {
            PacketDistributor.sendToPlayer(player, new TypewriterPayloads.Stop());
        }
        return targets.size();
    }
}
