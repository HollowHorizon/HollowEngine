package ru.hollowhorizon.hollowengine.common.components.property

fun interface Save {
    fun shouldSave(property: Property<*>): Boolean

    object ALWAYS : Save {
        override fun shouldSave(property: Property<*>): Boolean = true
    }

    object NEVER : Save {
        override fun shouldSave(property: Property<*>): Boolean = false
    }
}