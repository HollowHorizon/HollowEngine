package ru.hollowhorizon.hollowengine.client.shadergraph

import kotlinx.serialization.Serializable

/** A value that flows along a link of a shader graph. */
@Serializable
enum class ShaderType(val glsl: String, val width: Int) {
    FLOAT("float", 1), VEC2("vec2", 2), VEC3("vec3", 3), VEC4("vec4", 4),

    /** A texture to sample; it only ever connects to another texture pin. */
    TEXTURE("sampler2D", 0);

    val isVector: Boolean get() = width > 0

    companion object {
        fun ofWidth(width: Int): ShaderType = when (width) {
            1 -> FLOAT
            2 -> VEC2
            3 -> VEC3
            else -> VEC4
        }
    }
}

/**
 * The type a pin is declared with: a fixed one; [DYNAMIC], where every dynamic pin of the node takes
 * the widest vector any of them is given; [ANY], which keeps whatever vector it is given as it is; or
 * [PASS], which keeps whatever it is given, a texture too, for a node that only carries a link along.
 */
enum class ShaderPinType(val fixed: ShaderType?) {
    FLOAT(ShaderType.FLOAT), VEC2(ShaderType.VEC2), VEC3(ShaderType.VEC3), VEC4(ShaderType.VEC4), TEXTURE(ShaderType.TEXTURE), DYNAMIC(
        null
    ),
    ANY(null),
    PASS(null);

    /** Whether the pin takes the type of what is linked into it, rather than the node's or its own. */
    val keepsLinkedType: Boolean get() = this == ANY || this == PASS

    /** Whether a value of [type] can be linked into a pin of this type. */
    fun accepts(type: ShaderType): Boolean = when {
        this == PASS -> true
        fixed == null -> type.isVector
        else -> coerce("", type, fixed) != null
    }

    companion object {
        fun of(type: ShaderType): ShaderPinType = entries.first { it.fixed == type }
    }
}

/**
 * [expression] of type [from] read as [to]: a number spreads over every component, a vector is cut
 * down or padded (with 0, and 1 for alpha). Null when there is no such reading, which is anything to
 * or from a texture.
 */
fun coerce(expression: String, from: ShaderType, to: ShaderType): String? {
    if (from == to) return expression
    if (!from.isVector || !to.isVector) return null
    return when {
        from == ShaderType.FLOAT -> "${to.glsl}($expression)"
        to == ShaderType.FLOAT -> "($expression).x"
        to.width < from.width -> "($expression).${"xyzw".take(to.width)}"
        to == ShaderType.VEC4 && from == ShaderType.VEC3 -> "vec4($expression, 1.0)"
        to == ShaderType.VEC4 -> "vec4($expression, 0.0, 1.0)"
        else -> "vec3($expression, 0.0)"
    }
}

/** A GLSL literal of [type] out of [values], missing components taken from the last one given. */
fun shaderLiteral(type: ShaderType, values: List<Float>): String {
    val components = (0 until type.width).map { values.getOrElse(it) { values.lastOrNull() ?: 0f } }
    if (type == ShaderType.FLOAT) return glslFloat(components.first())
    if (components.distinct().size == 1) return "${type.glsl}(${glslFloat(components.first())})"
    return "${type.glsl}(${components.joinToString(", ") { glslFloat(it) }})"
}

/** A float as GLSL reads it: always with a point, never with a locale's comma. */
fun glslFloat(value: Float): String {
    if (value.isNaN() || value.isInfinite()) return "0.0"
    val text = value.toString()
    return if ('.' in text || 'E' in text || 'e' in text) text else "$text.0"
}

/**
 * What the engine hands a graph wherever it runs: each template declares the ones it can offer and
 * defines them under these names before the graph's code. The functions among them read the scene
 * where there is one and stand in for it where there is not, as in a node preview.
 */
enum class ShaderInput(
    val glsl: String,
    val type: ShaderType,
    val fragmentOnly: Boolean = false,
    val expressionName: String? = null,
) {
    /** 0 to 1 across the surface, whatever part of the texture it shows, or across the screen for a post effect. */
    UV("sg_uv", ShaderType.VEC2, expressionName = "uv"),

    /** The part of the material texture the surface shows, flipbook frame included. */
    TEXTURE_UV("sg_texture_uv", ShaderType.VEC2),

    /**
     * A function from a UV across the surface to the texture UV that shows it, flipbook frame
     * included; `sg_frame_uv(sg_uv)` is [TEXTURE_UV].
     */
    FRAME_UV("sg_frame_uv", ShaderType.VEC2),

    /** The particle color, tint and light already multiplied in. */
    COLOR("sg_color", ShaderType.VEC4, expressionName = "color"),

    /** Seconds; in game they run on as long as the game does, in the editor from when it opened. */
    TIME("sg_time", ShaderType.FLOAT, expressionName = "time"),

    /** Where the point is, relative to the camera, in blocks. */
    POSITION("sg_position", ShaderType.VEC3, expressionName = "position"),

    /**
     * Where the point is on the object itself, -1 to 1 across it: a particle, a built-in mesh, the mesh
     * of a preview.
     */
    OBJECT_POSITION("sg_object_position", ShaderType.VEC3, expressionName = "local"), NORMAL(
        "sg_normal",
        ShaderType.VEC3,
        expressionName = "normal"
    ),

    /** From the point toward the eye, unit length. */
    VIEW_DIRECTION("sg_view_direction", ShaderType.VEC3, expressionName = "view"),

    /** Where on the screen the fragment is, 0 to 1. */
    SCREEN_UV("sg_screen_uv", ShaderType.VEC2, fragmentOnly = true),

    /** The texture the material names; for a post effect, the frame it draws over. */
    MAIN_TEXTURE("Sampler0", ShaderType.TEXTURE),

    /** Blocks from the eye to whatever the frame had drawn behind this fragment. */
    SCENE_DEPTH("sg_scene_depth()", ShaderType.FLOAT, fragmentOnly = true),

    /** Blocks from the eye to this fragment. */
    FRAGMENT_DEPTH("sg_fragment_depth()", ShaderType.FLOAT, fragmentOnly = true),

    /** A function of a screen UV: the frame as it was before the effect drew. */
    SCENE_COLOR("sg_scene_color", ShaderType.VEC3, fragmentOnly = true);

    companion object {
        fun forExpression(name: String): ShaderInput? = entries.firstOrNull { it.expressionName == name }
    }
}
