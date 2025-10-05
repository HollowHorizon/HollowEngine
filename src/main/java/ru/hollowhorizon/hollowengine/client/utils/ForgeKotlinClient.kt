package ru.hollowhorizon.hollowengine.client.utils

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.client.server.IntegratedServer
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.utils.HollowJavaUtils
import ru.hollowhorizon.hollowengine.common.utils.ModList
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.io.InputStream

// Only Client utils
val mc: Minecraft get() = Minecraft.getInstance()

enum class Axis(val x: Float, val y: Float, val z: Float) {
    X(1f, 0f, 0f),
    Y(0f, 1f, 0f),
    Z(0f, 0f, 1f);
}

val hasShaders get() = ModList.isLoaded("oculus") || ModList.isLoaded("iris") || ModList.isLoaded("optifine")

val areShadersEnabled get() = hasShaders && areShadersEnabled_()

lateinit var areShadersEnabled_: () -> Boolean
lateinit var shouldOverrideShaders: () -> Boolean

val registryAccess: RegistryAccess
    get() = if (currentServer is IntegratedServer) Minecraft.getInstance().connection?.registryAccess()
        ?: currentServer.registryAccess()
    else currentServer.registryAccess()


fun ResourceLocation.exists(): Boolean {
    return mc.resourceManager.getResource(this).isPresent
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
