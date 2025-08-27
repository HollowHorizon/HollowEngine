package ru.hollowhorizon.hollowengine.common.events.registry

import net.minecraft.client.KeyMapping
import ru.hollowhorizon.hollowengine.common.events.Event
import java.util.function.Consumer

class RegisterKeyBindingsEvent(private val consumer: Consumer<KeyMapping>) : Event {
    fun registerKeyMapping(mapping: KeyMapping) {
        consumer.accept(mapping)
    }
}