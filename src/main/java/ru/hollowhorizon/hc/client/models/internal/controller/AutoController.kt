package ru.hollowhorizon.hc.client.models.internal.controller

import ru.hollowhorizon.hc.client.models.internal.animations.AnimationType

object AutoController {
    fun create(builder: StateMachineBuilder, animations: Map<AnimationType, String>) = builder.apply {
        val states = mutableSetOf<String>()

        fun has(type: AnimationType) = animations[type]?.isNotBlank() == true
        fun clipName(type: AnimationType) = animations[type] ?: ""

        // === STATE DEFINITIONS ===
        if (has(AnimationType.IDLE)) {
            state("Idle") {
                clip(clipName(AnimationType.IDLE), wrap = WrapMode.Loop)
            }
            states += "Idle"

            initialState("Idle")
        }

        if (has(AnimationType.WALK) || has(AnimationType.RUN)) {
            state("Move") {
                blendTree {
                    factor("q.ground_speed", 0.75f)
                    if (has(AnimationType.WALK))
                        clip(clipName(AnimationType.WALK), 2.35f, speed = "min(q.ground_speed / 1.5, 4f)")
                    if (has(AnimationType.RUN))
                        clip(clipName(AnimationType.RUN), 3f, speed = "min(q.ground_speed / 2, 3f)")
                }
            }
            states += "Move"
        }

        if (has(AnimationType.WALK_SNEAKED)) {
            state("CrouchWalking") {
                clip(
                    clipName(AnimationType.WALK_SNEAKED),
                    wrap = WrapMode.Loop,
                    speed = "min(q.ground_speed, 3f) * 2"
                )
            }
            states += "CrouchWalking"
        }

        if (has(AnimationType.IDLE_SNEAKED)) {
            state("Sneaking") {
                clip(clipName(AnimationType.IDLE_SNEAKED), wrap = WrapMode.Loop,)
            }
            states += "Sneaking"
        }

        if (has(AnimationType.JUMP)) {
            state("Jump") {
                clip(clipName(AnimationType.JUMP), wrap = WrapMode.ClampForever)
            }
            states += "Jump"
        }

        if (has(AnimationType.FALL)) {
            state("Fall") {
                clip(clipName(AnimationType.FALL), wrap = WrapMode.Loop)
            }
            states += "Fall"
        }

        if (has(AnimationType.FLY)) {
            state("Fly") {
                clip(clipName(AnimationType.FLY), wrap = WrapMode.Loop)
            }
            states += "Fly"
        }

        if (has(AnimationType.SWIM)) {
            state("Swim") {
                clip(clipName(AnimationType.SWIM), wrap = WrapMode.Loop, speed = "q.ground_speed")
            }
            states += "Swim"
        }

        if (has(AnimationType.SIT)) {
            state("Sit") {
                clip(clipName(AnimationType.SIT), wrap = WrapMode.Loop)
            }
            states += "Sit"
        }

        if (has(AnimationType.SLEEP)) {
            state("Sleep") {
                clip(clipName(AnimationType.SLEEP), wrap = WrapMode.Loop)
            }
            states += "Sleep"
        }

        if (has(AnimationType.HURT)) {
            state("Hurt") {
                clip(clipName(AnimationType.HURT), wrap = WrapMode.Once)
            }
            states += "Hurt"
        }

        if (has(AnimationType.SWING)) {
            state("Swing") {
                clip(clipName(AnimationType.SWING), wrap = WrapMode.Once)
            }
            states += "Swing"
        }

        if (has(AnimationType.DEATH)) {
            state("Death") {
                clip(clipName(AnimationType.DEATH), wrap = WrapMode.ClampForever)
            }
            states += "Death"
        }

        // === TRANSITIONS ===
        fun safeTransition(from: String, to: String, cond: String, duration: Float = 0.25f) {
            if (from in states && to in states) {
                transition(from, to) {
                    condition(cond)
                    this.duration(duration)
                }
            }
        }

        fun wildcardTransition(to: String, cond: String, duration: Float = 0.25f) {
            if (to in states) {
                transition("*", to) {
                    condition(cond)
                    this.duration(duration)
                }
            }
        }

        // Movement transitions
        safeTransition("Idle", "Sneaking", "q.is_sneaking && !q.is_moving")
        safeTransition("Sneaking", "Idle", "!q.is_sneaking && !q.is_moving")

        safeTransition("Idle", "Move", "q.is_moving")
        safeTransition("Move", "Idle", "!q.is_moving")

        safeTransition("Move", "CrouchWalking", "q.is_sneaking")
        safeTransition("CrouchWalking", "Move", "!q.is_sneaking")

        safeTransition("Sneaking", "CrouchWalking", "q.is_moving")
        safeTransition("CrouchWalking", "Sneaking", "!q.is_moving")

        // Jumping & falling
        wildcardTransition("Jump", "q.is_jumping", 0.1f)
        safeTransition("Jump", "Fall", "q.velocity_y < -0.1")
        safeTransition("Jump", "Idle", "q.is_on_ground")
        safeTransition("Jump", "Move", "q.is_on_ground && q.is_moving")
        safeTransition("Fall", "Idle", "q.is_on_ground")

        // Fly
        wildcardTransition("Fly", "q.is_flying", 0.15f)
        safeTransition("Fly", "Idle", "!q.is_flying")

        // Fall from high
        wildcardTransition("Fall", "q.fall_ticks > 4", 0.15f)

        // Swim
        wildcardTransition("Swim", "q.is_swimming")
        safeTransition("Swim", "Idle", "!q.is_swimming")

        // Sit
        wildcardTransition("Sit", "q.is_sitting")
        safeTransition("Sit", "Idle", "!q.is_sitting")

        // Sleep
        wildcardTransition("Sleep", "q.is_sleeping")
        safeTransition("Sleep", "Idle", "!q.is_sleeping")

        // Hurt
        wildcardTransition("Hurt", "q.is_hurt")
        safeTransition("Hurt", "Idle", "!q.is_hurt")

        // Swing
        wildcardTransition("Swing", "q.is_swinging")
        safeTransition("Swing", "Idle", "!q.is_swinging")

        // Death
        wildcardTransition("Death", "!q.is_alive")
    }

}