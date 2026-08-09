package ru.hollowhorizon.hollowengine.common.dialogue

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.dialogue.lang.actor
import ru.hollowhorizon.hollowengine.common.dialogue.lang.list
import ru.hollowhorizon.hollowengine.common.dialogue.lang.number
import ru.hollowhorizon.hollowengine.common.dialogue.lang.string
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationPlayMode
import ru.hollowhorizon.hollowengine.common.npcs.navigation.MoveOptions
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.entities.play
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.entities.playAndWait
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.entities.stopAnimation
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.clearLookTarget
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.lookAt
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.move
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.stopMoving
import kotlin.time.Duration.Companion.milliseconds

/**
 * Story commands that drive the characters a dialogue was given, over the engine's own NPC API.
 */
internal object StoryNpcFunctions {
    private val OWNED = listOf(
        "walk-to", "stop-walking", "look-at", "stop-looking",
        "play-once", "play-looped", "play-clamped", "play-ping-pong", "stop-animation",
    )

    fun install(registry: StoryFunctionRegistry) {
        OWNED.forEach(registry::unregister)
        registry.movement()
        registry.looking()
        registry.animations()
    }

    private fun StoryFunctionRegistry.movement() {
        add(
            "walk-to",
            actor("who"), list("position"),
            number("speed", default = DEFAULT_SPEED), number("distance", default = DEFAULT_DISTANCE),
        ) { args ->
            args.npc("who").move(args.vec3("position"), args.moveOptions())
        }

        add(
            "walk-to",
            actor("who"), actor("target"),
            number("speed", default = DEFAULT_SPEED), number("distance", default = DEFAULT_DISTANCE),
        ) { args ->
            args.npc("who").move(args.entity("target"), args.moveOptions())
        }

        add("stop-walking", actor("who")) { args -> args.npc("who").stopMoving() }
    }

    private fun StoryFunctionRegistry.looking() {
        add("look-at", actor("who"), list("position"), number("time", default = DEFAULT_LOOK_MILLIS)) { args ->
            args.npc("who").lookAt(args.vec3("position"), args.lookDuration())
        }

        add("look-at", actor("who"), actor("target"), number("time", default = DEFAULT_LOOK_MILLIS)) { args ->
            args.npc("who").lookAt(args.entity("target"))
        }

        add("stop-looking", actor("who")) { args -> args.npc("who").clearLookTarget() }
    }

    private fun StoryFunctionRegistry.animations() {
        add("play-once", actor("who"), string("animation"), number("fade", default = DEFAULT_FADE_MILLIS.toFloat())) { args ->
            args.entity("who").playAndWait(args.string("animation"), args.fade(), args.fade())
        }

        addPlayMode("play-looped", AnimationPlayMode.Loop)
        addPlayMode("play-clamped", AnimationPlayMode.ClampForever)
        addPlayMode("play-ping-pong", AnimationPlayMode.PingPong)

        add("stop-animation", actor("who"), string("animation"), number("fade", default = DEFAULT_FADE_MILLIS.toFloat())) { args ->
            args.entity("who").stopAnimation(args.string("animation"), args.fade())
        }
    }

    private fun StoryFunctionRegistry.addPlayMode(name: String, mode: AnimationPlayMode) {
        add(name, actor("who"), string("animation"), number("fade", default = DEFAULT_FADE_MILLIS.toFloat())) { args ->
            args.entity("who").play(args.string("animation"), mode, args.fade(), args.fade())
        }
    }

    private fun StoryArguments.npc(name: String): NpcEntity {
        val entity: LivingEntity = entity(name)
        return entity as? NpcEntity
            ?: throw IllegalArgumentException(
                "'${actor(name).name}' is a ${entity.type.description.string}, not an engine NPC, so it cannot be told where to go",
            )
    }

    private fun StoryArguments.moveOptions() = MoveOptions(
        speed = number("speed").toDouble(),
        arrivalDistance = number("distance").toDouble(),
    )

    private fun StoryArguments.lookDuration() = millis("time").milliseconds

    private fun StoryArguments.fade(): Double = number("fade").toDouble() / 1000.0

    private const val DEFAULT_LOOK_MILLIS = 1_500f
    private const val DEFAULT_FADE_MILLIS = 330f

    /** Kept in step with [MoveOptions] so the editor shows what the NPC API would have used anyway. */
    private val DEFAULT_SPEED = MoveOptions().speed.toFloat()
    private val DEFAULT_DISTANCE = MoveOptions().arrivalDistance.toFloat()
}

private suspend fun NpcEntity.move(position: Vec3, options: MoveOptions) {
    move(pos = position, options = options)
}

private suspend fun NpcEntity.move(target: LivingEntity, options: MoveOptions) {
    move(entity = target, dist = options.arrivalDistance, speed = options.speed)
}
