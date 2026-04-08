import de.fabmax.kool.math.QuatF
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.render.lighting.hasClusteredLightingFeatureFlag
import ru.hollowhorizon.hollowengine.client.render.lighting.selectLogarithmicSlice
import ru.hollowhorizon.hollowengine.client.render.lighting.spotLightDirection
import ru.hollowhorizon.hollowengine.common.geary.components.FlareSettings
import ru.hollowhorizon.hollowengine.common.geary.components.LightColor
import ru.hollowhorizon.hollowengine.common.geary.components.PointLightComponent
import ru.hollowhorizon.hollowengine.common.geary.components.ShadowSettings
import ru.hollowhorizon.hollowengine.common.geary.components.SpotLightComponent
import ru.hollowhorizon.hollowengine.common.geary.components.VolumetricFogSettings
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClusteredLightingTests {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    @Test
    fun `point light serialization preserves nested settings`() {
        val light = PointLightComponent(
            enabled = false,
            color = LightColor(0.25f, 0.5f, 0.75f),
            intensity = 3.5f,
            radius = 12f,
            shadow = ShadowSettings(enabled = true, dynamic = true, shadowDistance = 42f, fovOffset = 30f),
            volumetricFog = VolumetricFogSettings(enabled = true, sampleCount = 24, scattering = 0.12f, density = 0.5f, anisotropy = 0.2f),
            flare = FlareSettings(enabled = true, sizeOffset = 0.3f, falloffDistance = 16f, startAngle = 20f, endAngle = 55f, angleFactorOffset = 1.4f, intensity = 0.7f),
        )

        val decoded = json.decodeFromString(PointLightComponent.serializer(), json.encodeToString(PointLightComponent.serializer(), light))

        assertEquals(light, decoded)
    }

    @Test
    fun `spot light serialization preserves nested settings`() {
        val light = SpotLightComponent(
            enabled = true,
            color = LightColor(1f, 0.6f, 0.2f),
            intensity = 5f,
            innerAngle = 15f,
            outerAngle = 35f,
            distance = 24f,
            shadow = ShadowSettings(enabled = true, dynamic = false, shadowDistance = 64f, fovOffset = 12f),
            volumetricFog = VolumetricFogSettings(enabled = true, sampleCount = 32, scattering = 0.2f, density = 0.35f, anisotropy = -0.15f),
            flare = FlareSettings(enabled = true, sizeOffset = 0.6f, falloffDistance = 20f, startAngle = 15f, endAngle = 40f, angleFactorOffset = 1.1f, intensity = 0.5f),
        )

        val decoded = json.decodeFromString(SpotLightComponent.serializer(), json.encodeToString(SpotLightComponent.serializer(), light))

        assertEquals(light, decoded)
    }

    @Test
    fun `spot light direction follows rotation`() {
        val identityDirection = spotLightDirection(QuatF.IDENTITY)
        assertEquals(0f, identityDirection.x, 1e-5f)
        assertEquals(0f, identityDirection.y, 1e-5f)
        assertEquals(1f, identityDirection.z, 1e-5f)

        val halfSqrt = sqrt(0.5f)
        val rotatePositiveZToPositiveX = QuatF(0f, halfSqrt, 0f, halfSqrt)
        val rotatedDirection = spotLightDirection(rotatePositiveZToPositiveX)
        assertEquals(1f, rotatedDirection.x, 1e-4f)
        assertEquals(0f, rotatedDirection.y, 1e-4f)
        assertEquals(0f, rotatedDirection.z, 1e-4f)
    }

    @Test
    fun `logarithmic slice selection clamps to valid range`() {
        assertEquals(0, selectLogarithmicSlice(0.01f, 0.05f, 256f, 12))
        assertEquals(11, selectLogarithmicSlice(512f, 0.05f, 256f, 12))
        assertTrue(selectLogarithmicSlice(1f, 0.05f, 256f, 12) < selectLogarithmicSlice(32f, 0.05f, 256f, 12))
    }

    @Test
    fun `feature flag detection accepts optional or required marker`() {
        assertTrue(hasClusteredLightingFeatureFlag(listOf("HE_CLUSTERED_LIGHTING"), emptyList()))
        assertTrue(hasClusteredLightingFeatureFlag(emptyList(), listOf("CUSTOM_IMAGES", "HE_CLUSTERED_LIGHTING")))
        assertFalse(hasClusteredLightingFeatureFlag(listOf("CUSTOM_IMAGES"), emptyList()))
    }
}
