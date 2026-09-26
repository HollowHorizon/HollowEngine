package ru.hollowhorizon.hollowengine.client.shadergraph

import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderNodeCategory.NORMAL
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderPinType.FLOAT
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderPinType.VEC3

/**
 * Normals, moving the surface, and light that shows both. Normals and positions are in the axes of
 * the world, the position relative to the camera, in game and in the previews alike.
 */
internal object ShaderNormalNodes {
    val all: List<ShaderNodeType> by lazy { listOf(offset(), bump(), surfaceNormal(), lambert()) }

    /** How far to move a vertex along its normal: link it into the vertex offset of the output. */
    private fun offset() = shaderNode("hollowengine:normal/offset", NORMAL) {
        group("displace")
        preview(ShaderPreviewStyle.DISPLACEMENT)
        val amount = input("Amount", 0.1f, type = FLOAT)
        val normal = input("Normal", type = VEC3, fallback = ShaderInput.NORMAL)
        output("Offset", VEC3) { "normalize(${normal.code}) * ${amount.code}" }
    }

    /**
     * The normal tilted by how [Height] changes across the surface: fine detail a mesh does not have,
     * such as ripples, for a fresnel or light to pick up. Strength is how many blocks a height of one is.
     */
    private fun bump() = shaderNode("hollowengine:normal/bump", NORMAL) {
        group("normals")
        preview(ShaderPreviewStyle.SIGNED)
        uses(ShaderLibrary.NORMALS)
        fragmentOnly()
        val height = input("Height", 0f, type = FLOAT)
        val strength = input("Strength", 0.1f, type = FLOAT)
        val normal = input("Normal", type = VEC3, fallback = ShaderInput.NORMAL)
        val position = input("Position", type = VEC3, fallback = ShaderInput.POSITION)
        output("Normal", VEC3) { "sg_bump(normalize(${normal.code}), ${position.code}, ${height.code}, ${strength.code})" }
    }

    /**
     * The normal of the surface as it is drawn, worked out from the position: after a vertex offset
     * has moved it, which the normal of the mesh knows nothing about. Faceted, one per triangle.
     */
    private fun surfaceNormal() = shaderNode("hollowengine:normal/surface", NORMAL) {
        group("normals")
        preview(ShaderPreviewStyle.SIGNED)
        uses(ShaderLibrary.NORMALS)
        fragmentOnly()
        val position = input("Position", type = VEC3, fallback = ShaderInput.POSITION)
        val view = input("View", type = VEC3, fallback = ShaderInput.VIEW_DIRECTION)
        output("Normal", VEC3) { "sg_surface_normal(${position.code}, ${view.code})" }
    }

    /**
     * How much light a surface facing [Normal] gets from [Direction], 0 to 1; Wrap lets it reach
     * around the unlit side, as it does through something soft.
     */
    private fun lambert() = shaderNode("hollowengine:normal/lambert", NORMAL) {
        group("light")
        val normal = input("Normal", type = VEC3, fallback = ShaderInput.NORMAL)
        val direction = input("Direction", 0.4f, 1f, 0.3f, type = VEC3)
        val wrap = input("Wrap", 0f, type = FLOAT)
        output("Light", FLOAT) {
            "clamp((dot(normalize(${normal.code}), normalize(${direction.code})) + ${wrap.code}) / (1.0 + ${wrap.code}), 0.0, 1.0)"
        }
    }
}
