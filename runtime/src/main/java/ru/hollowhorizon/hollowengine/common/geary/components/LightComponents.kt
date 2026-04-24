package ru.hollowhorizon.hollowengine.common.geary.components

import ru.hollowhorizon.hollowengine.common.geary.api.Component
import de.fabmax.kool.math.Vec3f
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.api.Syncable
import ru.hollowhorizon.hollowengine.common.geary.anchor.removeComponents
import ru.hollowhorizon.hollowengine.common.geary.anchor.withOrReplace
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot

@Serializable
sealed class LightComponent {
    abstract val enabled: Boolean
    abstract val color: LightColor
    abstract val intensity: Float
    abstract val shadow: ShadowSettings?
    abstract val volumetricFog: VolumetricFogSettings?
    abstract val flare: FlareSettings?

    val hasShadow: Boolean get() = enabled && shadow?.enabled == true
    val hasVolumetricFog: Boolean get() = enabled && volumetricFog?.enabled == true
    val hasFlare: Boolean get() = enabled && flare?.enabled == true

    fun colorVector(): Vec3f = Vec3f(color.r, color.g, color.b)
}

@Serializable
@EditorIcon("hollowengine:textures/gui/icons/eye.svg")
data class LightColor(
    @EditorName("Red")
    @EditorRange(0f, 1f)
    val r: Float = 1f,
    @EditorName("Green")
    @EditorRange(0f, 1f)
    val g: Float = 1f,
    @EditorName("Blue")
    @EditorRange(0f, 1f)
    val b: Float = 1f,
)

@Serializable
@EditorIcon("hollowengine:textures/gui/icons/world.svg")
data class ShadowSettings(
    @EditorName("Enabled")
    val enabled: Boolean = true,
    @EditorName("Dynamic")
    val dynamic: Boolean = false,
    @EditorName("Distance")
    @EditorRange(0f, 256f)
    val shadowDistance: Float = 50f,
    @EditorName("Fov Offset")
    @EditorRange(-180f, 180f)
    val fovOffset: Float = 45f,
)

@Serializable
@EditorIcon("hollowengine:textures/gui/icons/world.svg")
data class VolumetricFogSettings(
    @EditorName("Enabled")
    val enabled: Boolean = true,
    @EditorName("Sample Count")
    @EditorRange(1f, 128f)
    val sampleCount: Int = 20,
    @EditorName("Scattering")
    @EditorRange(0f, 1f)
    val scattering: Float = 0.05f,
    @EditorName("Density")
    @EditorRange(0f, 5f)
    val density: Float = 0.05f,
    @EditorName("Anisotropy")
    @EditorRange(-1f, 1f)
    val anisotropy: Float = 0f,
)

@Serializable
@EditorIcon("hollowengine:textures/gui/icons/eye.svg")
data class FlareSettings(
    @EditorName("Enabled")
    val enabled: Boolean = true,
    @EditorName("Size Offset")
    @EditorRange(0f, 2f)
    val sizeOffset: Float = 0f,
    @EditorName("Falloff Distance")
    @EditorRange(0f, 100f)
    val falloffDistance: Float = 20f,
    @EditorName("Start Angle")
    @EditorRange(0f, 360f)
    val startAngle: Float = 30f,
    @EditorName("End Angle")
    @EditorRange(0f, 360f)
    val endAngle: Float = 0f,
    @EditorName("Angle Factor Offset")
    @EditorRange(0f, 5f)
    val angleFactorOffset: Float = 1.2f,
    @EditorName("Intensity")
    @EditorRange(0f, 1f)
    val intensity: Float = 0.1f,
)

@Registerable
@Syncable
@Serializable
@SerialName("hollowengine:light/point")
@EditorIcon("hollowengine:textures/gui/icons/eye.svg")
data class PointLightComponent(
    @EditorName("Enabled")
    override val enabled: Boolean = true,
    @EditorName("Color")
    override val color: LightColor = LightColor(),
    @EditorName("Intensity")
    @EditorRange(0f, 100f)
    override val intensity: Float = 1f,
    @EditorName("Shadow")
    override val shadow: ShadowSettings? = null,
    @EditorName("Volumetric Fog")
    override val volumetricFog: VolumetricFogSettings? = null,
    @EditorName("Flare")
    override val flare: FlareSettings? = null,
    @EditorName("Radius")
    @EditorRange(0f, 512f)
    val radius: Float = 10f,
) : LightComponent()

@Registerable
@Syncable
@Serializable
@SerialName("hollowengine:light/spot")
@EditorIcon("hollowengine:textures/gui/icons/eye.svg")
data class SpotLightComponent(
    @EditorName("Enabled")
    override val enabled: Boolean = true,
    @EditorName("Color")
    override val color: LightColor = LightColor(),
    @EditorName("Intensity")
    @EditorRange(0f, 100f)
    override val intensity: Float = 1f,
    @EditorName("Shadow")
    override val shadow: ShadowSettings? = null,
    @EditorName("Volumetric Fog")
    override val volumetricFog: VolumetricFogSettings? = null,
    @EditorName("Flare")
    override val flare: FlareSettings? = null,
    @EditorName("Inner Angle")
    @EditorRange(0f, 90f)
    val innerAngle: Float = 10f,
    @EditorName("Outer Angle")
    @EditorRange(0f, 90f)
    val outerAngle: Float = 20f,
    @EditorName("Distance")
    @EditorRange(0f, 256f)
    val distance: Float = 20f,
) : LightComponent()

fun EntitySnapshot.lightComponentOrNull(): LightComponent? =
    components.filterIsInstance<LightComponent>().firstOrNull()

fun EntitySnapshot.pointLightOrNull(): PointLightComponent? =
    components.filterIsInstance<PointLightComponent>().firstOrNull()

fun EntitySnapshot.spotLightOrNull(): SpotLightComponent? =
    components.filterIsInstance<SpotLightComponent>().firstOrNull()

fun EntitySnapshot.withLightComponent(component: LightComponent): EntitySnapshot =
    removeComponents { it is PointLightComponent || it is SpotLightComponent }
        .withOrReplace(component as Component)
