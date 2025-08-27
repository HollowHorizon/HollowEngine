//? if fabric {
package ru.hollowhorizon.hc.fabric

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.client.renderer.entity.EntityRenderers
import net.minecraft.server.packs.PackType
import ru.hollowhorizon.hc.common.utils.JavaHacks
import ru.hollowhorizon.hc.common.events.EventBus.post
import ru.hollowhorizon.hc.common.events.client.ItemTooltipEvent
import ru.hollowhorizon.hc.common.events.client.ScreenEvent
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hc.common.events.registry.*
import ru.hollowhorizon.hc.common.events.tick.TickEvent
import ru.hollowhorizon.hc.fabric.internal.DelegatedReloadListener

object FabricClientEvents {
    init {
        registerShaders()
        registerReloadListeners()
        RegisterEntityRenderersEvent { entity, renderer ->
            EntityRenderers.register(entity, renderer)
        }.post()
        RegisterBlockEntityRenderersEvent { entity, renderer ->
            BlockEntityRenderers.register(entity, renderer)
        }.post()

        renderTooltips()

        post(RegisterKeyBindingsEvent(KeyBindingHelper::registerKeyBinding))
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { c: Minecraft ->
            post(TickEvent.Client(c))
        })
        ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher, registryAccess ->
            post(RegisterClientCommandsEvent(JavaHacks.forceCast(dispatcher), registryAccess))
        })
    }

    private fun registerReloadListeners() {
        val event = RegisterReloadListenersEvent.Client()
        post(event)
        val helper = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)

        event.listeners.forEach {
            helper.registerReloadListener(DelegatedReloadListener(it))
        }
    }

    private fun registerShaders() {
        CoreShaderRegistrationCallback.EVENT.register(CoreShaderRegistrationCallback { ctx: CoreShaderRegistrationCallback.RegistrationContext ->
            val event = RegisterShadersEvent()
            post(event)
            event.shaders.forEach {
                ctx.register(it.key, it.value.first, it.value.second)
            }
        })
    }

    private fun renderTooltips() {
        //? if >= 1.21 {

        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, context, flags, tooltip ->
            ItemTooltipEvent(flags, stack, tooltip, context).post()
        })
        //?} else {
        
        /*ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, tooltipFlag, lines ->
            ItemTooltipEvent(tooltipFlag, stack, lines).post()
        })
        *///?}

    }
}
//?}