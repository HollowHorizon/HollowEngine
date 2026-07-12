package ru.hollowhorizon.hollowengine.common.addons

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.bootstrap.runtime.AddonBootstrapContract
import ru.hollowhorizon.hollowengine.network.HollowAddonPacketRegistry
import java.io.File

internal class HollowAddonRuntime(
    private val addonsDirectory: File,
    cacheDirectory: File,
) {
    private val mutex = Mutex()
    private val runtimeJob = SupervisorJob()
    private val runtimeScope = CoroutineScope(Dispatchers.IO + runtimeJob + CoroutineName("HollowEngine addons"))
    private val artifactStore = HollowAddonArtifactStore(cacheDirectory)
    private val activationStore = HollowAddonActivationStore(addonsDirectory.resolve(".disabled-addons"))
    private val loadedAddons = LinkedHashMap<String, LoadedHollowAddon>()
    private val pendingAddons = LinkedHashMap<String, HollowAddonCandidate>()
    private val restartRequiredAddons = LinkedHashMap<String, HollowAddonDescriptor>()
    private val knownCandidates = LinkedHashMap<String, HollowAddonCandidate>()
    private val disabledAddonIds = LinkedHashSet<String>()
    private var watcherJob: Job? = null

    val services = HollowAddonServiceRegistry()

    @Volatile
    var loadedSnapshot: List<HollowAddonDescriptor> = emptyList()
        private set

    @Volatile
    var restartRequiredSnapshot: List<HollowAddonDescriptor> = emptyList()
        private set

    suspend fun start() {
        withContext(Dispatchers.IO) {
            addonsDirectory.mkdirs()
            disabledAddonIds += activationStore.load()
        }
        val candidates = withContext(Dispatchers.IO) {
            addonsDirectory.listFiles { file ->
                file.isFile && file.extension.equals("jar", ignoreCase = true)
            }.orEmpty().sortedBy(File::getName).mapNotNull { file ->
                runCatching { artifactStore.stage(file) }
                    .onFailure { HollowEngine.LOGGER.error("Skipping addon jar '{}'", file.name, it) }
                    .getOrNull()
            }
        }

        locked {
            candidates.forEach { candidate -> knownCandidates[candidate.sourceFile.canonicalPath] = candidate }
            candidates.groupBy { it.descriptor.id }.forEach { (id, versions) ->
                if (versions.size == 1) {
                    queue(versions.single())
                } else {
                    HollowEngine.LOGGER.error(
                        "Skipping addon '{}': multiple artifacts found ({})",
                        id,
                        versions.joinToString { it.sourceFile.name },
                    )
                }
            }
            drainPending()
            reportPending()
        }
        watcherJob = HollowAddonWatcher(addonsDirectory, artifactStore, this, runtimeScope).start(candidates)
    }

    suspend fun addCandidate(candidate: HollowAddonCandidate): Boolean = locked {
        knownCandidates[candidate.sourceFile.canonicalPath] = candidate
        val id = candidate.descriptor.id
        val existing = loadedAddons[id]?.candidate ?: pendingAddons[id]
        if (existing != null && existing.sourceFile != candidate.sourceFile) {
            HollowEngine.LOGGER.error(
                "Ignoring duplicate addon '{}' from '{}'; '{}' is already selected",
                id,
                candidate.sourceFile.name,
                existing.sourceFile.name,
            )
            return@locked true
        }
        if (!queue(candidate)) return@locked true
        drainPending()
        id in loadedAddons || id in pendingAddons
    }

    suspend fun replaceCandidate(
        oldCandidate: HollowAddonCandidate,
        newCandidate: HollowAddonCandidate,
    ): Boolean = locked {
        knownCandidates.remove(oldCandidate.sourceFile.canonicalPath)
        knownCandidates[newCandidate.sourceFile.canonicalPath] = newCandidate
        val oldId = oldCandidate.descriptor.id
        val active = loadedAddons[oldId]?.takeIf { it.candidate.sourceFile == oldCandidate.sourceFile }
        val waiting = pendingAddons[oldId]?.takeIf { it.sourceFile == oldCandidate.sourceFile }
        if (active == null && waiting == null) return@locked addDetachedReplacement(newCandidate)

        if (active == null) {
            pendingAddons.remove(oldId)
            if (!queue(newCandidate)) {
                queue(oldCandidate)
                return@locked false
            }
            drainPending()
            if (newCandidate.descriptor.id in loadedAddons || newCandidate.descriptor.id in pendingAddons) {
                return@locked true
            }
            queue(oldCandidate)
            drainPending()
            return@locked false
        }

        val unloadOrder = collectUnloadOrder(oldId)
        val rollbackCandidates = unloadOrder.map(LoadedHollowAddon::candidate)
        unloadOrder.forEach { addon -> unloadSingle(addon) }
        pendingAddons.remove(oldId)
        rollbackCandidates.dropLast(1).forEach(::queue)
        queue(newCandidate)
        drainPending()

        if (newCandidate.descriptor.id in loadedAddons) return@locked true

        HollowEngine.LOGGER.error("Reload of addon '{}' failed; restoring the previous version", oldId)
        pendingAddons.remove(newCandidate.descriptor.id)
        rollbackCandidates.asReversed().forEach(::queue)
        drainPending()
        false
    }

    suspend fun removeCandidate(candidate: HollowAddonCandidate) = locked {
        val id = candidate.descriptor.id
        knownCandidates.remove(candidate.sourceFile.canonicalPath)
        restartRequiredAddons.remove(id)
        restartRequiredSnapshot = restartRequiredAddons.values.toList()
        pendingAddons[id]?.takeIf { it.sourceFile == candidate.sourceFile }?.let { pendingAddons.remove(id) }
        val active = loadedAddons[id]?.takeIf { it.candidate.sourceFile == candidate.sourceFile } ?: return@locked
        val unloadOrder = collectUnloadOrder(id)
        unloadOrder.dropLast(1).map(LoadedHollowAddon::candidate).forEach(::queue)
        unloadOrder.forEach { addon -> unloadSingle(addon) }
        drainPending()
    }

    suspend fun statuses(): List<HollowAddonStatus> = locked {
        knownCandidates.values
            .sortedWith(compareBy({ it.descriptor.id }, { it.sourceFile.name }))
            .map(::statusOf)
    }

    suspend fun setEnabled(id: String, enabled: Boolean): HollowAddonOperationResult = locked {
        val candidates = knownCandidates.values.filter { candidate -> candidate.descriptor.id == id }
        if (candidates.isEmpty()) return@locked HollowAddonOperationResult(false, "Addon '$id' is not installed.")
        if (candidates.size > 1) {
            return@locked HollowAddonOperationResult(false, "Addon '$id' has multiple JAR files; remove duplicates first.")
        }
        val candidate = candidates.single()

        if (enabled) {
            disabledAddonIds.remove(id)
            withContext(Dispatchers.IO) { activationStore.save(disabledAddonIds) }
            if (id !in loadedAddons && id !in pendingAddons) {
                queue(candidate)
                drainPending()
            }
            val status = statusOf(candidate)
            return@locked HollowAddonOperationResult(
                status.state == HollowAddonState.LOADED || status.state == HollowAddonState.WAITING_FOR_DEPENDENCIES,
                "Addon '$id' state: ${status.state.name.lowercase()}." +
                    status.details?.let { details -> " $details" }.orEmpty(),
            )
        }

        disabledAddonIds += id
        withContext(Dispatchers.IO) { activationStore.save(disabledAddonIds) }
        pendingAddons.remove(id)
        restartRequiredAddons.remove(id)
        restartRequiredSnapshot = restartRequiredAddons.values.toList()
        val active = loadedAddons[id]
        if (active != null) {
            val unloadOrder = collectUnloadOrder(id)
            val dependantCandidates = unloadOrder.dropLast(1).map(LoadedHollowAddon::candidate).asReversed()
            unloadOrder.forEach { addon -> unloadSingle(addon) }
            dependantCandidates.forEach(::queue)
            drainPending()
        }
        HollowAddonOperationResult(true, "Addon '$id' is disabled.")
    }

    suspend fun reload(id: String): HollowAddonOperationResult = locked {
        val candidates = knownCandidates.values.filter { candidate -> candidate.descriptor.id == id }
        if (candidates.isEmpty()) return@locked HollowAddonOperationResult(false, "Addon '$id' is not installed.")
        if (candidates.size > 1) {
            return@locked HollowAddonOperationResult(false, "Addon '$id' has multiple JAR files; remove duplicates first.")
        }
        if (id in disabledAddonIds) {
            return@locked HollowAddonOperationResult(false, "Addon '$id' is disabled; enable it first.")
        }

        val currentCandidate = candidates.single()
        val refreshedCandidate = withContext(Dispatchers.IO) { artifactStore.stage(currentCandidate.sourceFile) }
        knownCandidates[refreshedCandidate.sourceFile.canonicalPath] = refreshedCandidate
        val active = loadedAddons[id]
        if (active == null) {
            pendingAddons.remove(id)
            queue(refreshedCandidate)
            drainPending()
            val status = statusOf(refreshedCandidate)
            return@locked HollowAddonOperationResult(
                status.state == HollowAddonState.LOADED,
                "Addon '$id' state after reload: ${status.state.name.lowercase()}.",
            )
        }

        val unloadOrder = collectUnloadOrder(id)
        val rollbackCandidates = unloadOrder.map(LoadedHollowAddon::candidate)
        val dependantCandidates = rollbackCandidates.dropLast(1).asReversed()
        unloadOrder.forEach { addon -> unloadSingle(addon) }
        queue(refreshedCandidate)
        dependantCandidates.forEach(::queue)
        drainPending()
        if (id in loadedAddons) {
            return@locked HollowAddonOperationResult(true, "Addon '$id' was reloaded.")
        }

        pendingAddons.remove(id)
        rollbackCandidates.asReversed().forEach(::queue)
        drainPending()
        HollowAddonOperationResult(false, "Reload of '$id' failed; the previous version was restored.")
    }

    suspend fun close() {
        watcherJob?.cancelAndJoin()
        watcherJob = null
        locked {
            pendingAddons.clear()
            loadedAddons.keys.toList().forEach { id ->
                loadedAddons[id]?.let { collectUnloadOrder(id).forEach { addon -> unloadSingle(addon) } }
            }
        }
        runtimeJob.cancelAndJoin()
    }

    private suspend fun addDetachedReplacement(candidate: HollowAddonCandidate): Boolean {
        val existing = loadedAddons[candidate.descriptor.id]?.candidate ?: pendingAddons[candidate.descriptor.id]
        if (existing != null && existing.sourceFile != candidate.sourceFile) {
            HollowEngine.LOGGER.error("Ignoring duplicate addon '{}' from '{}'", candidate.descriptor.id, candidate.sourceFile.name)
            return true
        }
        if (!queue(candidate)) return true
        drainPending()
        return candidate.descriptor.id in loadedAddons || candidate.descriptor.id in pendingAddons
    }

    private fun queue(candidate: HollowAddonCandidate): Boolean {
        val descriptor = candidate.descriptor
        restartRequiredAddons.remove(descriptor.id)
        restartRequiredSnapshot = restartRequiredAddons.values.toList()
        if (!descriptor.environment.supports(HollowAddonRuntimeEnvironment.isClient)) {
            HollowEngine.LOGGER.info(
                "Skipping addon '{}' because it targets the {} environment",
                descriptor.id,
                descriptor.environment.name.lowercase(),
            )
            return false
        }
        if (descriptor.id in disabledAddonIds) {
            HollowEngine.LOGGER.info("Addon '{}' is disabled", descriptor.id)
            return false
        }
        if (wasBootstrapAddonRejected(candidate)) {
            HollowEngine.LOGGER.error(
                "Skipping addon '{}': its bundled libraries conflict with Minecraft or another addon. " +
                    "See the bootstrap validation error above.",
                descriptor.id,
            )
            return false
        }
        if (candidate.requiresBootstrapLibraries && !areBootstrapLibrariesLoaded(candidate)) {
            restartRequiredAddons[descriptor.id] = descriptor
            restartRequiredSnapshot = restartRequiredAddons.values.toList()
            HollowEngine.LOGGER.warn(
                "Addon '{}' contains bootstrap/native libraries and was added or updated after startup; " +
                    "it is disabled for this session.",
                descriptor.id,
            )
            HollowAddonNotifications.restartRequired(descriptor)
            return false
        }
        val runtimeNamespace = HollowAddonRuntimeEnvironment.mappingNamespace()
        if (
            descriptor.mappingNamespace != HollowAddonMappingNamespace.AGNOSTIC &&
            descriptor.mappingNamespace != runtimeNamespace
        ) {
            HollowEngine.LOGGER.error(
                "Skipping addon '{}': jar namespace is {}, runtime namespace is {}. Use the platform-specific addon jar.",
                descriptor.id,
                descriptor.mappingNamespace.id,
                runtimeNamespace.id,
            )
            return false
        }
        pendingAddons[descriptor.id] = candidate
        return true
    }

    private fun areBootstrapLibrariesLoaded(candidate: HollowAddonCandidate): Boolean = System
        .getProperty(AddonBootstrapContract.LOADED_ADDON_FINGERPRINTS_PROPERTY, "")
        .split(',')
        .any { fingerprint -> fingerprint == candidate.fingerprint }

    private fun wasBootstrapAddonRejected(candidate: HollowAddonCandidate): Boolean = System
        .getProperty(AddonBootstrapContract.REJECTED_ADDON_FINGERPRINTS_PROPERTY, "")
        .split(',')
        .any { fingerprint -> fingerprint == candidate.fingerprint }

    private fun statusOf(candidate: HollowAddonCandidate): HollowAddonStatus {
        val descriptor = loadedAddons[candidate.descriptor.id]
            ?.takeIf { loaded -> loaded.candidate.sourceFile == candidate.sourceFile }
            ?.candidate
            ?.descriptor
            ?: candidate.descriptor
        val missingDependencies = descriptor.dependencies.filterNot(loadedAddons::containsKey)
        val isLoadedCandidate = loadedAddons[descriptor.id]?.candidate?.sourceFile == candidate.sourceFile
        val isPendingCandidate = pendingAddons[descriptor.id]?.sourceFile == candidate.sourceFile
        val state = when {
            descriptor.id in disabledAddonIds -> HollowAddonState.DISABLED
            isLoadedCandidate -> HollowAddonState.LOADED
            descriptor.id in restartRequiredAddons -> HollowAddonState.RESTART_REQUIRED
            wasBootstrapAddonRejected(candidate) -> HollowAddonState.REJECTED
            isPendingCandidate -> HollowAddonState.WAITING_FOR_DEPENDENCIES
            else -> HollowAddonState.INACTIVE
        }
        val details = when (state) {
            HollowAddonState.WAITING_FOR_DEPENDENCIES -> "Missing dependencies: ${missingDependencies.joinToString()}."
            HollowAddonState.RESTART_REQUIRED -> "Restart Minecraft to load bootstrap/native libraries."
            HollowAddonState.REJECTED -> "Bundled libraries failed bootstrap safety validation."
            HollowAddonState.INACTIVE -> "Check environment, mapping namespace, required classes, and the log."
            else -> null
        }
        return HollowAddonStatus(descriptor, state, candidate.sourceFile.name, details)
    }

    private suspend fun drainPending() {
        while (true) {
            val ready = pendingAddons.values.firstOrNull { candidate ->
                candidate.descriptor.dependencies.all(loadedAddons::containsKey)
            } ?: return
            pendingAddons.remove(ready.descriptor.id)
            load(ready)
        }
    }

    private suspend fun load(candidate: HollowAddonCandidate): Boolean {
        val descriptor = candidate.descriptor
        val dependencyLoaders = descriptor.dependencies.map { dependencyId ->
            loadedAddons.getValue(dependencyId).classLoader
        }
        val libraries = withContext(Dispatchers.IO) { artifactStore.extractLibraries(candidate) }
        val urls = (listOf(candidate.classesFile, candidate.artifactFile) + libraries)
            .distinct()
            .map { it.toURI().toURL() }
            .toTypedArray()
        val classLoader = HollowAddonClassLoader(
            urls = urls,
            parent = HollowAddonEntrypoint::class.java.classLoader,
            dependencies = dependencyLoaders,
        )
        val hostServices = services.ownedBy(descriptor.id)
        var koinApplication: KoinApplication? = null
        var addonJob: Job? = null
        return runCatching {
            descriptor.requiredClasses.forEach { className -> Class.forName(className, false, classLoader) }
            val entrypoint = Class.forName(descriptor.entrypoint, true, classLoader)
                .asSubclass(HollowAddonEntrypoint::class.java)
                .getDeclaredConstructor()
                .newInstance()
            val bridgeModule = module {
                single<HollowAddonHostServices> { hostServices }
                single { descriptor }
            }
            val createdKoinApplication = koinApplication {
                modules(listOf(bridgeModule) + entrypoint.koinModules)
            }
            koinApplication = createdKoinApplication
            val context = HollowAddonContext(
                hostServices = hostServices,
                descriptor = descriptor,
                addonFile = candidate.artifactFile,
                classLoader = classLoader,
                koin = createdKoinApplication.koin,
            )
            val createdJob = SupervisorJob(runtimeJob)
            addonJob = createdJob
            val addonScope = CoroutineScope(
                Dispatchers.Default + createdJob + CoroutineName("Addon ${descriptor.id}") + ClassLoaderContextElement(classLoader),
            )
            withContext(Dispatchers.Default + ClassLoaderContextElement(classLoader)) {
                entrypoint.load(context, addonScope)
                HollowAddonEventRegistrar.register(candidate.classesFile, classLoader, descriptor.id, entrypoint, addonScope)
            }
            loadedAddons[descriptor.id] = LoadedHollowAddon(
                candidate = candidate,
                classLoader = classLoader,
                entrypoint = entrypoint,
                context = context,
                job = createdJob,
                koinApplication = createdKoinApplication,
                hostServices = hostServices,
            )
            refreshSnapshot()
            HollowEngine.LOGGER.info("Loaded addon {} {}", descriptor.id, descriptor.version)
            true
        }.onFailure { error ->
            HollowEngine.LOGGER.error("Failed to load addon '${descriptor.id}'", error)
            addonJob?.cancelAndJoin()
            HollowAddonPacketRegistry.unregister(descriptor.id)
            koinApplication?.close()
            hostServices.cleanup()
            classLoader.close()
        }.getOrDefault(false)
    }

    private suspend fun unloadSingle(addon: LoadedHollowAddon) {
        val id = addon.candidate.descriptor.id
        addon.job.cancelAndJoin()
        HollowAddonPacketRegistry.unregister(id)
        runCatching {
            withContext(Dispatchers.Default + ClassLoaderContextElement(addon.classLoader)) {
                addon.entrypoint.unload(addon.context)
            }
        }.onFailure { HollowEngine.LOGGER.error("Failed to run unload hook for addon '$id'", it) }
        runCatching { addon.koinApplication.close() }
            .onFailure { HollowEngine.LOGGER.error("Failed to close Koin for addon '$id'", it) }
        addon.hostServices.cleanup()
        runCatching { addon.classLoader.close() }
            .onFailure { HollowEngine.LOGGER.error("Failed to close classloader for addon '$id'", it) }
        loadedAddons.remove(id)
        refreshSnapshot()
        HollowEngine.LOGGER.info("Unloaded addon {}", id)
    }

    private fun collectUnloadOrder(rootId: String): List<LoadedHollowAddon> {
        val result = ArrayList<LoadedHollowAddon>()
        val visited = HashSet<String>()
        fun visit(id: String) {
            if (!visited.add(id)) return
            loadedAddons.values
                .filter { id in it.candidate.descriptor.dependencies }
                .forEach { dependant -> visit(dependant.candidate.descriptor.id) }
            loadedAddons[id]?.let(result::add)
        }
        visit(rootId)
        return result
    }

    private fun reportPending() {
        pendingAddons.values.forEach { candidate ->
            val missing = candidate.descriptor.dependencies.filterNot { dependency ->
                dependency in loadedAddons || dependency in pendingAddons
            }
            val reason = if (missing.isEmpty()) "a dependency cycle" else "missing dependencies: $missing"
            HollowEngine.LOGGER.error("Addon '{}' is not loadable because of {}", candidate.descriptor.id, reason)
        }
    }

    private fun refreshSnapshot() {
        loadedSnapshot = loadedAddons.values.map { it.candidate.descriptor }
    }

    private suspend fun <T> locked(block: suspend () -> T): T {
        mutex.lock()
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }

    private data class LoadedHollowAddon(
        val candidate: HollowAddonCandidate,
        val classLoader: HollowAddonClassLoader,
        val entrypoint: HollowAddonEntrypoint,
        val context: HollowAddonContext,
        val job: Job,
        val koinApplication: KoinApplication,
        val hostServices: OwnedHollowAddonHostServices,
    )
}
