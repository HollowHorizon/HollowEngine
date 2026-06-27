package ru.hollowhorizon.hollowengine.common.scripting.state

import kotlinx.coroutines.currentCoroutineContext
import net.minecraft.nbt.CompoundTag

internal suspend fun stateContext(): StateContext {
    return currentCoroutineContext()[StateContext.Key] ?: error("StateContext not found!")
}

internal suspend fun transition(target: String) {
    stateContext().nextState = target
}

suspend fun stateTag(): CompoundTag = stateContext().tag