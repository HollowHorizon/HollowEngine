package ru.hollowhorizon.hollowengine.common.scripting.mod

import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.kotlinscript.common.scripting.ScriptingCompiler
import ru.hollowhorizon.kotlinscript.common.scripting.errors
import ru.hollowhorizon.kotlinscript.common.scripting.kotlin.AbstractHollowScriptConfiguration
import sun.misc.Unsafe
import thedarkcolour.kotlinforforge.forge.FORGE_BUS
import java.io.File
import java.lang.invoke.LambdaMetafactory
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodHandles.Lookup
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.function.Consumer
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies
import kotlin.script.experimental.jvm.util.isError


@KotlinScript(
    displayName = "Mod Script",
    fileExtension = "mod.kts",
    compilationConfiguration = ModScriptConfiguration::class
)
abstract class ModScript : ModScriptBase()

fun runModScript(script: File) {
    HollowCore.LOGGER.info("[ModScriptCompiler]: loading script \"${script.name}\"")

    val result = ScriptingCompiler.compileFile<ModScript>(script)

    result.errors?.let { errors ->
        errors.forEach { error ->
            HollowCore.LOGGER.error("[ModScriptCompiler]: $error")
        }
        return
    }

    HollowCore.LOGGER.info("[ModScriptCompiler]: Script compiled: \"${result}\"")

    val res = result.execute {
        jvm {
            loadDependencies(false)
        }
    }

    HollowCore.LOGGER.info("[ModScriptCompiler]: Script evaluated: \"${res}\"")

    if (res.isError()) {
        (res as ResultWithDiagnostics.Failure).errors().let { errors ->
            errors.forEach { error ->
                HollowCore.LOGGER.error("[ModScriptCompiler]: $error")
            }
            return
        }
    } else {
        val modScript = res.valueOrThrow().returnValue.scriptInstance as? ModScriptBase ?: return

        if(HollowEngine.isLoading) modScript.init()

        reloadModEvents(script.toReadablePath(), modScript)
    }
}

val implLookup by lazy {
    val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
    theUnsafe.isAccessible = true
    val unsafe = theUnsafe[null] as Unsafe
    val lookupClass = Class.forName("java.lang.invoke.MethodHandles\$Lookup", true, Thread.currentThread().contextClassLoader)
    val field = lookupClass.getDeclaredField("IMPL_LOOKUP")
    val base = unsafe.staticFieldBase(field)
    val offset = unsafe.staticFieldOffset(field)
    unsafe.getObject(base, offset) as Lookup
}

fun reloadModEvents(path: String, script: ModScriptBase) {
    val loader = Thread.currentThread().contextClassLoader
    Thread.currentThread().contextClassLoader = script.javaClass.classLoader

    val events = script.javaClass.declaredMethods.filter {
        it.parameterCount == 1 && it.returnType == Void.TYPE && Event::class.java.isAssignableFrom(it.parameterTypes[0]) && it.declaredAnnotations.any { annotation -> annotation is SubscribeEvent }
    }

    val lookup = MethodHandles.privateLookupIn(script.javaClass, implLookup)

    val eventListeners = events.map { method ->
        lookup.createInvokerFunction(method, script)
    }

    Thread.currentThread().contextClassLoader = loader

    MOD_EVENTS[path]?.forEach(FORGE_BUS::unregister)

    eventListeners.forEach(FORGE_BUS::addListener)

    MOD_EVENTS[path] = eventListeners
}

val MOD_EVENTS = HashMap<String, List<Consumer<Event>>>()

fun Lookup.createInvokerFunction(method: Method, target: Any): Consumer<Event> {
    // Use the 'LambdaMetafactory' to generate a consumer which can be passed directly to an 'IEventBus'
    // when registering a listener, this reduces the overhead involved when reflectively invoking methods.
    try {
        val methodHandle = unreflect(method)
        val callSite = LambdaMetafactory.metafactory(
            this,
            "accept",
            MethodType.methodType(Consumer::class.java, target.javaClass),
            MethodType.methodType(Void.TYPE, Any::class.java),
            methodHandle,
            MethodType.methodType(Void.TYPE, method.parameterTypes[0])
        )

        return callSite.target.bindTo(target).invokeWithArguments() as Consumer<Event>
    } catch (t: Throwable) {
        throw IllegalStateException("Error whilst registering $method", t)
    }
}

fun main() {
    runModScript(File("run/hollowengine/scripts/hollow_engine_test.mod.kts"))
}

class ModScriptConfiguration : AbstractHollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowengine.common.scripting.story.waitForgeEvent",
        "ru.hollowhorizon.hollowengine.common.scripting.story.onForgeEvent",
        "net.minecraftforge.eventbus.api.SubscribeEvent",
        "ru.hollowhorizon.hollowengine.common.registry.ModStructures",
        "thedarkcolour.kotlinforforge.forge.MOD_BUS",
        "ru.hollowhorizon.hc.client.utils.*"
    )

    baseClass(ModScriptBase::class)
})