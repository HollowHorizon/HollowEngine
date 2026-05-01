package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.*

class KatariScriptSystem(
    private val server: MinecraftServer,
    private val scope: CoroutineScope,
    private val onDirty: () -> Unit,
) {
    init {
        HollowEditorAPI.generate()
    }
    private val records = linkedMapOf<String, KatariRunRecord>()
    private val programCache = linkedMapOf<ProgramKey, com.sunnychung.lib.multiplatform.kotlite.katari.KatariProgram>()

    fun run(path: String, sourcePlayer: ServerPlayer? = null): Result<String> = runCatching {
        val source = loadSource(path)
        val runId = UUID.randomUUID().toString()
        val (bindings, host) = createHollowKatariBindings(server, runId, sourcePlayer, ::markDirty)

        val program = programCache.getOrPut(ProgramKey(source.path, source.hash)) {
            KatariNarrativeProgram(source.path, source.text, bindings)
        }
        val initialState = KatariState(
            programVersion = program.version,
            tasks = listOf(TaskState(id = program.entryTaskId)),
            globals = bindings.globals,
        )
        val instance = KatariInstance(
            program = program,
            initialState = initialState,
            functionRegistry = bindings.functionRegistry,
            propertyRegistry = bindings.propertyRegistry,
            snapshotCodec = bindings.snapshotCodec,
            coroutineScope = scope,
        )
        val record = KatariRunRecord(
            id = runId,
            path = source.path,
            hash = source.hash,
            sourcePlayer = sourcePlayer?.uuid?.toString(),
            instance = instance,
            host = host,
            status = KatariRunStatus.RUNNING,
        )
        records[runId] = record
        instance.start()
        watch(record)
        markDirty()
        runId
    }

    fun stop(idOrPath: String): Int {
        if (idOrPath == "all") {
            val count = records.size
            records.values.forEach { it.instance?.cancel() }
            records.clear()
            markDirty()
            return count
        }
        val matched = records.values.filter { it.id == idOrPath || it.path == idOrPath }
        matched.forEach {
            it.instance?.cancel()
            records.remove(it.id)
        }
        if (matched.isNotEmpty()) markDirty()
        return matched.size
    }

    fun choose(runId: String, optionId: String): Boolean {
        return records[runId]?.host?.select(optionId) == true
    }

    fun submitChat(player: ServerPlayer, text: String): Boolean {
        return records.values
            .asSequence()
            .filter { it.sourcePlayer == null || it.sourcePlayer == player.uuid.toString() }
            .any { it.host?.submitInput(text) == true }
    }

    fun list(): List<KatariRunInfo> = records.values.map {
        KatariRunInfo(it.id, it.path, it.status, it.error)
    }

    fun serialize(tag: CompoundTag) {
        val entries = ListTag()
        records.values.forEach { record ->
            val entry = CompoundTag()
            entry.putString("id", record.id)
            entry.putString("path", record.path)
            entry.putString("hash", record.hash)
            entry.putString("status", record.status.name)
            record.sourcePlayer?.let { entry.putString("source_player", it) }
            record.error?.let { entry.putString("error", it) }
            val instance = record.instance
            if (instance != null) {
                val snapshot = runBlocking { instance.serializeState() }
                val format = NBTFormat(record.bindingsModule())
                entry.put("snapshot", format.serialize(KatariStateSnapshot.serializer(), snapshot))
            } else {
                record.snapshot?.let { entry.put("snapshot", it.copy()) }
            }
            entries.add(entry)
        }
        tag.put("katari", entries)
    }

    fun deserialize(tag: CompoundTag) {
        records.values.forEach { it.instance?.cancel() }
        records.clear()

        val entries = tag.getList("katari", 10)
        entries.filterIsInstance<CompoundTag>().forEach { entry ->
            val id = entry.getString("id").takeIf { it.isNotBlank() } ?: return@forEach
            records[id] = KatariRunRecord(
                id = id,
                path = entry.getString("path"),
                hash = entry.getString("hash"),
                sourcePlayer = entry.getString("source_player").takeIf { it.isNotBlank() },
                snapshot = entry.get("snapshot"),
                status = runCatching { KatariRunStatus.valueOf(entry.getString("status")) }.getOrDefault(KatariRunStatus.PAUSED),
                error = entry.getString("error").takeIf { it.isNotBlank() },
            )
        }
        restoreLoadedRecords()
    }

    fun availableScripts(): Collection<String> = getAvailableKatariScripts()

    private fun restoreLoadedRecords() {
        records.values.filter { it.snapshot != null }.forEach { record ->
            scope.launch {
                restoreRecord(record)
            }
        }
    }

    private suspend fun restoreRecord(record: KatariRunRecord) {
        val result = runCatching {
            val source = loadSource(record.path)
            if (source.hash != record.hash) {
                error("Script content changed since the saved Katari run")
            }
            val sourcePlayer = record.sourcePlayer?.let { uuid ->
                server.playerList.players.firstOrNull { it.uuid.toString() == uuid }
            }
            val (bindings, host) = createHollowKatariBindings(
                server,
                record.id,
                sourcePlayer,
                ::markDirty,
                record.sourcePlayer
            )
            val snapshotTag = record.snapshot ?: error("Saved Katari snapshot is missing")
            val format = NBTFormat(bindings.snapshotCodec.serializersModule())
            val snapshot = format.deserialize(KatariStateSnapshot.serializer(), snapshotTag)
            val state = bindings.snapshotCodec.restore(snapshot, KatariRestoreContext(server))
            val program = programCache.getOrPut(ProgramKey(source.path, source.hash)) {
                KatariNarrativeProgram(source.path, source.text, bindings, HollowEngineSources(source))
            }
            val instance = KatariInstance(
                program = program,
                initialState = state.copy(globals = bindings.globals + state.globals),
                functionRegistry = bindings.functionRegistry,
                propertyRegistry = bindings.propertyRegistry,
                snapshotCodec = bindings.snapshotCodec,
                coroutineScope = scope,
            )
            record.instance = instance
            record.host = host
            record.status = KatariRunStatus.RUNNING
            record.error = null
            record.snapshot = null
            instance.start()
            watch(record)
        }
        result.exceptionOrNull()?.let { error ->
            record.status = KatariRunStatus.PAUSED
            record.error = error.message ?: error::class.java.simpleName
            HollowEngine.LOGGER.error("Failed to restore Katari script {}", record.path, error)
        }
        markDirty()
    }

    private fun watch(record: KatariRunRecord) {
        val instance = record.instance ?: return
        scope.launch {
            instance.join()
            val state = instance.currentState()
            val failed = state.tasks.firstOrNull { it.status is TaskStatus.Failed }?.status as? TaskStatus.Failed
            if (failed != null) {
                record.status = KatariRunStatus.FAILED
                record.error = failed.message
                record.snapshot = null
                record.instance = null
                record.host = null
                HollowEngine.LOGGER.error("Katari script {} failed: {}", record.path, failed.message)
            } else {
                records.remove(record.id)
            }
            markDirty()
        }
    }

    private fun KatariRunRecord.bindingsModule() =
        createHollowKatariBindings(server, id, null, ::markDirty).first.snapshotCodec.serializersModule()

    private fun markDirty() = onDirty()
}

class HollowEngineSources(val source: CodeSource) : KatariSourceProvider {
    override fun readSource(request: KatariSourceRequest): KatariSource {
        val source = this@HollowEngineSources.source.path.fromReadablePath()
        val file = source.parentFile.resolve(request.path)
        if (!file.exists()) throw FileNotFoundException(file.toRelativeString(source))
        return KatariSource(filename = request.path, code = file.readText(), id = request.path)
    }
}

data class KatariRunInfo(
    val id: String,
    val path: String,
    val status: KatariRunStatus,
    val error: String?,
)

enum class KatariRunStatus {
    RUNNING,
    PAUSED,
    FAILED,
}

private data class KatariRunRecord(
    val id: String,
    val path: String,
    val hash: String,
    val sourcePlayer: String? = null,
    var snapshot: net.minecraft.nbt.Tag? = null,
    var instance: KatariInstance? = null,
    var host: HollowKatariHost? = null,
    var status: KatariRunStatus = KatariRunStatus.RUNNING,
    var error: String? = null,
)

private data class ProgramKey(val path: String, val hash: String)

data class CodeSource(val path: String, val text: String, val hash: String)

private fun loadSource(path: String): CodeSource {
    val file = path.fromReadablePath()
    require(file.exists() && file.isFile) { "Katari script not found: $path" }
    require(file.extension == "ktr") { "Katari script must use .ktr extension: $path" }
    val text = file.readText()
    return CodeSource(file.toReadablePath(), text, text.sha256())
}

fun getAvailableKatariScripts(): Collection<String> {
    val scriptsDir = DirectoryManager.HOLLOW_ENGINE.resolve("scripts").toFile()
    if (!scriptsDir.exists()) {
        scriptsDir.mkdirs()
        return emptyList()
    }
    return scriptsDir.walk()
        .filter(File::isFile)
        .filter { it.name.endsWith(".ktr") }
        .map { it.toReadablePath() }
        .toList()
}

private fun String.sha256(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
