package ru.hollowhorizon.hollowengine.common.fleks

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType


interface SyncedComponent<T> : Component<T> {
    fun shouldSync(): Boolean = true

    companion object: ComponentType<SyncedComponent<*>>()
}