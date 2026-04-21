package ru.hollowhorizon.hollowengine.bootstrap

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import ru.hollowhorizon.hollowengine.bootstrap.runtime.EventBridge
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.events.blocks.BlockEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityLoadedEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityTrackingEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.events.client.ItemTooltipEvent
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.common.events.item.BuildTabContentsEvent
import ru.hollowhorizon.hollowengine.common.events.registry.*
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent
import java.util.function.BiConsumer
import java.util.function.Consumer

object EventBridgeImpl : EventBridge {
    override fun onRegisterShaders(shaders: EventBridge.ShaderRegistration) {
        val event = RegisterShadersEvent().post()
        event.shaders.forEach {
            shaders.register(it.key, it.value.first, it.value.second)
        }
    }

    override fun onRegisterEntityRenderers(consumer: BiConsumer<EntityType<out Entity>, EntityRendererProvider<Entity>>) {
        RegisterEntityRenderersEvent { a, b -> consumer.accept(a, b) }.post()
    }

    override fun onRegisterBlockEntityRenderers(consumer: BiConsumer<BlockEntityType<out BlockEntity>, BlockEntityRendererProvider<BlockEntity>>) {
        RegisterBlockEntityRenderersEvent { a, b -> consumer.accept(a, b) }.post()
    }

    override fun onRegisterKeybindings(consumer: Consumer<KeyMapping>) {
        RegisterKeyBindingsEvent(consumer::accept).post()
    }

    override fun onRegisterClientReloadListeners(registration: EventBridge.ReloadListenerRegistration) {
        val event = RegisterReloadListenersEvent.Client().post()
        event.listeners.forEach(registration::register)
    }

    override fun onRegisterServerReloadListeners(registration: EventBridge.ReloadListenerRegistration) {
        val event = RegisterReloadListenersEvent.Server().post()
        event.listeners.forEach(registration::register)
    }

    override fun onRegisterEntityAttributes(
        consumer: BiConsumer<EntityType<out LivingEntity>, AttributeSupplier>
    ) {
        val event = RegisterEntityAttributesEvent().post()
        event.getAttributes().forEach(consumer::accept)
    }

    override fun onGetTooltip(
        stack: ItemStack,
        tooltip: Item.TooltipContext,
        flags: TooltipFlag,
        lines: MutableList<Component>,
    ) {
        ItemTooltipEvent(flags, stack, lines, tooltip).post()
    }

    override fun onClientTick(minecraft: Minecraft) {
        TickEvent.Client(minecraft).post()
    }

    override fun onServerTick(server: MinecraftServer) {
        TickEvent.Server(server).post()
    }

    override fun onRegisterCommands(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        ctx: CommandBuildContext,
        environment: Commands.CommandSelection,
    ) {
        RegisterCommandsEvent(dispatcher, ctx, environment).post()
    }

    override fun onClientCommandRegistration(
        dispatcher: CommandDispatcher<SharedSuggestionProvider>,
        ctx: CommandBuildContext,
    ) {
        RegisterClientCommandsEvent(dispatcher, ctx).post()
    }

    override fun onEntityTrackingStart(player: ServerPlayer, entity: Entity) {
        EntityTrackingEvent.Start(player, entity).post()
    }

    override fun onEntityTrackingStop(player: ServerPlayer, entity: Entity) {
        EntityTrackingEvent.Stop(player, entity).post()
    }

    override fun onPlayerJoin(player: ServerPlayer) {
        PlayerEvent.Join(player).post()
    }

    override fun onPlayerLeave(player: ServerPlayer) {
        PlayerEvent.Leave(player).post()
    }

    override fun onPlayerChangeDimension(player: ServerPlayer, from: ServerLevel, to: ServerLevel) {
        PlayerEvent.ChangeDimension(player, from, to).post()
    }

    override fun onEntityLoad(entity: Entity) {
        EntityLoadedEvent(entity).post()
    }

    override fun onBlockBreak(level: Level, pos: BlockPos, state: BlockState, player: ServerPlayer): Boolean {
        val event = BlockEvent.Break(level, pos, state, player)
        EventBus.post(event)
        return event.isCanceled
    }

    override fun onBuildTabContents(
        tab: CreativeModeTab,
        tabKey: ResourceKey<CreativeModeTab>,
        parameters: CreativeModeTab.ItemDisplayParameters,
        output: CreativeModeTab.Output,
    ) {
        BuildTabContentsEvent(tab, tabKey, parameters, output::accept).post()
    }
}
