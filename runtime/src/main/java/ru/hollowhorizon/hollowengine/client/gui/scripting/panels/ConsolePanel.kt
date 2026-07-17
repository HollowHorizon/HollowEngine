package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import androidx.compose.runtime.*
import com.mojang.brigadier.suggestion.Suggestion
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.spi.StandardLevel
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.console.ConsoleSuggestionProvider
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiCheckboxVariant
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdown
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownItem
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.logging.HollowLogEntry
import ru.hollowhorizon.hollowengine.common.logging.HollowLogSnapshot
import ru.hollowhorizon.hollowengine.common.logging.HollowLogStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun HollowIdeConsolePanel() {
    var minimumLevel by remember { mutableStateOf(StandardLevel.DEBUG) }
    var filterText by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<Suggestion>()) }
    var levelMenuExpanded by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    var snapshot by remember { mutableStateOf(HollowLogStore.snapshot()) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            HollowLogStore.snapshotAfter(snapshot.revision)?.let { snapshot = it }
        }
    }

    val filter = remember(filterText) { ConsoleLogFilter.compile(filterText) }
    val visibleEntries = remember(snapshot, minimumLevel, filter) {
        snapshot.entries.filter { entry -> entry.level.ordinal <= minimumLevel.ordinal && filter.matches(entry) }
    }
    LaunchedEffect(visibleEntries.lastOrNull()?.id, autoScroll) {
        if (autoScroll) {
            repeat(AutoScrollLayoutFrames) {
                withFrameNanos { }
                listState.scroll.scrollTo(y = Float.MAX_VALUE)
            }
        }
    }

    fun updateCommand(value: String) {
        command = value
        suggestions = if (value.isBlank()) {
            emptyList()
        } else {
            runCatching { ConsoleSuggestionProvider.suggest(value, value.length) }.getOrDefault(emptyList())
        }
    }

    fun executeCommand() {
        val value = command.trim()
        if (value.isEmpty()) return
        val connection = Minecraft.getInstance().connection
        if (connection == null) {
            HollowCore.LOGGER.warn("Cannot execute console command without a server connection")
        } else {
            connection.sendCommand(value.trimStart('/'))
            HollowCore.LOGGER.info("Executed command: {}", value)
        }
        updateCommand("")
    }

    Column(
        tags = listOf("ide-panel", "console-panel"),
        modifier = Modifier.size(100.percent, 100.percent),
    ) {
        Row(tags = listOf("console-toolbar")) {
            Text("hollowengine.gui.console.level".lang, tags = listOf("console-toolbar-label"))
            UiDropdown(
                id = "console-level",
                label = minimumLevel.name,
                expanded = levelMenuExpanded,
                onExpandedChange = { levelMenuExpanded = it },
                items = ConsoleLevels.map { level ->
                    UiDropdownItem(level.name) {
                        minimumLevel = level
                        levelMenuExpanded = false
                    }
                },
                tags = listOf("console-level-dropdown"),
            )
            Text("hollowengine.gui.console.filter".lang, tags = listOf("console-toolbar-label"))
            TextField(
                value = filterText,
                placeholder = "hollowengine.gui.console.filter_hint".lang,
                onChange = { filterText = it },
                tags = listOf("console-filter"),
                modifier = Modifier.size(0.px, 20.px).grow(1f),
            )
            Text("hollowengine.gui.console.auto_scroll".lang, tags = listOf("console-toolbar-label"))
            Checkbox(
                checked = autoScroll,
                variant = UiCheckboxVariant.SWITCH,
                onCheckedChange = { autoScroll = it },
                tags = listOf("console-auto-scroll"),
            )
        }

        if (visibleEntries.isEmpty()) {
            Box(tags = listOf("console-empty"), modifier = Modifier.size(100.percent, 0.px).grow(1f)) {
                Text("hollowengine.gui.console.empty".lang)
            }
        } else {
            LazyColumn(
                state = listState,
                tags = listOf("console-log-list"),
                crossAxisScroll = true,
                modifier = Modifier.size(100.percent, 0.px).grow(1f)
                    .onScroll { event ->
                        if (event.rawScrollY > 0f) autoScroll = false
                    },
            ) {
                items(visibleEntries, key = HollowLogEntry::id) { entry -> ConsoleLogRow(entry) }
            }
        }

        if (suggestions.isNotEmpty()) {
            Column(tags = listOf("console-suggestions")) {
                suggestions.take(MaxVisibleSuggestions).forEach { suggestion ->
                    val label = suggestion.text + (suggestion.tooltip?.string?.let { " ($it)" } ?: "")
                    Text(
                        label,
                        tags = listOf("console-suggestion"),
                        modifier = Modifier.cursor(UiCursorShape.HAND).onClick { event ->
                            updateCommand(suggestion.apply(command))
                            event.consume()
                        },
                    )
                }
            }
        }

        Row(tags = listOf("console-command-row")) {
            TextField(
                value = command,
                placeholder = "hollowengine.gui.console.command_hint".lang,
                onChange = ::updateCommand,
                id = "console-command",
                tags = listOf("console-command"),
                modifier = Modifier.size(0.px, 22.px).grow(1f).onKeyInput { input ->
                    if (input.key == GLFW.GLFW_KEY_ENTER && !input.repeat) {
                        executeCommand()
                        input.consume()
                    }
                },
            )
            ConsoleButton("hollowengine.gui.console.execute".lang, ::executeCommand)
        }
    }
}

@Composable
private fun ConsoleLogRow(entry: HollowLogEntry) {
    val levelTag = "level-${entry.level.name.lowercase()}"
    Row(
        tags = listOf("console-log-row", levelTag),
        modifier = Modifier.size(UiLength.Fit, UiLength.Fit),
    ) {
        Text(LogTimeFormatter.format(Instant.ofEpochMilli(entry.timeMillis)), tags = listOf("console-log-time"))
        Text(entry.level.name.padEnd(LevelLabelWidth), tags = listOf("console-log-level"))
        entry.loggerName?.let { logger ->
            Text(logger.substringAfterLast('.'), tags = listOf("console-log-logger"))
        }
        Text(entry.displayMessage, tags = listOf("console-log-message"))
    }
}

@Composable
private fun ConsoleButton(label: String, onClick: () -> Unit) {
    Box(
        tags = listOf("console-button"),
        modifier = Modifier.cursor(UiCursorShape.HAND).onClick { event ->
            if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) onClick()
            event.consume()
        },
    ) {
        Text(label)
    }
}

private class ConsoleLogFilter private constructor(private val regex: Regex?) {
    fun matches(entry: HollowLogEntry): Boolean {
        val matcher = regex ?: return true
        return matcher.containsMatchIn(entry.message) ||
                entry.loggerName?.let(matcher::containsMatchIn) == true
    }

    companion object {
        fun compile(value: String): ConsoleLogFilter {
            val query = value.trim()
            if (query.isEmpty()) return ConsoleLogFilter(null)
            val regex = runCatching { Regex(query, RegexOption.IGNORE_CASE) }
                .getOrElse { Regex(Regex.escape(query), RegexOption.IGNORE_CASE) }
            return ConsoleLogFilter(regex)
        }
    }
}

private val ConsoleLevels = listOf(
    StandardLevel.FATAL,
    StandardLevel.ERROR,
    StandardLevel.WARN,
    StandardLevel.INFO,
    StandardLevel.DEBUG,
    StandardLevel.TRACE,
)
private val LogTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    .withZone(ZoneId.systemDefault())
private const val MaxVisibleSuggestions = 10
private const val LevelLabelWidth = 5
private const val AutoScrollLayoutFrames = 2
