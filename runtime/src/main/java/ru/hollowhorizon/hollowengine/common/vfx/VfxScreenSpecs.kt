package ru.hollowhorizon.hollowengine.common.vfx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.vfx.VfxAnimatables.scalar

/**
 * A full-screen pass over the frame while the node is on: a core shader drawn over the whole screen,
 * reading the frame so far as `SceneColor` and its depth as `SceneDepth`.
 *
 * How strong it is, and whether it fades with distance, is up to the uniforms the author declares:
 * an expression such as `1 - n.camera_distance / 32` makes it weaker the farther away the node is.
 */
@Serializable
@SerialName("hollowengine:vfx/post_effect")
data class VfxPostEffectSpec(
    override val id: String = newVfxNodeId("post"),
    override val name: String = "Post Effect",
    override val enabled: Boolean = true,
    override val transform: VfxTransform = VfxTransform.IDENTITY,
    override val children: List<VfxNodeSpec> = emptyList(),
    /** `namespace:path` of `assets/namespace/shaders/core/path.json`. */
    val shader: String = "hollowengine:vfx/post/grayscale",
    val uniforms: List<VfxUniformSpec> = listOf(VfxUniformSpec("Strength", VfxUniformValue.Scalar(VfxValue.ONE))),
    val samplers: List<VfxSamplerSpec> = emptyList(),
) : VfxNodeSpec() {
    override fun withCommon(
        id: String,
        name: String,
        enabled: Boolean,
        transform: VfxTransform,
        children: List<VfxNodeSpec>,
    ) = copy(id = id, name = name, enabled = enabled, transform = transform, children = children)

    override fun expressions(): List<String> = uniforms.flatMap { it.value.sources() }

    override fun animatables(): List<VfxAnimatable> = uniforms.animatables()
}

/**
 * Shakes the camera of whoever sees the effect while the node is on.
 *
 * [strength] scales everything and is the place for a distance falloff, such as
 * `1 - n.camera_distance / 24`.
 */
@Serializable
@SerialName("hollowengine:vfx/camera_shake")
data class VfxCameraShakeSpec(
    override val id: String = newVfxNodeId("shake"),
    override val name: String = "Camera Shake",
    override val enabled: Boolean = true,
    override val transform: VfxTransform = VfxTransform.IDENTITY,
    override val children: List<VfxNodeSpec> = emptyList(),
    val strength: VfxValue = VfxValue.ONE,
    /** Shakes per second. */
    val frequency: VfxValue = VfxValue.Const(14f),
    /** The widest swing in degrees: pitch, yaw and roll. */
    val amplitude: Vec3f = Vec3f(1.5f, 1.5f, 0.75f),
) : VfxNodeSpec() {
    override fun withCommon(
        id: String,
        name: String,
        enabled: Boolean,
        transform: VfxTransform,
        children: List<VfxNodeSpec>,
    ) = copy(id = id, name = name, enabled = enabled, transform = transform, children = children)

    override fun expressions(): List<String> = strength.sources() + frequency.sources()

    override fun animatables(): List<VfxAnimatable> = listOf(
        scalar(VfxProperty.SHAKE_STRENGTH, "shake_strength", strength),
        scalar(VfxProperty.SHAKE_FREQUENCY, "shake_frequency", frequency),
    )
}
