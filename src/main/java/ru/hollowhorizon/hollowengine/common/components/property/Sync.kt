package ru.hollowhorizon.hollowengine.common.components.property

fun interface Sync {
    fun shouldSync(property: Property<*>): Boolean

    object NEVER : Sync {
        override fun shouldSync(property: Property<*>): Boolean = false
    }

    object ON_CHANGE : Sync {
        override fun shouldSync(property: Property<*>): Boolean = true
    }
}