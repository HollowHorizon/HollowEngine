package ru.hollowhorizon.hollowengine.common.scripting.events


import com.google.common.collect.HashMultimap
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.common.coroutines.scopeAsync
import ru.hollowhorizon.hc.common.events.*
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import sun.misc.Unsafe
import java.io.File
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
    DirectoryManager.eventScripts.map { file ->
        startEventScript(file)
    }.toList().let {
        runBlocking {
            it.awaitAll()
        }
    }
}

fun startEventScript(file: File) = scopeAsync {
    try {
        val jar = ScriptingCompiler.compileFile<EventScript>(file)

        val result = jar.execute()
        val script = result.valueOrThrow().returnValue.scriptInstance ?: error("Script instance is null")

        EVENT_SCRIPTS[file]?.unsubscribeEvents()
        script.subscribeEvents()
        EVENT_SCRIPTS[file] = script
    } catch (e: Exception) {
        HollowCore.LOGGER.warn(e)
    }
}

val EVENT_SCRIPTS = HashMap<File, Any>()

private val EVENTS = HashMultimap.create<Any, EventListener<Event>>()

val implLookup by lazy {
    val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
    theUnsafe.isAccessible = true
    val unsafe = theUnsafe[null] as Unsafe
    val lookupClass =
        Class.forName("java.lang.invoke.MethodHandles\$Lookup", true, Thread.currentThread().contextClassLoader)
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