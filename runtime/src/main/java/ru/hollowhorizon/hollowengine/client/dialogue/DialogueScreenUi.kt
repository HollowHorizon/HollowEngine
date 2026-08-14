package ru.hollowhorizon.hollowengine.client.dialogue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.Box
import ru.hollowhorizon.hollowengine.client.ui.Column
import ru.hollowhorizon.hollowengine.client.ui.Image
import ru.hollowhorizon.hollowengine.client.ui.Row
import ru.hollowhorizon.hollowengine.client.ui.Span
import ru.hollowhorizon.hollowengine.client.ui.Text
import ru.hollowhorizon.hollowengine.client.ui.UiBoxMode
import ru.hollowhorizon.hollowengine.client.ui.UiState
import ru.hollowhorizon.hollowengine.client.ui.focusScope
import ru.hollowhorizon.hollowengine.client.ui.input
import ru.hollowhorizon.hollowengine.client.ui.onClick
import ru.hollowhorizon.hollowengine.client.ui.onDrag
import ru.hollowhorizon.hollowengine.client.ui.onKeyInput
import ru.hollowhorizon.hollowengine.client.ui.onPlaced
import ru.hollowhorizon.hollowengine.client.ui.padding
import ru.hollowhorizon.hollowengine.client.ui.px
import ru.hollowhorizon.hollowengine.client.ui.scroll.rememberScrollState
import ru.hollowhorizon.hollowengine.client.ui.scrollable
import ru.hollowhorizon.hollowengine.client.ui.state
import ru.hollowhorizon.hollowengine.client.ui.style
import ru.hollowhorizon.hollowengine.common.dialogue.DialogueChoiceView
import ru.hollowhorizon.hollowengine.common.dialogue.DialogueChoicesView
import ru.hollowhorizon.hollowengine.common.dialogue.DialogueLineView
import ru.hollowhorizon.hollowengine.common.dialogue.DialoguePhase
import ru.hollowhorizon.hollowengine.common.dialogue.DialogueUiKeys
import ru.hollowhorizon.hollowengine.common.dialogue.UiDialoguePresentation
import ru.hollowhorizon.hollowengine.common.ui.UiGuiScale
import ru.hollowhorizon.hollowengine.common.ui.UiScope
import ru.hollowhorizon.hollowengine.common.ui.UiScreenDefinition
import ru.hollowhorizon.hollowengine.common.ui.send

/**
 * The dialogue window the engine ships with.
 */
object DialogueScreenUi {
    const val STYLESHEET = "hollowengine:ui/styles/dialogue.hss"
    const val EXIT_DURATION_MILLIS = 760L

    /** Registers the built-in screen. Scripts declaring the same id take over from it. */
    fun register(registry: (UiScreenDefinition) -> Unit) {
        registry(
            UiScreenDefinition(
                id = UiDialoguePresentation.DEFAULT_SCREEN,
                title = "Dialogue",
                closeOnEscape = false,
                pausesGame = false,
                rebuildEveryFrame = false,
                guiScale = UiGuiScale.Auto,
                exitDuration = EXIT_DURATION_MILLIS,
                content = { DialogueScreen() },
            ),
        )
    }
}

private const val CURSOR_TEXTURE = "hollowengine:textures/gui/dialogues/cursor.png"
private const val DESC_TEXTURE = "hollowengine:textures/gui/dialogues/desc.png"

/** `icon="none"` on a `@choice` drops the icon that otherwise sits left of the button. */
private const val NO_ICON = "none"

/** Beyond this the appearance stagger stops growing, or a long menu would crawl in. */
private const val MAX_STAGGERED_CHOICE = 7

/** `#dialogue-continue:ready`, the line is finished and waiting to be advanced. */
private val READY_STATE = UiState.of("ready")

private val ADVANCE_KEYS = intArrayOf(
    GLFW.GLFW_KEY_ENTER,
    GLFW.GLFW_KEY_KP_ENTER,
    GLFW.GLFW_KEY_SPACE,
    GLFW.GLFW_KEY_TAB,
)

@Composable
private fun UiScope.DialogueScreen() {
    val line = data[DialogueUiKeys.Line]
    val choices = data[DialogueUiKeys.Choices]
    val closing = data[DialogueUiKeys.Phase] == DialoguePhase.CLOSING

    var root = Modifier
        .style(DialogueScreenUi.STYLESHEET)
        .focusScope()
        .input(clickable = true)
        .onClick { advance() }
        .onKeyInput { input ->
            if (input.key in ADVANCE_KEYS) {
                input.consume()
                advance()
            }
        }
    if (closing) root = root.state(UiState.CLOSING)

    Box(id = "dialogue-root", mode = UiBoxMode.FREE, modifier = root) {
        Box(id = "dialogue-dim")
        if (choices.options.isNotEmpty()) ChoiceList(choices)
        if (line.text.isNotEmpty()) DialogueBox(line, showContinue = choices.options.isEmpty())
    }
}

@Composable
private fun DialogueBox(line: DialogueLineView, showContinue: Boolean) {
    Column(id = "dialogue-box") {
        if (line.speaker.isNotEmpty()) {
            Box(id = "dialogue-name") { Text(line.speaker, tags = listOf("dialogue-name-text")) }
        }
        Box(id = "dialogue-body") {
            TypedText(line)

            val ready = showContinue && line.awaiting
            Image(
                CURSOR_TEXTURE,
                id = "dialogue-continue",
                modifier = if (ready) Modifier.state(READY_STATE) else null,
            )
        }
    }
}

@Composable
private fun TypedText(line: DialogueLineView) {
    val reveal = remember(line.id) { mutableStateOf(0) }

    LaunchedEffect(line.id, line.text.length, line.charDelay) {
        if (line.charDelay <= 0) {
            reveal.value = line.text.length
            return@LaunchedEffect
        }
        val from = reveal.value
        var startNanos = -1L
        while (reveal.value < line.text.length) {
            withFrameNanos { now ->
                if (startNanos < 0L) startNanos = now
                val elapsedMillis = (now - startNanos) / 1_000_000L
                reveal.value = (from + elapsedMillis / line.charDelay).toInt().coerceAtMost(line.text.length)
            }
        }
    }

    LaunchedEffect(line.id, line.skips) {
        if (line.skips > 0) reveal.value = line.text.length
    }

    val shown = reveal.value.coerceIn(0, line.text.length)
    Text(tags = listOf("dialogue-text")) {
        Span(line.text.take(shown))
        if (shown < line.text.length) {
            Span(line.text.substring(shown), tags = listOf("dialogue-text-pending"))
        }
    }
}

@Composable
private fun UiScope.ChoiceList(choices: DialogueChoicesView) {
    val scroll = rememberScrollState()
    var viewportHeight by remember { mutableStateOf(0f) }
    var firstHeight by remember { mutableStateOf(0f) }
    var lastHeight by remember { mutableStateOf(0f) }
    val lastIndex = choices.options.lastIndex

    fun edge(optionHeight: Float) =
        if (viewportHeight <= 0f || optionHeight <= 0f) 0f else ((viewportHeight - optionHeight) / 2f).coerceAtLeast(0f)

    Box(
        id = "choice-viewport",
        modifier = Modifier
            .scrollable(horizontal = false, hasVerticalScrollbar = false, state = scroll)
            .input(draggable = true)
            .onDrag { event -> scroll.scrollBy(deltaY = -event.deltaY) }
            .onPlaced { rect -> viewportHeight = rect.height },
    ) {
        Column(
            id = "choice-list",
            modifier = Modifier.padding(0.px, edge(firstHeight).px, 0.px, edge(lastHeight).px),
        ) {
            choices.options.forEach { option ->
                Choice(
                    option = option,
                    choices = choices,
                    onMeasured = { height ->
                        if (option.index == 0) firstHeight = height
                        if (option.index == lastIndex) lastHeight = height
                    },
                )
            }
        }
    }
}

@Composable
private fun UiScope.Choice(option: DialogueChoiceView, choices: DialogueChoicesView, onMeasured: (Float) -> Unit) {
    val voted = choices.myVote >= 0
    val mine = choices.myVote == option.index
    val won = choices.decided == option.index
    val lost = choices.decided >= 0 && !won

    var modifier = Modifier.onPlaced { rect -> onMeasured(rect.height) }
    if (voted && !mine) modifier = modifier.state(UiState.DISABLED)
    if (mine || won) modifier = modifier.state(UiState.SELECTED)
    if (won) modifier = modifier.state(UiState.of("won"))
    if (lost) modifier = modifier.state(UiState.of("lost"))
    if (!voted && choices.decided < 0) {
        modifier = modifier.input(hoverable = true, clickable = true)
            .onClick { choose(option.index) }
    }

    val order = "choice-${option.index.coerceIn(0, MAX_STAGGERED_CHOICE)}"
    Row(tags = listOf("choice", order), modifier = modifier) {
        if (!option.icon.equals(NO_ICON, ignoreCase = true)) {
            Image(option.icon.ifEmpty { DESC_TEXTURE }, tags = listOf("choice-desc"))
        }
        Box(tags = listOf("choice-button")) { Text(option.text, tags = listOf("choice-text")) }
        Image(CURSOR_TEXTURE, tags = listOf("choice-cursor"))
    }
}

private fun UiScope.advance() = send { putString(DialogueUiKeys.Action, DialogueUiKeys.AdvanceAction) }

private fun UiScope.choose(index: Int) = send {
    putString(DialogueUiKeys.Action, DialogueUiKeys.ChooseAction)
    putInt(DialogueUiKeys.Index, index)
}
