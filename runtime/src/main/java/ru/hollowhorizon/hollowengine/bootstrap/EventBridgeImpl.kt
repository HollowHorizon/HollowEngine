package ru.hollowhorizon.hollowengine.bootstrap

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import ru.hollowhorizon.hollowengine.bootstrap.runtime.EventBridge
import ru.hollowhorizon.hollowengine.common.events.client.ItemTooltipEvent
import ru.hollowhorizon.hollowengine.common.events.post
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

    override fun onRegisterReloadListeners(registration: EventBridge.ReloadListenerRegistration) {
        val event = RegisterReloadListenersEvent.Client().post()
        event.listeners.forEach(registration::register)
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

    override fun onClientCommandRegistration(
        dispatcher: CommandDispatcher<SharedSuggestionProvider>,
        ctx: CommandBuildContext,
    ) {
        RegisterClientCommandsEvent(dispatcher, ctx).post()
    }
}