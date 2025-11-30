package ru.hollowhorizon.hollowengine.common.components

import net.minecraft.nbt.CompoundTag

class ScriptableComponent<T : Any>(
    owner: T,
    var component: Component<T>,
) : Component<T>(owner) {
    override fun onAttach() {
        component.onAttach()
    }

    override fun onDetach() {
        component.onDetach()
    }

    override fun onDisabled() {
        component.onDisabled()
    }

    override fun onEnabled() {
        component.onEnabled()
    }

    override fun onTick() {
        component.onTick()
    }

    override fun loadExtras(tag: CompoundTag) {
        component.loadExtras(tag)
    }

    override fun saveExtras(tag: CompoundTag) {
        component.saveExtras(tag)
    }
}