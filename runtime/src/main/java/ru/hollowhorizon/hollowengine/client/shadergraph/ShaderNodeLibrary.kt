package ru.hollowhorizon.hollowengine.client.shadergraph

import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderNodeCategory.*
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderNodeCategory.TEXTURE
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderPinType.FLOAT
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderPinType.VEC2
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderPinType.VEC3
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderPinType.VEC4

/** The kinds of node the engine ships, in the order the add menu lists them. */
object ShaderNodeLibrary {
    const val SURFACE_OUTPUT = "hollowengine:output/surface"
    const val POST_OUTPUT = "hollowengine:output/post"
    const val PROPERTY = "hollowengine:input/property"
    const val EXPRESSION = ShaderMathNodes.EXPRESSION
    const val REROUTE = "hollowengine:reroute"
    const val REROUTE_INPUT = "In"
    const val REROUTE_OUTPUT = "Out"

    val all: List<ShaderNodeType> by lazy {
        inputs() + uv() + texture() + output() + ShaderMathNodes.all + ShaderNormalNodes.all + reroute()
    }

    /** What a new graph of [target] starts as. */
    fun default(target: ShaderTarget): ShaderGraph = when (target) {
        ShaderTarget.SURFACE -> defaultSurface()
        ShaderTarget.POST -> defaultPost()
    }

    /** What a new post effect starts as: the frame passed through untouched. */
    fun defaultPost() = ShaderGraph(
        target = ShaderTarget.POST,
        nodes = listOf(
            ShaderGraphNode("scene", "hollowengine:input/scene_color", x = -300f, y = 0f),
            ShaderGraphNode("output", POST_OUTPUT, x = -60f, y = 0f),
        ),
        links = listOf(ShaderGraphLink("scene", "RGB", "output", PostOutputs.COLOR)),
    )

    /**
     * What a new surface graph starts as: the material texture times the particle color, which is what
     * a material without a graph draws, so a new graph changes nothing until it is edited.
     */
    fun defaultSurface() = ShaderGraph(
        nodes = listOf(
            ShaderGraphNode("texture", "hollowengine:texture/sample", x = -520f, y = -40f),
            ShaderGraphNode("color", "hollowengine:input/vertex_color", x = -520f, y = 190f),
            ShaderGraphNode("tint", "hollowengine:math/multiply", x = -300f, y = 0f),
            ShaderGraphNode("alpha", "hollowengine:vector/split", x = -300f, y = 190f),
            ShaderGraphNode("output", SURFACE_OUTPUT, x = -60f, y = 0f),
        ),
        links = listOf(
            ShaderGraphLink("texture", "RGBA", "tint", "A"),
            ShaderGraphLink("color", "RGBA", "tint", "B"),
            ShaderGraphLink("tint", "Out", "output", SurfaceOutputs.COLOR),
            ShaderGraphLink("tint", "Out", "alpha", "In"),
            ShaderGraphLink("alpha", "A", "output", SurfaceOutputs.ALPHA),
        ),
    )

    private fun inputs() = listOf(
        engineInput("uv", ShaderInput.UV, "UV", "coordinates"),
        engineInput("texture_uv", ShaderInput.TEXTURE_UV, "UV", "coordinates"),
        engineInput("position", ShaderInput.POSITION, "Position", "coordinates"),
        engineInput("object_position", ShaderInput.OBJECT_POSITION, "Position", "coordinates"),
        engineInput("normal", ShaderInput.NORMAL, "Normal"),
        engineInput("view_direction", ShaderInput.VIEW_DIRECTION, "Direction"),
        engineInput("screen_uv", ShaderInput.SCREEN_UV, "UV", "coordinates"),

        shaderNode("hollowengine:input/vertex_color", INPUT) {
            group("surface")
            noPreview()
            reads(ShaderInput.COLOR)
            icon(graphIcon("color"))
            output("RGBA", VEC4) { ShaderInput.COLOR.glsl }
            output("RGB", VEC3) { "${ShaderInput.COLOR.glsl}.rgb" }
            output("A", FLOAT) { "${ShaderInput.COLOR.glsl}.a" }
        },

        shaderNode("hollowengine:input/time", INPUT) {
            group("time")
            icon(graphIcon("time"))
            noPreview()
            reads(ShaderInput.TIME)
            output("Time", FLOAT) { ShaderInput.TIME.glsl }
            output("Sine", FLOAT) { "sin(${ShaderInput.TIME.glsl})" }
        },

        shaderNode("hollowengine:input/scene_depth", INPUT) {
            group("scene")
            noPreview()
            reads(ShaderInput.SCENE_DEPTH, ShaderInput.FRAGMENT_DEPTH)
            fragmentOnly()
            output("Scene", FLOAT) { ShaderInput.SCENE_DEPTH.glsl }
            output("Fragment", FLOAT) { ShaderInput.FRAGMENT_DEPTH.glsl }
            output("Difference", FLOAT) { "${output("Scene")} - ${output("Fragment")}" }
        },

        shaderNode("hollowengine:input/scene_color", INPUT) {
            group("scene")
            noPreview()
            reads(ShaderInput.SCENE_COLOR)
            icon(graphIcon("color"))
            fragmentOnly()
            val uv = input("UV", type = VEC2, fallback = ShaderInput.SCREEN_UV)
            output("RGB", VEC3) { "${ShaderInput.SCENE_COLOR.glsl}(${uv.code})" }
        },

        constant("float", FLOAT, 0f),
        constant("vector2", VEC2, 0f, 0f),
        constant("vector3", VEC3, 0f, 0f, 0f),
        constant("vector4", VEC4, 0f, 0f, 0f, 0f),
        shaderNode("hollowengine:input/color", INPUT) {
            group("value")
            noPreview()
            icon(graphIcon("color"))
            val color = input("Color", 1f, 1f, 1f, 1f, type = VEC4, color = true)
            output("RGBA", VEC4) { color.code }
            output("RGB", VEC3) { "${output("RGBA")}.rgb" }
        },
        shaderNode(PROPERTY, INPUT) {
            group("value")
            noPreview()
            property("property")
            icon(graphIcon("property"))
            output("Out", { graph.property(node.options["property"].orEmpty())?.type?.let(ShaderPinType::of) }) {
                propertyUniform(option("property"))
            }
        },
        shaderNode("hollowengine:input/main_texture", INPUT) {
            group("value")
            reads(ShaderInput.MAIN_TEXTURE)
            icon(graphIcon("texture"))
            output("Texture", ShaderPinType.TEXTURE) { ShaderInput.MAIN_TEXTURE.glsl }
        },
    )

    private fun uv() = listOf(
        shaderNode("hollowengine:uv/tiling_offset", UV) {
            val uv = input("UV", type = VEC2, fallback = ShaderInput.UV)
            val tiling = input("Tiling", 1f, 1f, type = VEC2)
            val offset = input("Offset", 0f, 0f, type = VEC2)
            output("Out", VEC2) { "${uv.code} * ${tiling.code} + ${offset.code}" }
        },
        shaderNode("hollowengine:uv/rotate", UV) {
            uses(ShaderLibrary.ROTATE)
            val uv = input("UV", type = VEC2, fallback = ShaderInput.UV)
            val center = input("Center", 0.5f, 0.5f, type = VEC2)
            val degrees = input("Degrees", 0f, type = FLOAT)
            output("Out", VEC2) { "sg_rotate(${uv.code}, ${center.code}, ${degrees.code})" }
        },
        shaderNode("hollowengine:uv/polar", UV) {
            uses(ShaderLibrary.POLAR)
            val uv = input("UV", type = VEC2, fallback = ShaderInput.UV)
            val center = input("Center", 0.5f, 0.5f, type = VEC2)
            output("Out", VEC2) { "sg_polar(${uv.code}, ${center.code})" }
        },
        shaderNode("hollowengine:uv/scroll", UV) {
            reads(ShaderInput.TIME)
            val uv = input("UV", type = VEC2, fallback = ShaderInput.UV)
            val speed = input("Speed", 0f, 0.5f, type = VEC2)
            output("Out", VEC2) { "${uv.code} + ${speed.code} * ${ShaderInput.TIME.glsl}" }
        },
    )

    private fun texture() = listOf(
        shaderNode("hollowengine:texture/sample", TEXTURE) {
            toggle("flip_x")
            toggle("flip_y")
            reads(ShaderInput.FRAME_UV)
            val texture = input("Texture", type = ShaderPinType.TEXTURE, fallback = ShaderInput.MAIN_TEXTURE)
            val uv = input("UV", type = VEC2, fallback = ShaderInput.TEXTURE_UV)
            output("RGBA", VEC4) {
                val at = sampledUv(uv.code, uv.linked, toggle("flip_x"), toggle("flip_y"))
                if (vertex) "textureLod(${texture.code}, $at, 0.0)" else "texture(${texture.code}, $at)"
            }
            output("RGB", VEC3) { "${output("RGBA")}.rgb" }
            output("A", FLOAT) { "${output("RGBA")}.a" }
        },
    )

    /**
     * Where a sample reads: [uv] as it is, or turned over. With nothing linked the surface UV is turned
     * over and then mapped into the texture, so a flipbook frame turns over in place.
     */
    private fun sampledUv(uv: String, linked: Boolean, flipX: Boolean, flipY: Boolean): String {
        if (!flipX && !flipY) return uv
        fun flip(of: String): String {
            val x = if (flipX) "1.0 - $of.x" else "$of.x"
            val y = if (flipY) "1.0 - $of.y" else "$of.y"
            return "vec2($x, $y)"
        }
        return if (linked) flip("($uv)") else "${ShaderInput.FRAME_UV.glsl}(${flip(ShaderInput.UV.glsl)})"
    }

    /**
     * A point a link passes through, to lead it around other nodes: whatever comes in goes out as it
     * is, a texture too. The editor draws it as a dot and leaves it out of the categories of the add menu.
     */
    private fun reroute() = shaderNode(REROUTE, INPUT) {
        noPreview()
        val value = input(REROUTE_INPUT, 0f, type = ShaderPinType.PASS)
        output(REROUTE_OUTPUT, typeOf = { input(REROUTE_INPUT)?.let(ShaderPinType::of) }) { value.code }
    }

    private fun output() = listOf(
        shaderNode(SURFACE_OUTPUT, OUTPUT) {
            master(ShaderTarget.SURFACE, SurfaceOutputs.VERTEX_OFFSET)
            input(SurfaceOutputs.COLOR, 1f, 1f, 1f, type = VEC3, color = true)
            input(SurfaceOutputs.ALPHA, 1f, type = FLOAT)
            input(SurfaceOutputs.EMISSION, 0f, 0f, 0f, type = VEC3, color = true)
            input(SurfaceOutputs.ALPHA_CLIP, 0f, type = FLOAT)
            input(SurfaceOutputs.VERTEX_OFFSET, 0f, 0f, 0f, type = VEC3)
        },
        shaderNode(POST_OUTPUT, OUTPUT) {
            master(ShaderTarget.POST)
            input(PostOutputs.COLOR, 1f, 1f, 1f, type = VEC3, color = true)
            input(PostOutputs.ALPHA, 1f, type = FLOAT)
        },
    )

    /** A node that hands out one thing the engine gives every graph. */
    private fun engineInput(name: String, input: ShaderInput, output: String, iconName: String? = null) =
        shaderNode("hollowengine:input/$name", INPUT) {
            group("surface")
            iconName?.let { icon(graphIcon(it)) }
            reads(input)
            if (input.fragmentOnly) fragmentOnly()
            if (input == ShaderInput.NORMAL || input == ShaderInput.VIEW_DIRECTION) preview(ShaderPreviewStyle.SIGNED)
            output(output, ShaderPinType.of(input.type)) { input.glsl }
        }

    /** A value typed in on the node itself: an input with nothing to link, passed straight out. */
    private fun constant(name: String, type: ShaderPinType, vararg default: Float) =
        shaderNode("hollowengine:input/$name", INPUT) {
            group("value")
            noPreview()
            val value = input("Value", *default, type = type)
            output("Out", type) { value.code }
        }
}

/** The inputs of the surface output node, which the templates read back by these names. */
object SurfaceOutputs {
    const val COLOR = "Color"
    const val ALPHA = "Alpha"

    /** What glows in the glow pass; left unlinked, the color does, as with a material that has no graph. */
    const val EMISSION = "Emission"

    /** Fragments with less alpha than this are dropped. */
    const val ALPHA_CLIP = "Alpha Clip"

    /** Blocks the vertex moves by, in the space of the view. */
    const val VERTEX_OFFSET = "Vertex Offset"
}

/**
 * The inputs of the post output node. They are named as the surface ones are, so switching a graph
 * between the two keeps what is linked into them.
 */
object PostOutputs {
    const val COLOR = SurfaceOutputs.COLOR

    /** How much of the frame the color replaces: 0 leaves the frame as it was. */
    const val ALPHA = SurfaceOutputs.ALPHA
}

/** The uniform a property reads, named so it cannot clash with anything of the engine's. */
fun propertyUniform(property: String): String = "p_" + property.filter { it.isLetterOrDigit() || it == '_' }
