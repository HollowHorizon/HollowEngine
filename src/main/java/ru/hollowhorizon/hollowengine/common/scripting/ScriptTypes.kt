package ru.hollowhorizon.hollowengine.common.scripting

import ru.hollowhorizon.hollowengine.common.events.AnnotationProcessorEvent
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import kotlin.script.experimental.annotations.KotlinScript

object ScriptTypes {
    val SCRIPTS = HashMap<KotlinScript, Class<*>>()

    private val blacklist = listOf(
        "kotlin.script.experimental.host.DummyScriptTemplate" // Эта дичь использует обобщённый тип `.kts` из-за чего буквально подходит под любой паттерн, ломая анализатор
    )

    @SubscribeEvent
    fun loadTypes(event: AnnotationProcessorEvent) {
        event.registerClassHandler<KotlinScript> { clazz, annotation ->
            if(clazz.name in blacklist) return@registerClassHandler
            SCRIPTS[annotation] = clazz
        }
    }
}