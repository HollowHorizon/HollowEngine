package ru.hollowhorizon.hollowengine.common.events

import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hollowengine.common.structure.PoolBuilder
import ru.hollowhorizon.hollowengine.common.structure.StructureBuilder

class StructureCreateEvent: Event {
    fun builder(id: String): StructureBuilder = builder(id, "minecraft:jigsaw")
    @get:JvmName("pool") val pool: PoolBuilder = pool("minecraft:empty")

    fun builder(id: String, type: String): StructureBuilder = StructureBuilder(type, id.rl)
    fun pool(fallback: String): PoolBuilder = PoolBuilder(fallback)
}
