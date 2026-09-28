package ru.hollowhorizon.hollowengine.client.vfx.render

import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderGraph
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderGraphProperty
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderType
import ru.hollowhorizon.hollowengine.common.vfx.VfxUniformValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VfxGraphMaterialsTest {
    @Test
    fun `the properties of a material are what an effect gives values to, under their own names`() {
        val graph = ShaderGraph(
            properties = listOf(
                ShaderGraphProperty("Speed", ShaderType.FLOAT, listOf(2f)),
                ShaderGraphProperty("Tint", ShaderType.VEC4, listOf(1f, 0.5f, 0f, 1f)),
                ShaderGraphProperty("Noise", ShaderType.TEXTURE, texture = "hollowengine:textures/noise.png"),
            ),
        )
        val declaration = VfxGraphMaterials.declaration(graph)

        assertEquals(listOf("Speed", "Tint"), declaration.uniforms.map { it.name })
        assertTrue(declaration.uniform("Tint")!!.defaultValue() is VfxUniformValue.Color)
        assertEquals(listOf("Noise"), declaration.samplers)
        assertEquals("p_Speed", declaration.glslName("Speed"))
        assertEquals("hollowengine:textures/noise.png", declaration.samplerDefaults["Noise"])
        assertTrue(declaration.drawsGlow)
    }

    @Test
    fun `a material is told from a core shader by its extension`() {
        assertTrue(VfxGraphMaterials.isGraph("hollowengine:materials/energy_orb.material"))
        assertEquals(false, VfxGraphMaterials.isGraph("hollowengine:vfx/distortion"))
        assertEquals("hollowengine:materials/orb.material", VfxGraphMaterials.locationOf("assets/hollowengine/materials/orb.material"))
    }
}
