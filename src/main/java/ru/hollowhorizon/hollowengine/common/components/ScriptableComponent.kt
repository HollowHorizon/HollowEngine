package ru.hollowhorizon.hollowengine.common.components

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.components.events.on
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.components.registry.create
import ru.hollowhorizon.hollowengine.common.scripting.components.ScriptComponentsReloadedEvent

class ScriptableComponent<T : Any>(
    owner: T,
    val type: ResourceLocation,
    var component: Component<T>,
) : Component<T>(owner) {

    init {
        on<ScriptComponentsReloadedEvent>().listen {
            component.onDetach()
            component = ComponentRegistry[type].create(owner) as Component<T>
            component.onAttach()
        }
    }

    override fun onAttach() {
        super.onAttach()
        component.onAttach()
    }

    override fun onDetach() {
        super.onDetach()
        component.onDetach()
    }

    override fun onDisabled() {
        super.onDisabled()
        component.onDisabled()
    }

    override fun onEnabled() {
        super.onEnabled()
        component.onEnabled()
    }

    override fun onTick() {
        super.onTick()
        component.onTick()
    }
}