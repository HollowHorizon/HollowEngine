package ru.hollowhorizon.hollowengine.common.geary.api

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.geary.binding.NodeRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.binding.withOrReplace
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot

infix fun Entity.set(component: Component) {
    val service = NodeRuntimeState.service(level())
    val snapshot = (GearyRuntimeState.entitySnapshot(level(), uuid) ?: EntitySnapshot().withEntity(this))
        .withOrReplace(component)
    service.materialize(snapshot)
}