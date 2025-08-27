package ru.hollowhorizon.hc.common.events.capabilities

import ru.hollowhorizon.hc.api.ICapabilityDispatcher
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.events.Event

class LoadCapabilitiesEvent(
    val provider: ICapabilityDispatcher,
    private val capabilities: MutableMap<String, CapabilityInstance>,
) : Event {
    fun addCapability(capability: CapabilityInstance) {
        capabilities[capability.javaClass.name] = capability
        capability.provider = provider
    }
}