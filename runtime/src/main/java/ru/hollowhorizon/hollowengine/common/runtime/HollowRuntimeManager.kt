package ru.hollowhorizon.hollowengine.common.runtime

import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.utils.isProduction
import java.io.File

object HollowRuntimeManager : AutoCloseable {
    private const val ENTRYPOINT_CLASS_NAME = "ru.hollowhorizon.hollowengine.runtime.bootstrap.HollowEngineRuntimeBootstrap"

    private val parentFirstPackages = setOf(
        "java.",
        "javax.",
        "jdk.",
        "sun.",
        "com.sun.",
        "net.minecraft.",
        "net.minecraftforge.",
        "net.neoforged.",
        "cpw.mods.",
        "org.spongepowered.",
        "ru.hollowhorizon.hollowengine.api.",
        "ru.hollowhorizon.hollowengine.common.runtime.",
    )

    private var classLoader: ChildFirstUrlClassLoader? = null
    private var entrypoint: HollowRuntimeEntrypoint? = null

    val isLoaded: Boolean
        get() = entrypoint != null

    val annotationIndex: RuntimeAnnotationIndex
        get() = entrypoint?.annotationIndex ?: RuntimeAnnotationEnvironment.annotationIndex

    fun initialize() {
        if (entrypoint != null) return

        val runtimeJar = EmbeddedRuntimeJar.extract(HollowEngine::class.java, DirectoryManager.HOLLOW_ENGINE.toFile())
            ?: return

        val loader = ChildFirstUrlClassLoader(
            urls = arrayOf(runtimeJar.toURI().toURL()),
            parent = HollowRuntimeEntrypoint::class.java.classLoader,
            parentFirstPackages = parentFirstPackages,
        )

        val currentThread = Thread.currentThread()
        val oldContextLoader = currentThread.contextClassLoader

        try {
            currentThread.contextClassLoader = loader
            val bootstrapClass = Class.forName(ENTRYPOINT_CLASS_NAME, true, loader)
            val bootstrap = bootstrapClass.getDeclaredConstructor().newInstance() as HollowRuntimeEntrypoint
            bootstrap.initialize(
                HollowRuntimeBootstrapContext(
                    modId = HollowEngine.MODID,
                    gameDirectory = File("").absoluteFile,
                    cacheDirectory = DirectoryManager.HOLLOW_ENGINE.resolve(".cache").toFile(),
                    isProduction = isProduction,
                    logger = HollowEngine.LOGGER,
                )
            )

            classLoader = loader
            entrypoint = bootstrap
            HollowEngine.LOGGER.info("Loaded isolated HollowEngine runtime from {}", runtimeJar.name)
        } catch (error: Throwable) {
            loader.close()
            throw RuntimeException("Failed to initialize isolated HollowEngine runtime from $runtimeJar", error)
        } finally {
            currentThread.contextClassLoader = oldContextLoader
        }
    }

    fun loadClass(className: String): Class<*>? {
        return classLoader?.let { loader ->
            runCatching { Class.forName(className, false, loader) }.getOrNull()
        }
    }

    override fun close() {
        runCatching { entrypoint?.close() }
        runCatching { classLoader?.close() }
        entrypoint = null
        classLoader = null
    }
}
