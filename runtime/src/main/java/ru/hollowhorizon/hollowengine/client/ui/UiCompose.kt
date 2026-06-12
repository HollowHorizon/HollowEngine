package ru.hollowhorizon.hollowengine.client.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.Updater
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

typealias HollowUiContent = @Composable () -> Unit

val LocalUiFrameTimeNanos = staticCompositionLocalOf { 0L }

class HollowUiComposition(
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
) : AutoCloseable {
    private val frameClock = BroadcastFrameClock()
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext + frameClock)
    private val recomposer = Recomposer(scope.coroutineContext)
    private val rootNode = BoxNode(layout = UiLayout.Column)
    private val applier = HollowUiApplier(rootNode)
    private val composition = Composition(applier, recomposer)
    private val recomposerJob: Job = scope.launch { recomposer.runRecomposeAndApplyChanges() }
    private var observedChangeCount = recomposer.changeCount
    private var closed = false

    val root: BoxNode
        get() {
            applyPendingChanges()
            return rootNode
        }

    fun setContent(content: HollowUiContent): BoxNode {
        check(!closed) { "HollowUiComposition is already closed" }
        composition.setContent(content)
        applyPendingChanges()
        return rootNode
    }

    fun frameRoot(nowNanos: Long = System.nanoTime()): BoxNode {
        applyPendingChanges(nowNanos)
        return rootNode
    }

    fun applyPendingChanges(nowNanos: Long = System.nanoTime()): Boolean {
        if (closed) return false
        pumpPendingChanges(nowNanos)
        UiNodeKeys.assign(rootNode)
        val changed = recomposer.changeCount != observedChangeCount
        observedChangeCount = recomposer.changeCount
        return changed
    }

    private fun pumpPendingChanges(nowNanos: Long) {
        Snapshot.sendApplyNotifications()
        if (frameClock.hasAwaiters) frameClock.sendFrame(nowNanos)
        Snapshot.sendApplyNotifications()
        if (frameClock.hasAwaiters) frameClock.sendFrame(nowNanos)
    }

    override fun close() {
        if (closed) return
        closed = true
        composition.dispose()
        recomposer.cancel()
        recomposerJob.cancel()
        scope.cancel()
    }
}

class HollowUiApplier(root: BoxNode) : AbstractApplier<UiNode>(root) {
    override fun insertTopDown(index: Int, instance: UiNode) {
        current.children.add(index, instance)
    }

    override fun insertBottomUp(index: Int, instance: UiNode) = Unit

    override fun remove(index: Int, count: Int) {
        repeat(count) { current.children.removeAt(index) }
    }

    override fun move(from: Int, to: Int, count: Int) {
        if (from == to || count <= 0) return
        val moving = current.children.subList(from, from + count).toList()
        repeat(count) { current.children.removeAt(from) }
        val target = if (to > from) to - count else to
        current.children.addAll(target, moving)
    }

    override fun onClear() {
        root.children.clear()
    }
}

@Composable
fun Box(
    id: String? = null,
    mode: UiBoxMode = UiBoxMode.FREE,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) {
    val modifiers = modifier.asList()
    val layout = UiLayout.Box(mode)
    ComposeNode<BoxNode, HollowUiApplier>(
        factory = { BoxNode(id, layout, tags, modifiers, attributes) },
        update = {
            update(layout) { this.layout = it }
            updateCommon(modifiers, attributes)
        },
        content = content,
    )
}

@Composable
fun Column(
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) {
    val modifiers = modifier.asList()
    ComposeNode<BoxNode, HollowUiApplier>(
        factory = { BoxNode(id, UiLayout.Column, tags, modifiers, attributes) },
        update = {
            update(UiLayout.Column) { layout = it }
            updateCommon(modifiers, attributes)
        },
        content = content,
    )
}

@Composable
fun Row(
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) {
    val modifiers = modifier.asList()
    ComposeNode<BoxNode, HollowUiApplier>(
        factory = { BoxNode(id, UiLayout.Row, tags, modifiers, attributes) },
        update = {
            update(UiLayout.Row) { layout = it }
            updateCommon(modifiers, attributes)
        },
        content = content,
    )
}

@Composable
fun LazyColumn(
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) {
    val modifiers = modifier.asList()
    ComposeNode<BoxNode, HollowUiApplier>(
        factory = { BoxNode(id, UiLayout.LazyColumn, tags, modifiers, attributes) },
        update = {
            update(UiLayout.LazyColumn) { layout = it }
            updateCommon(modifiers, attributes)
        },
        content = content,
    )
}

@Composable
fun LazyRow(
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) {
    val modifiers = modifier.asList()
    ComposeNode<BoxNode, HollowUiApplier>(
        factory = { BoxNode(id, UiLayout.LazyRow, tags, modifiers, attributes) },
        update = {
            update(UiLayout.LazyRow) { layout = it }
            updateCommon(modifiers, attributes)
        },
        content = content,
    )
}

@Composable
fun Layout(
    content: HollowUiContent,
    modifier: Modifier? = null,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
    measurePolicy: UiMeasurePolicy,
) {
    val modifiers = modifier.asList()
    val layout = UiLayout.Custom(measurePolicy)
    ComposeNode<BoxNode, HollowUiApplier>(
        factory = { BoxNode(id, layout, tags, modifiers, attributes) },
        update = {
            update(layout) { this.layout = it }
            updateCommon(modifiers, attributes)
        },
        content = content,
    )
}

@Composable
fun Text(
    value: String,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) {
    val modifiers = modifier.asList()
    ComposeNode<TextNode, HollowUiApplier>(
        factory = { TextNode(value.bound(), id, tags, modifiers, attributes) },
        update = {
            update(value) { text = it.bound() }
            updateCommon(modifiers, attributes)
        },
        content = content,
    )
}

@Composable
fun Text(
    textContent: UiTextContent,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) {
    val modifiers = modifier.asList()
    ComposeNode<TextNode, HollowUiApplier>(
        factory = { TextNode(textContent, id, tags, modifiers, attributes) },
        update = {
            update(textContent) { this.content = it }
            updateCommon(modifiers, attributes)
        },
        content = content,
    )
}

@Composable
fun InlineWidget(
    id: String,
    modifier: Modifier? = null,
    tags: Iterable<String> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) {
    Box(id = id, tags = tags, modifier = modifier, attributes = attributes, content = content)
}

@Composable
fun Image(
    source: String,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
) {
    val modifiers = modifier.asList()
    ComposeNode<ImageNode, HollowUiApplier>(
        factory = { ImageNode(source.bound(), id, tags, modifiers, attributes) },
        update = {
            update(source) { this.source = it.bound() }
            updateCommon(modifiers, attributes)
        },
    )
}

@Composable
fun Canvas(
    renderer: String? = null,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
) {
    val modifiers = modifier.asList()
    ComposeNode<CanvasNode, HollowUiApplier>(
        factory = { CanvasNode(renderer, id, tags, modifiers, attributes) },
        update = {
            update(renderer) { this.renderer = it }
            updateCommon(modifiers, attributes)
        },
    )
}

@Composable
fun Element(
    type: String,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) {
    val modifiers = modifier.asList()
    val nodeType = type.lowercase()
    ComposeNode<BaseUiNode, HollowUiApplier>(
        factory = {
            BaseUiNode(
                nodeType,
                id?.removePrefix("#"),
                tags.map { it.removePrefix(".") },
                modifiers,
                attributes,
            )
        },
        update = { updateCommon(modifiers, attributes) },
        content = content,
    )
}

@Composable
fun Slider(
    value: Float = 0f,
    min: Float = 0f,
    max: Float = 1f,
    step: Float = 0f,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
) {
    val modifiers = modifier.asList()
    val values = SliderValues(value, min, max, step)
    ComposeNode<SliderNode, HollowUiApplier>(
        factory = { SliderNode(value, min, max, step, id, tags, modifiers, attributes) },
        update = {
            update(values) { apply(it) }
            updateCommon(modifiers, attributes)
        },
    )
}

@Composable
fun Checkbox(
    checked: Boolean = false,
    variant: UiCheckboxVariant = UiCheckboxVariant.CHECKBOX,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
) {
    val modifiers = modifier.asList()
    val values = CheckboxValues(checked, variant)
    ComposeNode<CheckboxNode, HollowUiApplier>(
        factory = { CheckboxNode(checked, variant, id, tags, modifiers, attributes) },
        update = {
            update(values) { apply(it) }
            updateCommon(modifiers, attributes)
        },
    )
}

@Composable
fun TextField(
    value: String = "",
    mode: UiTextFieldMode = UiTextFieldMode.SINGLE_LINE,
    filter: UiTextInputFilter = UiTextInputFilter.ANY,
    multiCaret: Boolean = false,
    syntaxHighlighter: UiSyntaxHighlighter? = null,
    placeholder: String = "",
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
) {
    val modifiers = modifier.asList()
    val textFieldModifiers = modifiers + TextFieldDefaultKeyInputModifier
    val values = TextFieldValues(value, mode, filter, multiCaret, syntaxHighlighter, placeholder)
    ComposeNode<TextFieldNode, HollowUiApplier>(
        factory = {
            TextFieldNode(value, mode, filter, multiCaret, syntaxHighlighter, id, tags, textFieldModifiers, attributes)
                .also { it.placeholder = placeholder }
        },
        update = {
            update(values) { apply(it) }
            updateCommon(textFieldModifiers, attributes)
        },
    )
}

@Composable
fun Item(
    item: String,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
) {
    val modifiers = modifier.asList()
    ComposeNode<ItemNode, HollowUiApplier>(
        factory = { ItemNode(item.bound(), id, tags, modifiers, attributes) },
        update = {
            update(item) { this.item = it.bound() }
            updateCommon(modifiers, attributes)
        },
    )
}

@Composable
fun Entity(
    entity: String,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
) {
    val modifiers = modifier.asList()
    ComposeNode<EntityNode, HollowUiApplier>(
        factory = { EntityNode(entity.bound(), id, tags, modifiers, attributes) },
        update = {
            update(entity) { this.entity = it.bound() }
            updateCommon(modifiers, attributes)
        },
    )
}

@Composable
fun Popup(
    anchor: UiPopupAnchor,
    alignment: UiPopupAlignment = UiPopupAlignment.BelowStart,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) {
    val modifiers = modifier.asList()
    val values = PopupValues(anchor, alignment)
    ComposeNode<PopupNode, HollowUiApplier>(
        factory = { PopupNode(anchor, alignment, id, tags, modifiers, attributes) },
        update = {
            update(values) { apply(it) }
            updateCommon(modifiers, attributes)
        },
        content = content,
    )
}

private data class SliderValues(
    val value: Float,
    val min: Float,
    val max: Float,
    val step: Float,
)

private data class CheckboxValues(
    val checked: Boolean,
    val variant: UiCheckboxVariant,
)

private data class TextFieldValues(
    val value: String,
    val mode: UiTextFieldMode,
    val filter: UiTextInputFilter,
    val multiCaret: Boolean,
    val syntaxHighlighter: UiSyntaxHighlighter?,
    val placeholder: String,
)

private data class PopupValues(
    val anchor: UiPopupAnchor,
    val alignment: UiPopupAlignment,
)

private fun SliderNode.apply(values: SliderValues) {
    min = values.min
    max = values.max
    step = values.step
    value = values.value
}

private fun CheckboxNode.apply(values: CheckboxValues) {
    variant = values.variant
    checked = values.checked
}

private fun TextFieldNode.apply(values: TextFieldValues) {
    mode = values.mode
    filter = values.filter
    multiCaret = values.multiCaret
    syntaxHighlighter = values.syntaxHighlighter
    placeholder = values.placeholder
    value = values.value
}

private fun PopupNode.apply(values: PopupValues) {
    anchor = values.anchor
    alignment = values.alignment
}

fun <T : BaseUiNode> Updater<T>.updateCommon(
    modifiers: List<Modifier>,
    attributes: Map<String, String>,
) {
    update(modifiers) {
        this.modifiers.clear()
        this.modifiers += it
    }
    update(attributes) {
        replaceCustomAttributes(it)
    }
}

private fun BaseUiNode.replaceCustomAttributes(attributes: Map<String, String>) {
    val builtInAttributes = builtInAttributeNames()
    val retained = this.attributes.filterKeys { it in builtInAttributes }
    this.attributes.clear()
    this.attributes += retained
    this.attributes += attributes.filterKeys { it !in builtInAttributes }
}

private fun BaseUiNode.builtInAttributeNames(): Set<String> = when (type) {
    UiNodeType.SLIDER.typeName -> setOf("value", "min", "max", "step")
    UiNodeType.CHECKBOX.typeName -> setOf("checked", "variant")
    UiNodeType.TEXT_FIELD.typeName -> setOf("value", "placeholder", "mode", "filter", "multi-caret")
    else -> emptySet()
}

private fun Modifier?.asList(): List<Modifier> = this?.let(::listOf).orEmpty()
