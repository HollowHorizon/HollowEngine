package ru.hollowhorizon.hollowengine.client.vfx

import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.utils.expressions.Coercions
import ru.hollowhorizon.hollowengine.common.utils.expressions.CompiledUnit
import ru.hollowhorizon.hollowengine.common.utils.expressions.Declarations
import ru.hollowhorizon.hollowengine.common.utils.expressions.Expression
import ru.hollowhorizon.hollowengine.common.utils.expressions.References
import ru.hollowhorizon.hollowengine.common.utils.expressions.mathNamespace
import kotlin.random.Random

/**
 * What an effect expression can see.
 */
class VfxEvalContext {
    /** Random source of the emitter, so a seeded effect stays reproducible. */
    var rng: Random = Random.Default

    var age: Float = 0f
    var lifetime: Float = 1f

    /** Age over lifetime, 0 to 1. */
    var progress: Float = 0f

    var positionX: Float = 0f
    var positionY: Float = 0f
    var positionZ: Float = 0f

    var velocityX: Float = 0f
    var velocityY: Float = 0f
    var velocityZ: Float = 0f
    var speed: Float = 0f

    /** A number fixed when the particle was born, for per-particle variation that does not flicker. */
    var particleRandom: Float = 0f

    /** The index of the particle in its emitter, which is also its birth order. */
    var particleIndex: Float = 0f

    var emitterAge: Float = 0f
    var emitterCount: Float = 0f
    var effectTime: Float = 0f
    var partialTick: Float = 0f
    var gameTime: Float = 0f

    /** The numeric leaves of the handle data store by path, refilled when the store changes. */
    var data: Map<String, Float> = emptyMap()

    /** Values an expression may write and read back later; they live as long as the effect. */
    val variables = HashMap<String, Float>()

    /** Scratch space, cleared between steps. */
    val temporaries = HashMap<String, Float>()
}

val VfxDeclarations: Declarations<VfxEvalContext> = Declarations {
    val particle = struct<VfxEvalContext>("particle") {
        float("age") { it.age }
        float("lifetime") { it.lifetime }
        float("progress", alias = "t") { it.progress }
        float("x") { it.positionX }
        float("y") { it.positionY }
        float("z") { it.positionZ }
        float("velocity_x", alias = "vx") { it.velocityX }
        float("velocity_y", alias = "vy") { it.velocityY }
        float("velocity_z", alias = "vz") { it.velocityZ }
        float("speed") { it.speed }
        float("random") { it.particleRandom }
        float("index") { it.particleIndex }
    }

    val emitter = struct<VfxEvalContext>("emitter") {
        float("age") { it.emitterAge }
        float("count") { it.emitterCount }
        float("time") { it.effectTime }
        float("partial_tick") { it.partialTick }
        float("game_time") { it.gameTime }
        float("random") { it.rng.nextFloat() }
    }

    val math = mathNamespace()

    val variables = dynamic(
        name = "variables",
        read = { owner, key -> (owner as VfxEvalContext).variables[key] ?: 0f },
        write = { owner, key, value ->
            (owner as VfxEvalContext).variables[key] = Coercions.toFloat(value)
        },
    )

    val temporaries = dynamic(
        name = "temporaries",
        read = { owner, key -> (owner as VfxEvalContext).temporaries[key] ?: 0f },
        write = { owner, key, value ->
            (owner as VfxEvalContext).temporaries[key] = Coercions.toFloat(value)
        },
    )

    val handleData = dynamic(
        name = "data",
        read = { owner, key -> (owner as VfxEvalContext).data[key] ?: 0f },
        nested = true,
    )

    property("particle", particle, alias = "p") { it }
    property("emitter", emitter, alias = "e") { it }
    property("math", math) { it }
    property("variable", variables, alias = "v") { it }
    property("temp", temporaries, alias = "t") { it }
    property("data", handleData, alias = "d") { it }
    receiver("math")
}

/**
 * The VFX dialect.
 *
 * An unknown name warns and reads as zero rather than failing the file: an effect that mentions a
 * data key the server has not written yet still has to play.
 */
val VfxExpressionLanguage: Expression<VfxEvalContext> = Expression {
    options { unresolvedReferences(References.warnWithDefault(0f)) }
    declarations(VfxDeclarations)
}

/**
 * Every expression of one `.vfx` file, compiled together.
 */
class VfxExpressions private constructor(private val unit: CompiledUnit<VfxEvalContext>?) {
    fun float(source: String, default: Float = 0f): (VfxEvalContext) -> Float {
        val unit = unit ?: return { default }
        val compiled = unit.float(source, default)
        return compiled::eval
    }

    companion object {
        val EMPTY = VfxExpressions(null)

        private val cache = HashMap<List<String>, VfxExpressions>()

        fun compile(sources: Collection<String>, name: String): VfxExpressions {
            val wanted = sources.filter { it.isNotBlank() }.distinct().sorted()
            if (wanted.isEmpty()) return EMPTY

            cache[wanted]?.let { return it }

            val unit = VfxExpressionLanguage.compile(wanted)
            unit.diagnostics.forEach { HollowEngine.LOGGER.warn("Effect {}: {}", name, it) }

            if (cache.size >= CACHE_LIMIT) cache.clear()
            return VfxExpressions(unit).also { cache[wanted] = it }
        }

        private const val CACHE_LIMIT = 64
    }
}
