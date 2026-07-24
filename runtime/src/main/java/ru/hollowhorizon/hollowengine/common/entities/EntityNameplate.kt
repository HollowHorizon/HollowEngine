package ru.hollowhorizon.hollowengine.common.entities

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.geary.api.set
import ru.hollowhorizon.hollowengine.common.geary.binding.EntitySnapshotPacket
import ru.hollowhorizon.hollowengine.common.geary.components.NameplateComponent
import ru.hollowhorizon.hollowengine.common.geary.components.NameplateMode
import ru.hollowhorizon.hollowengine.common.geary.components.nameplateComponent
import ru.hollowhorizon.hollowengine.common.geary.snapshot.snapshotOf
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import ru.hollowhorizon.hollowengine.common.utils.literal

fun Entity.setNameplate(text: String, mode: NameplateMode = NameplateMode.SHOW) {
    customName = text.takeIf(String::isNotBlank)?.literal
    nameplateMode = mode
}

var Entity.nameplateMode: NameplateMode
    get() = nameplateComponent?.mode ?: if (isCustomNameVisible) NameplateMode.SHOW else NameplateMode.HIDDEN
    set(value) {
        isCustomNameVisible = value == NameplateMode.SHOW && customName != null
        set(NameplateComponent(value))
        syncNameplate()
    }

private fun Entity.syncNameplate() {
    val serverEntity = takeIf { !level().isClientSide } ?: return
    EntitySnapshotPacket(serverEntity.id, snapshotOf(serverEntity)).sendTrackingEntityAndSelf(serverEntity)
}