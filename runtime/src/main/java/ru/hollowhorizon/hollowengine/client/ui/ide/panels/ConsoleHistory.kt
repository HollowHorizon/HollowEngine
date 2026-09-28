package ru.hollowhorizon.hollowengine.client.ui.ide.panels

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hollowengine.common.config.Config
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Where earlier inputs of one console mode live, oldest first. */
internal interface ConsoleHistoryStore {
    val entries: List<String>

    fun add(entry: String)
}

/**
 * The chat's own history, so a command sent from the console comes up on Up in the chat and the
 * other way round.
 */
internal object VanillaCommandHistory : ConsoleHistoryStore {
    override val entries: List<String>
        get() = Minecraft.getInstance().gui.chat.recentChat
            .filter { it.startsWith("/") }
            .map { it.removePrefix("/") }

    override fun add(entry: String) {
        Minecraft.getInstance().gui.chat.addRecentChat("/" + entry.removePrefix("/"))
    }
}

/** Kotlin snippets, kept in the config directory between sessions. */
internal object ConsoleSnippetHistory : ConsoleHistoryStore {
    private const val MaxEntries = 50

    private val LOGGER = LogManager.getLogger("HollowIde")
    private val serializer = ListSerializer(String.serializer())
    private val json = Json { prettyPrint = true }
    private val file get() = Config.CONFIG_DIR.resolve("hollowengine").resolve("console-history.json")

    private val loaded: ArrayList<String> by lazy { ArrayList(read()) }

    override val entries: List<String> get() = loaded

    override fun add(entry: String) {
        if (loaded.lastOrNull() == entry) return
        loaded += entry
        if (loaded.size > MaxEntries) loaded.removeAt(0)
        write()
    }

    private fun read(): List<String> {
        val path = file
        if (!path.exists()) return emptyList()
        return try {
            json.decodeFromString(serializer, path.readText()).takeLast(MaxEntries)
        } catch (e: Exception) {
            LOGGER.warn("Could not read the console history", e)
            emptyList()
        }
    }

    private fun write() {
        try {
            val path = file
            path.createParentDirectories()
            path.writeText(json.encodeToString(serializer, loaded))
        } catch (e: Exception) {
            LOGGER.warn("Could not write the console history", e)
        }
    }
}

/**
 * Up/Down through a [ConsoleHistoryStore], like the chat does it: the text typed before stepping
 * back is kept and comes back when stepping past the newest entry.
 */
internal class ConsoleHistory(private val store: ConsoleHistoryStore) {
    private var position = -1
    private var draft = ""

    /** The input one step [older] or newer than [current], or null when there is nowhere to go. */
    fun step(current: String, older: Boolean): String? {
        val entries = store.entries
        val target = if (older) position + 1 else position - 1
        if (target < -1 || target > entries.lastIndex) return null
        if (position == -1) draft = current
        position = target
        return if (target == -1) draft else entries[entries.lastIndex - target]
    }

    fun add(entry: String) {
        store.add(entry)
        reset()
    }

    /** Back to the text being typed, for when the input changes under the history. */
    fun reset() {
        position = -1
        draft = ""
    }
}
