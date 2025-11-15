package ru.hollowhorizon.hollowengine.common.scripting.renderer

import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.client.models.internal.animations.NodeInstance
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    displayName = "Renderer Script",
    fileExtension = "renderer.kts",
    compilationConfiguration = HollowScriptConfiguration::class)
abstract class RendererScript {
    val root = NodeInstance.Root()

    open fun update(entity: LivingEntity) {

    }
}