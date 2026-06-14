package ru.hollowhorizon.hollowengine.common.addons

interface HollowAddonEntrypoint : AutoCloseable {
    fun initialize(context: HollowAddonContext)

    override fun close() = Unit
}
