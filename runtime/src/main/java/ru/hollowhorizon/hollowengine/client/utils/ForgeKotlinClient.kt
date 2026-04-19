package ru.hollowhorizon.hollowengine.client.utils

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.ModelInstancingBackend
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.VanillaInstancingBackend
import ru.hollowhorizon.hollowengine.common.utils.HollowJavaUtils
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import java.io.InputStream

// Only Client utils
val mc: Minecraft get() = Minecraft.getInstance()

enum class Axis(val x: Float, val y: Float, val z: Float) {
    X(1f, 0f, 0f),
    Y(0f, 1f, 0f),
    Z(0f, 0f, 1f);
}

var instancingBackendProvider: () -> ModelInstancingBackend = { VanillaInstancingBackend }
var instancingEntityInfoProvider: () -> InstancingEntityInfo = { InstancingEntityInfo() }
var areShadersEnabled_: () -> Boolean = { false }
var shouldOverrideShaders: () -> Boolean = { false }

val instancingBackend get() = instancingBackendProvider()
val instancingEntityInfo get() = instancingEntityInfoProvider()
val areShadersEnabled get() = areShadersEnabled_()

data class InstancingEntityInfo(
    val entity: Int = -1,
    val blockEntity: Int = 0,
    val item: Int = -1,
)

val registryAccess: RegistryAccess
    get() = if (currentServer is IntegratedServer) Minecraft.getInstance().connection?.registryAccess()
        ?: currentServer.registryAccess()
    else currentServer.registryAccess()


fun ResourceLocation.exists(): Boolean {
    return try {
        mc.resourceManager.getResource(this).isPresent
    } catch (e: Exception) {
        true
    }
}

val ResourceLocation.stream: InputStream
    get() = HollowJavaUtils.getResource(this)

fun Screen.open() {
    mc.setScreen(this)
}

fun ResourceLocation.toTexture(): AbstractTexture = mc.textureManager.getTexture(this)


inline fun PoseStack.use(usable: PoseStack.() -> Unit) {
    this.pushPose()
    usable()
    this.popPose()
}
