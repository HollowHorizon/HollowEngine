package ru.hollowhorizon.hollowengine.client

import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.util.Time
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.gui.overlay.CompilationStatus
import ru.hollowhorizon.hollowengine.client.kool.KoolInitEvent
import ru.hollowhorizon.hollowengine.client.kool.createFramebufferTexture
import ru.hollowhorizon.hollowengine.client.kool.gl.render
import ru.hollowhorizon.hollowengine.client.kool.minecraft.ImageManager
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.particles.BedrockParticles
import ru.hollowhorizon.hollowengine.client.render.RenderManager
import ru.hollowhorizon.hollowengine.client.render.entity.EmptyEntityRenderer
import ru.hollowhorizon.hollowengine.client.render.posteffects.DebugInvertPostEffect
import ru.hollowhorizon.hollowengine.client.render.posteffects.PostEffectsManager
import ru.hollowhorizon.hollowengine.client.render.posteffects.RenderContext
import ru.hollowhorizon.hollowengine.client.utils.HollowPack
import ru.hollowhorizon.hollowengine.client.utils.mc
import ru.hollowhorizon.hollowengine.common.config.Config
import ru.hollowhorizon.hollowengine.common.config.ConfigName
import ru.hollowhorizon.hollowengine.common.config.HollowCoreConfig
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.GuiOverlay
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderOverlayEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterEntityRenderersEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterKeyBindingsEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterReloadListenersEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterResourcePacksEvent
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.common.registry.ModEntities

@ClientOnly
object HollowCoreClient {

    init {
        RenderSystem.recordRenderCall(RenderManager::onInitialize)
    }

    @SubscribeEvent
    fun onRegisterReloadListener(event: RegisterReloadListenersEvent.Client) {
        event.register(HollowModelManager)
        event.register(BedrockParticles)
        event.register(ImageManager)
    }

    @SubscribeEvent
    fun onRegisterResourcePacks(event: RegisterResourcePacksEvent) {
        event.addPack(HollowPack)
    }

    @SubscribeEvent
    fun onRegisterRenderers(event: RegisterEntityRenderersEvent) {
        event.registerEntity(ModEntities.NPC_ENTITY, ::EmptyEntityRenderer)
    }

    val KEY_V = KeyMapping("key.v", GLFW.GLFW_KEY_V, "key.v1")
    private var posteffectsEnabled = false
    private var posteffectsInitialized = false

    @SubscribeEvent
    fun onRegisterKeys(event: RegisterKeyBindingsEvent) {
        if (HollowCoreConfig.debugMode) event.registerKeyMapping(KEY_V)
    }

    val img by lazy {
        createFramebufferTexture(Minecraft.getInstance().mainRenderTarget)
    }

    @SubscribeEvent
    fun onKoolInit(event: KoolInitEvent) {
        val window = mc.window
        if (!posteffectsInitialized) {
            PostEffectsManager.initialize(window.width, window.height)
            PostEffectsManager.addEffect(DebugInvertPostEffect())
            posteffectsInitialized = true
        }
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.Client) {
        if (HollowCoreConfig.debugMode && KEY_V.isDown) {
            posteffectsEnabled = !posteffectsEnabled
        }
    }

    @SubscribeEvent
    fun onRenderOverlay(event: RenderOverlayEvent.Pre) {
        if (event.overlay != GuiOverlay.HOTBAR) return
        CompilationStatus.overlay.render()

        if (!posteffectsEnabled) return

        val mc = Minecraft.getInstance()
        val window = mc.window

        val ctx = RenderContext(
            deltaTime = mc.frameTime,
            resolution = de.fabmax.kool.math.Vec2i(window.width, window.height),
            time = (mc.level?.gameTime ?: 0L).toFloat(),
            frameCount = Time.frameCount.toLong()
        )
        PostEffectsManager.render(mc.mainRenderTarget, ctx)
    }
}

@ConfigName("gui/example")
object ExampleConfig: Config() {
    var switch by property(false)
}