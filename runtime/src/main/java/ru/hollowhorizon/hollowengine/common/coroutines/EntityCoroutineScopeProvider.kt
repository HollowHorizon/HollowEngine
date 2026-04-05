package ru.hollowhorizon.hollowengine.common.coroutines

import net.minecraft.world.entity.Entity

interface EntityCoroutineScopeProvider {
    val `hollowengine$coroutineScope`: SerializableCoroutineScope
}

val Entity.coroutineScope: SerializableCoroutineScope
    get() = ru.hollowhorizon.hollowengine.common.geary.api.GearyRuntimeState.coroutineScope(this)
