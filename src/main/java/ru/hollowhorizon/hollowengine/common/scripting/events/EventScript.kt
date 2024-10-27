package ru.hollowhorizon.hollowengine.common.scripting.events


import com.google.common.collect.HashMultimap
import kotlinx.coroutines.runBlocking
import ru.hollowhorizon.hc.common.events.*

import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryEvent
import sun.misc.Unsafe
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.valueOrThrow

@KotlinScript(
    "EventScript", "event.kts", compilationConfiguration = HollowScriptConfiguration::class
)
abstract class EventScript

fun loadEvents() {
//    DirectoryManager.eventScripts.forEach { file ->
//        runBlocking {
//            val jar = ScriptingCompiler.compileFile<EventScript>(file)
//
//            val result = jar.execute()
//            val script = result.valueOrThrow().returnValue.scriptInstance ?: error("Script instance is null")
//
//            script.subscribeEvents()
//        }
//    }

    runBlocking {
        val jar = ScriptingCompiler.compileText<StoryEvent>("""
            val npc = npc(pos = pos(95, 69, -70))

            npc.moveTo(npc)
            npc say "Привет!"
            wait(2.sec)
            npc say "Как дела?"
        """.trimIndent())

        val result = jar.execute()
        val script = result.valueOrThrow().returnValue.scriptInstance as? StoryEvent ?: error("Script instance is null")
    }
}

private val EVENTS = HashMultimap.create<Any, EventListener<Event>>()

val implLookup by lazy {
    val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
    theUnsafe.isAccessible = true
    val unsafe = theUnsafe[null] as Unsafe
    val lookupClass = Class.forName("java.lang.invoke.MethodHandles\$Lookup", true, Thread.currentThread().contextClassLoader)
    val field = lookupClass.getDeclaredField("IMPL_LOOKUP")
    val base = unsafe.staticFieldBase(field)
    val offset = unsafe.staticFieldOffset(field)
    unsafe.getObject(base, offset) as MethodHandles.Lookup
}

fun Any.subscribeEvents() {
    val loader = Thread.currentThread().contextClassLoader
    Thread.currentThread().contextClassLoader = this.javaClass.classLoader
    val lookup = MethodHandles.privateLookupIn(this.javaClass, implLookup)
    val listeners = this.javaClass.declaredMethods
        .filter { method -> method.isAnnotationPresent(SubscribeEvent::class.java) }
        .map { method ->
            val listener = lookup.createEventListener(method, this)
            EventBus.registerNoInline(method.parameterTypes[0] as Class<Event>, listener)
            listener
        }
    Thread.currentThread().contextClassLoader = loader
    EVENTS.putAll(this, listeners)
}

fun Any.unsubscribeEvents() {
    EVENTS[this].forEach { listener -> EventBus.unregister(listener) }
    EVENTS.removeAll(this)
}
private fun Method.isStatic() = Modifier.isStatic(this.modifiers)