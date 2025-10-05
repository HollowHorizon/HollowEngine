package ru.hollowhorizon.hollowengine.client

import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.pipeline.FullscreenShaderUtil.generateFullscreenQuad
import de.fabmax.kool.pipeline.shading.BlurShader
import de.fabmax.kool.pipeline.shading.BlurShaderConfig
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.addTextureMesh
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.gui.overlay.CompilationStatus
import ru.hollowhorizon.hollowengine.client.kool.Entity
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.kool.KoolScreen
import ru.hollowhorizon.hollowengine.client.kool.createFramebufferTexture
import ru.hollowhorizon.hollowengine.client.kool.gl.render
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.kool.minecraft.ImageManager
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.particles.BedrockParticles
import ru.hollowhorizon.hollowengine.client.render.RenderManager
import ru.hollowhorizon.hollowengine.client.render.entity.EmptyEntityRenderer
import ru.hollowhorizon.hollowengine.client.utils.HollowPack
import ru.hollowhorizon.hollowengine.client.utils.open
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

    @SubscribeEvent
    fun onRegisterKeys(event: RegisterKeyBindingsEvent) {
        if (HollowCoreConfig.debugMode) event.registerKeyMapping(KEY_V)
    }

    val img by lazy {
        createFramebufferTexture(Minecraft.getInstance().mainRenderTarget)
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.Client) {
        if (HollowCoreConfig.debugMode && KEY_V.isDown) {
            object : KoolScreen() {
                override fun Scene.setup() {
                    setupUiScene()

                    addTextureMesh {
                        generateFullscreenQuad()

                        shader = BlurShader(BlurShaderConfig()).apply {
                            blurInput = img
                            direction = Vec2f(0.002f, 0.002f)
                            strength = 1f
                        }

                    }

                    val window = UiDockable("Example")
                    val w2 = UiDockable("W2")
                    addWindowSurface(window) {
                        Column(Grow.Std, Grow.Std) {
                            TitleBar(window)

                        }
                        Image("hollowengine:textures/block/example.png")
                    }
                    addWindowSurface(w2) {
                        Column(Grow.Std, Grow.Std) {
                            TitleBar(w2)
                            Entity(Minecraft.getInstance().player!!) {
                                modifier.size(Grow.Std, Grow.Std)
                                    .mouseRotation(3f)
                                    .padding(sizes.gap)
                            }
                        }
                        Item(Items.DIAMOND.defaultInstance) {}

                        Switch(ExampleConfig.switch) {
                            modifier.onToggle { ExampleConfig.switch = it }
                        }
                        surface.triggerUpdate()
                    }
                }

            }.open()
        }
    }

    @SubscribeEvent
    fun onRenderOverlay(event: RenderOverlayEvent.Pre) {
        if (event.overlay != GuiOverlay.HOTBAR) return
        CompilationStatus.overlay.render()
    }
}

@ConfigName("gui/example")
object ExampleConfig: Config() {
    var switch by property(false)
}