package ru.hollowhorizon.hollowengine.client.models.internal.controller

import net.minecraft.world.entity.LivingEntity

class AnimationController(val entity: LivingEntity, val system: AnimationSystem) {
    var currentState: State
    val idle: State
    val walk: State
    val run: State
    val lay: State

    init {
        idle = State("idle") {
            if(entity.isMoving) {
                system.transition(from="idle", to="walk")
                currentState = walk
            } else if (entity.isShiftKeyDown) {
                system.transition(from="idle", to="lay")
                currentState = lay
            }
        }
        walk = State("walk") {
            if(entity.isSprinting) {
                system.transition(from="walk", to="run")
                currentState = run
            } else if (!entity.isMoving) {
                system.transition(from="walk",to="idle")
                currentState = idle
            }
        }
        run = State("run") {
            if(!entity.isMoving) {
                system.transition(from="run",to="idle")
                currentState = idle
            } else if(!entity.isSprinting) {
                system.transition(from="run",to="walk")
                currentState = walk
            }
        }
        lay = State("lay") {
            if(!entity.isShiftKeyDown) {
                system.transition(from="lay", to="idle")
                currentState = idle
            }
        }

        currentState = idle

        system.onUpdate {
            currentState.onUpdate()
        }
    }

    data class State(val animation: String, val onUpdate: suspend () -> Unit = {})

}