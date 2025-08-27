package ru.hollowhorizon.hollowengine.common.registry

import ru.hollowhorizon.hollowengine.api.ICapabilityDispatcher
import ru.hollowhorizon.hollowengine.common.capabilities.CAPABILITIES
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.capabilities.LoadCapabilitiesEvent

object ModCapabilities {
    @SubscribeEvent
    fun loadCapabilities(event: LoadCapabilitiesEvent) {
        CAPABILITIES
            .filter { it.key.isValid(event.provider) }
            .forEach {
                it.value.forEach {
                    event.addCapability(it())
                }
            }
    }

    private fun Class<*>.isValid(dispatcher: ICapabilityDispatcher) = isInstance(dispatcher)
}