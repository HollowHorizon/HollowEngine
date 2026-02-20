package ru.hollowhorizon.hollowengine.common.scripting

import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import java.io.File
import java.net.URLClassLoader

class CompilerLoader(
    private val compilerJar: File,
    private val implementationClassName: String = "ru.hollowhorizon.hollowengine.common.ScriptingEnvironmentInitializerImpl"
) : AutoCloseable {
    var isLoaded = false

    private var classLoader: URLClassLoader? = null

    fun hasCompilerJar() = compilerJar.exists()

    fun initialize(javaHome: File, classpath: List<File>, mappings: Mappings) {
        if (!hasCompilerJar()) return
        val jarUrl = compilerJar.toURI().toURL()

        val parentLoader = ScriptingEnvironmentInitializer::class.java.classLoader
        classLoader = URLClassLoader(arrayOf(jarUrl), parentLoader)
        val currentThread = Thread.currentThread()
        val oldContextLoader = currentThread.contextClassLoader

        try {
            currentThread.contextClassLoader = classLoader
            val implClass = Class.forName(implementationClassName, true, classLoader)
            val initializer = implClass.getDeclaredConstructor().newInstance() as ScriptingEnvironmentInitializer
            initializer.initialize(javaHome, classpath, listOf(
                // Standard Kotlin scripts
                ScriptClassProvider(".kts", "kotlin.Any"),
                // Animation controller scripts - extends AnimationController base class
                ScriptClassProvider(
                    extension = ".animation-controller.kts",
                    baseClass = "ru.hollowhorizon.hollowengine.client.models.internal.controller.AnimationController",
                    defaultImports = listOf(
                        "net.minecraft.world.entity.LivingEntity",
                        "ru.hollowhorizon.hollowengine.client.models.internal.controller.AnimationController",
                        "ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode",
                        "ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment"
                    )
                ),
            ), mappings)

            isLoaded = true
            println("HollowEngine Compiler loaded successfully from ${compilerJar.name}")

        } catch (e: Exception) {
            throw RuntimeException("Failed to load compiler from $compilerJar", e)
        } finally {
            currentThread.contextClassLoader = oldContextLoader
        }
    }

    override fun close() {
        try {
            ScriptingEnvironment.INSTANCE.close()
            classLoader?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}