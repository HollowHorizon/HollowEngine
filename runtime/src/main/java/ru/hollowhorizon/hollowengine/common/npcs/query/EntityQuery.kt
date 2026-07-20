package ru.hollowhorizon.hollowengine.common.npcs.query

import net.minecraft.world.entity.Entity

enum class EntityQuerySort {
    NONE,
    NEAREST,
    FARTHEST,
}

class EntityQuery<T : Entity> internal constructor(
    val type: Class<T>,
    val radius: Double,
    val aliveOnly: Boolean,
    val visibleOnly: Boolean,
    val limit: Int,
    val sort: EntityQuerySort,
    internal val predicate: (T) -> Boolean,
)

class EntityQueryBuilder<T : Entity>(private val type: Class<T>) {
    var radius: Double = 16.0
    var aliveOnly: Boolean = true
    var visibleOnly: Boolean = false
    var limit: Int = Int.MAX_VALUE
    var sort: EntityQuerySort = EntityQuerySort.NEAREST

    private val predicates = mutableListOf<(T) -> Boolean>()

    fun filter(predicate: (T) -> Boolean) {
        predicates += predicate
    }

    fun build(): EntityQuery<T> {
        require(radius >= 0.0) { "Entity query radius cannot be negative" }
        require(limit >= 0) { "Entity query limit cannot be negative" }
        return EntityQuery(type, radius, aliveOnly, visibleOnly, limit, sort) { entity ->
            predicates.all { it(entity) }
        }
    }
}

inline fun <reified T : Entity> entityQuery(
    block: EntityQueryBuilder<T>.() -> Unit = {},
): EntityQuery<T> = EntityQueryBuilder(T::class.java).apply(block).build()
