package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.world

import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode

fun IContextBuilder.pauseTime() {
    stateMachine.startTasks += {
        if(stateMachine.extra.getBoolean("is_time_paused")) {
            stateMachine.server.allLevels.forEach {
                it.tickTime = false
            }
        }
    }

    +SimpleNode {
        stateMachine.extra.putBoolean("is_time_paused", true)
        stateMachine.server.allLevels.forEach {
            it.tickTime = false
        }
    }
}

fun IContextBuilder.resumeTime() {
    +SimpleNode {
        stateMachine.extra.putBoolean("is_time_paused", false)
        stateMachine.server.allLevels.forEach {
            it.tickTime = true
        }
    }
}