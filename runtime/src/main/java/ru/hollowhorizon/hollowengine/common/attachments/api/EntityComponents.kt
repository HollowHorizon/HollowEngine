package ru.hollowhorizon.hollowengine.common.attachments.api

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.attachments.binding.NodeRuntimeState
import ru.hollowhorizon.hollowengine.common.attachments.binding.withOrReplace
import ru.hollowhorizon.hollowengine.common.attachments.snapshot.EntitySnapshot

infix fun Entity.set(component: Component) {
    val service = NodeRuntimeState.service(level())
    val snapshot = (AttachmentRegistry.entitySnapshot(level(), uuid) ?: EntitySnapshot().withEntity(this))
        .withOrReplace(component)
    service.materialize(snapshot)
}