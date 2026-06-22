package ru.hollowhorizon.hollowengine.common.scripting.story

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import java.io.File
import java.util.UUID

class StoryScriptSystem(
    private val server: MinecraftServer,
) {
    private val runs = linkedMapOf<String, StoryScriptRun>()

    fun run(path: String, player: ServerPlayer): Result<String> = runCatching {
        val file = path.fromReadablePath()
        require(file.isFile) { "Story script not found: $path" }
        require(file.name.endsWith(StoryScriptExtension)) {
            "Story script must use $StoryScriptExtension extension: $path"
        }

        val scripting = ScriptingEnvironment.currentOrNull()
            ?: error("Kotlin scripting compiler addon is not installed")
        val id = UUID.randomUUID().toString()
        val job = SupervisorJob(server.coroutineScope.coroutineContext[Job])
        val scope = CoroutineScope(server.coroutineScope.coroutineContext + job)
        val context = StoryScriptContext(server, player, file.toReadablePath(), scope)
        runs[id] = StoryScriptRun(id, context.path, scope)
        job.invokeOnCompletion { runs.remove(id) }

        scripting.compiler.compile(file).getOrThrow().execute<Any>(context).getOrThrow()
        id
    }.onFailure { error ->
        runs.values.lastOrNull()?.takeIf { it.path == path }?.let { failed ->
            failed.scope.cancel("Story script failed", error)
            runs.remove(failed.id)
        }
    }

    fun stop(idOrPath: String): Int {
        val matches = if (idOrPath == "all") runs.values.toList() else runs.values.filter {
            it.id == idOrPath || it.path == idOrPath
        }
        matches.forEach { run ->
            run.scope.cancel("Story script stopped")
            runs.remove(run.id)
        }
        return matches.size
    }

    fun list(): List<StoryScriptRunInfo> = runs.values.map { StoryScriptRunInfo(it.id, it.path) }

    fun dispose() = stop("all")
}

data class StoryScriptRunInfo(
    val id: String,
    val path: String,
)

private data class StoryScriptRun(
    val id: String,
    val path: String,
    val scope: CoroutineScope,
)

const val StoryScriptExtension = ".story.kts"

fun getAvailableStoryScripts(): List<String> {
    val scriptsDirectory = DirectoryManager.HOLLOW_ENGINE.resolve("scripts").toFile()
    if (!scriptsDirectory.exists()) return emptyList()
    return scriptsDirectory.walk()
        .filter(File::isFile)
        .filter { it.name.endsWith(StoryScriptExtension) }
        .map { it.toReadablePath() }
        .toList()
}
