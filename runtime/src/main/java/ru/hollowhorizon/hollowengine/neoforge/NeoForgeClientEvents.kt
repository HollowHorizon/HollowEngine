package ru.hollowhorizon.hollowengine.neoforge

//? if neoforge {

/*import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.world.item.Item
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.common.NeoForge
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterBlockEntityRenderersEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterEntityRenderersEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterKeyBindingsEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterReloadListenersEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterShadersEvent


class NeoForgeClientEvents(val modBus: IEventBus) {


        init {
            modBus.addListener(::registerShaders)
            modBus.addListener(::onRegisterKeys)
            modBus.addListener(::onEntityRenderers)
            modBus.addListener(::registerReloadListeners)
            NeoForge.EVENT_BUS.addListener(::onClientTick)
            NeoForge.EVENT_BUS.addListener(::onRenderTooltips)
            NeoForge.EVENT_BUS.addListener(::onCameraSetup)
        }

        private fun onEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
            RegisterEntityRenderersEvent(event::registerEntityRenderer).post()
            RegisterBlockEntityRenderersEvent(event::registerBlockEntityRenderer).post()
        }

        private fun registerReloadListeners(event: RegisterClientReloadListenersEvent) {
            val hcevent = RegisterReloadListenersEvent.Client()
            EventBus.post(hcevent)
            hcevent.listeners.forEach {
                event.registerReloadListener(it)
            }
        }

        private fun registerShaders(event: net.neoforged.neoforge.client.event.RegisterShadersEvent) {
            val hcEvent = RegisterShadersEvent()
            EventBus.post(hcEvent)
            hcEvent.shaders.forEach {
                event.registerShader(ShaderInstance(event.resourceProvider, it.key, it.value.first), it.value.second)
            }
        }

        private fun onClientTick(event: ClientTickEvent.Post) {
            EventBus.post(
                ru.hollowhorizon.hollowengine.common.events.tick.TickEvent.Client(
                    Minecraft.getInstance()
                )
            )
        }

        private fun onRegisterKeys(event: RegisterKeyMappingsEvent) {
            RegisterKeyBindingsEvent(event::register).post()
        }

        private fun onRenderTooltips(event: net.neoforged.neoforge.event.entity.player.ItemTooltipEvent) {
            ru.hollowhorizon.hollowengine.common.events.client.ItemTooltipEvent(
                event.flags,
                event.itemStack,
                event.toolTip,
                //? if >=1.21 {
                /^Item.TooltipContext.of(Minecraft.getInstance().level)
                ^///?}
            ).post()
        }

        private fun onCameraSetup(event: net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles) {
            val hcEvent = ru.hollowhorizon.hollowengine.common.events.client.CameraSetupEvent(event.renderer, event.camera, event.partialTick.toFloat(), event.yaw, event.pitch, event.roll)
            hcEvent.post()
            event.yaw = hcEvent.yaw
            event.pitch = hcEvent.pitch
            event.roll = hcEvent.roll
        }
    
}

*///?}