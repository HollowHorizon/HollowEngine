package ru.hollowhorizon.hollowengine.common.geary.tracking

import com.mineinabyss.geary.datatypes.GearyEntity
import ru.hollowhorizon.hollowengine.common.geary.withGeary

fun MCEntity.toGeary(): GearyEntity = withGeary {
    return toGearyOrNull() ?: error("Entity ${this@toGeary} is not being tracked by Geary!")
}

fun MCEntity.toGearyOrNull(): GearyEntity? =
    withGeary { getAddon(EntityTracking).mc2Geary[this@toGearyOrNull] }

fun GearyEntity.toMinecraft(): MCEntity? =
    with(world) { get(getAddon(EntityTracking).mcEntityComponent) as? MCEntity }