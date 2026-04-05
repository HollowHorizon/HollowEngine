package ru.hollowhorizon.hollowengine.common.geary.sync

import com.mineinabyss.geary.datatypes.ComponentId
import com.mineinabyss.geary.datatypes.Entity

interface ContextWithSource {
    val entity: Entity
    val source: ComponentId
}

interface ContextWithDataAndSource<R> : ContextWithSource {
    val event: R
}