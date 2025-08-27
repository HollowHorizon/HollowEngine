package ru.hollowhorizon.hollowengine.common.events.client.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.culling.Frustum
import org.joml.Matrix4f
import ru.hollowhorizon.hollowengine.common.events.Event

open class RenderLevelStageEvent(
    val renderer: LevelRenderer,
    val poseStack: PoseStack,
    val projectionMatrix: Matrix4f,
    val renderTick: Int, val partialTick: Float,
    val camera: Camera, val frustum: Frustum?,
    val stage: RenderStage,
) : Event

enum class RenderStage {
    /**
     * After rendering sky.
     */
    AFTER_SKY,
    /**
     * After rendering solid blocks like stone, dirt, etc.
     */
    AFTER_SOLID_BLOCKS,
    /**
     * After rendering cutout blocks (full transparent) with mipmapping. (like plants)
     */
    AFTER_CUTOUT_MIPPED_BLOCKS,
    /**
     * After rendering cutout blocks (full transparent) without mipmapping. (like glass)
     */
    AFTER_CUTOUT_BLOCKS,
    /**
     * After rendering entities.
     */
    AFTER_ENTITIES,
    /**
     * After rendering block entities.
     */
    AFTER_BLOCK_ENTITIES,
    /**
     * After rendering translucent blocks (semi-transparent), like colored glass.
     */
    AFTER_TRANSLUCENT_BLOCKS,
    /**
     * After rendering tripwire blocks. (like strings)
     */
    AFTER_TRIPWIRE_BLOCKS,
    /**
     * After rendering particles.
     */
    AFTER_PARTICLES,
    /**
     * After rendering weather.
     */
    AFTER_WEATHER,
    /**
     * After draw all level.
     */
    AFTER_LEVEL
}