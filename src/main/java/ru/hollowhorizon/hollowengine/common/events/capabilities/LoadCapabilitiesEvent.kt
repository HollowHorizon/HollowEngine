package ru.hollowhorizon.hollowengine.common.events.capabilities

import ru.hollowhorizon.hollowengine.api.ICapabilityDispatcher
import ru.hollowhorizon.hollowengine.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hollowengine.common.events.Event

class LoadCapabilitiesEvent(
    val provider: ICapabilityDispatcher,
    private val capabilities: MutableMap<String, CapabilityInstance>,
) : Event {
    fun addCapability(capability: CapabilityInstance) {
        capabilities[capability.javaClass.name] = capability
        capability.provider = provider
    }
}