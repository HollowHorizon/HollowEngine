package ru.hollowhorizon.hollowengine.common.geary

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.common.utils.ModList
import kotlin.reflect.KClass

data class FeatureContext(
    val server: MinecraftServer,
    val logger: Logger,
    val isFirstEnable: Boolean,
)

typealias FeatureBuilder = (FeatureContext) -> Feature

abstract class Feature(context: FeatureContext) {
    open val name = this::class.simpleName
    val server = context.server
    open val logger = context.logger

    val tasks = mutableListOf<Job>()

    private var modDeps = listOf<String>()

    open val subFeatures = Features(server, logger)

    fun modDeps(vararg modIds: String) {
        modDeps = modIds.toList()
    }

    open fun canLoad(): Boolean = true
    open fun canEnable(): Boolean = true
    open fun load() {}
    open fun enable() {}
    open fun disable() {}

    fun defaultDisable() {
        logger.i { "Disabling ${this::class.simpleName}" }
        disable()
        subFeatures.disableAll()

        // Отмена корутин
        tasks.forEach { it.cancel() }
        tasks.clear()
    }

    fun defaultLoad(isFirstLoad: Boolean) = runCatching {
        subFeatures.loadAll(isFirstLoad)
        if (isFirstLoad) load()
    }.onSuccess {
        if (isFirstLoad) logger.i("Loaded ${this::class.simpleName}")
    }.onFailure {
        logger.e("Failed to load ${this::class.simpleName}")
    }

    fun defaultEnable() = runCatching {
        subFeatures.enableAll()
        enable()
    }.onSuccess {
        logger.i("Enabled ${this::class.simpleName}")
    }.onFailure {
        logger.e("Failed to enable ${this::class.simpleName}")
    }

    fun defaultCanEnable(): Boolean {
        val unmet = modDeps.filter { !ModList.isLoaded(it) }
        if (unmet.isNotEmpty()) {
            logger.w { "Mod enable dependencies not met for $name: $unmet" }
            return false
        }
        return canEnable()
    }

    fun task(job: Job) {
        tasks.add(job)
    }

    fun subFeatures(vararg features: FeatureBuilder) = Features(server, logger, *features)
}

class Features(
    val server: MinecraftServer,
    val logger: Logger,
    vararg val features: FeatureBuilder,
) {
    val featuresByClass = mutableMapOf<KClass<*>, FeatureBuilder>()
    val loaded = mutableListOf<Feature>()
    val enabled = mutableListOf<Feature>()
    private var isFirstEnable = true

    val context get() = FeatureContext(server, logger, isFirstEnable)

    fun loadAll(isFirstLoad: Boolean = true) {
        features.forEach { load(it, isFirstLoad) }
    }

    fun enableAll() {
        loaded.forEach { enable(it) }
        isFirstEnable = false
    }

    fun load(builder: FeatureBuilder, isFirstLoad: Boolean): Result<Feature> = runCatching {
        val feature = builder(context)
        featuresByClass[feature::class] = builder
        if (!feature.canLoad()) return Result.failure(IllegalStateException("Feature ${feature.name} could not be loaded"))
        return feature.defaultLoad(isFirstLoad)
            .onSuccess { loaded.add(feature) }
            .onFailure { it.printStackTrace() }
            .map { feature }
    }.onFailure {
        context.logger.e { "Failed to create feature from constructor: $builder" }
        it.printStackTrace()
    }

    fun enable(feature: Feature): Result<Feature> {
        if (!feature.defaultCanEnable()) return Result.failure(IllegalStateException("Feature ${feature::class.simpleName} could not be enabled"))
        enabled.add(feature)
        return feature.defaultEnable()
            .onFailure {
                enabled.remove(feature)
                it.printStackTrace()
            }
            .map { feature }
    }

    fun disableAll() {
        enabled.forEach(Feature::defaultDisable)
        enabled.clear()
        loaded.clear()
    }

    fun reloadAll() {
        disableAll()
        loadAll(isFirstLoad = false)
        enableAll()
    }

    inline fun <reified T : Feature> getOrNull() = enabled.firstOrNull { it is T } as? T

    inline fun <reified T : Feature> get() = getOrNull<T>() ?: error("Feature ${T::class.simpleName} is not enabled!")

    inline fun <reified T : Feature> reload(notify: CommandSender? = null) {
        val builder = featuresByClass[T::class] ?: error("Feature ${T::class.simpleName} has never been loaded!")
        val feature = getOrNull<T>()!!
        feature.defaultDisable()
        enabled.remove(feature)
        load(builder, isFirstLoad = false)
            .map { enable(it) }
            .onSuccess { notify?.success("Reloaded ${T::class.simpleName}") }
            .onFailure { notify?.error("Failed to reload ${T::class.simpleName}\n${it.stackTraceToString()}") }

    }
}

interface CommandSender {
    fun success(message: String)
    fun error(message: String)
}