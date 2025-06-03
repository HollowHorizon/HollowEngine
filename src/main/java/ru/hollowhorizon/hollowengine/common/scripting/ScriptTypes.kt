package ru.hollowhorizon.hollowengine.common.scripting

import ru.hollowhorizon.hc.common.events.AnnotationProcessorEvent
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import kotlin.script.experimental.annotations.KotlinScript

object ScriptTypes {
    val SCRIPTS = HashMap<KotlinScript, Class<*>>()

    @SubscribeEvent
    fun loadTypes(event: AnnotationProcessorEvent) {
        event.registerClassHandler<KotlinScript> { clazz, annotation ->
            SCRIPTS[annotation] = clazz
        }
    }
}