package ru.hollowhorizon.hollowengine.client.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.*
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
        val changed = recomposer.changeCount != observedChangeCount
        if (changed) {
            UiNodeKeys.assign(rootNode)
        }
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
        instance.layoutState.attachTo(current)
        current.invalidateLayout()
    }

    override fun insertBottomUp(index: Int, instance: UiNode) = Unit

    override fun remove(index: Int, count: Int) {
        repeat(count) {
            current.children.removeAt(index).detachLayoutParentRecursively()
        }
        current.invalidateLayout()
    }

    override fun move(from: Int, to: Int, count: Int) {
        if (from == to || count <= 0) return
        val moving = current.children.subList(from, from + count).toList()
        repeat(count) { current.children.removeAt(from) }
        val target = if (to > from) to - count else to
        current.children.addAll(target, moving)
        current.invalidateLayout()
    }

    override fun onClear() {
        root.children.forEach { it.detachLayoutParentRecursively() }
        root.children.clear()
        root.invalidateLayout()
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
    ReusableComposeNode<BoxNode, HollowUiApplier>(
        factory = { BoxNode(id, layout, tags, modifiers, attributes) },
        update = {
            update(layout) {
                this.layout = it
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    ReusableComposeNode<BoxNode, HollowUiApplier>(
        factory = { BoxNode(id, UiLayout.Column, tags, modifiers, attributes) },
        update = {
            update(UiLayout.Column) {
                layout = it
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    ReusableComposeNode<BoxNode, HollowUiApplier>(
        factory = { BoxNode(id, UiLayout.Row, tags, modifiers, attributes) },
        update = {
            update(UiLayout.Row) {
                layout = it
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    ReusableComposeNode<BoxNode, HollowUiApplier>(
        factory = { BoxNode(id, UiLayout.LazyColumn, tags, modifiers, attributes) },
        update = {
            update(UiLayout.LazyColumn) {
                layout = it
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    ReusableComposeNode<BoxNode, HollowUiApplier>(
        factory = { BoxNode(id, UiLayout.LazyRow, tags, modifiers, attributes) },
        update = {
            update(UiLayout.LazyRow) {
                layout = it
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    ReusableComposeNode<BoxNode, HollowUiApplier>(
        factory = { BoxNode(id, layout, tags, modifiers, attributes) },
        update = {
            update(layout) {
                this.layout = it
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    ReusableComposeNode<TextNode, HollowUiApplier>(
        factory = { TextNode(value.bound(), id, tags, modifiers, attributes) },
        update = {
            update(value) {
                text = it.bound()
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    ReusableComposeNode<TextNode, HollowUiApplier>(
        factory = { TextNode(textContent, id, tags, modifiers, attributes) },
        update = {
            update(textContent) {
                this.content = it
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    ReusableComposeNode<ImageNode, HollowUiApplier>(
        factory = { ImageNode(source.bound(), id, tags, modifiers, attributes) },
        update = {
            update(source) {
                this.source = it.bound()
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    ReusableComposeNode<CanvasNode, HollowUiApplier>(
        factory = { CanvasNode(renderer, id, tags, modifiers, attributes) },
        update = {
            update(renderer) {
                this.renderer = it
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    ReusableComposeNode<BaseUiNode, HollowUiApplier>(
        factory = {
            BaseUiNode(
                nodeType,
                id?.removePrefix("#"),
                tags.map { it.removePrefix(".") },
                modifiers,
                attributes,
            )
        },
        update = { updateCommon(modifiers, attributes, tags.map { it.removePrefix(".") }) },
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
    ReusableComposeNode<SliderNode, HollowUiApplier>(
        factory = { SliderNode(value, min, max, step, id, tags, modifiers, attributes) },
        update = {
            update(values) {
                apply(it)
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    ReusableComposeNode<CheckboxNode, HollowUiApplier>(
        factory = { CheckboxNode(checked, variant, id, tags, modifiers, attributes) },
        update = {
            update(values) {
                apply(it)
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    completionContributor: UiCompletionContributor? = null,
    indentSize: Int? = null,
    autoPairs: Boolean = false,
    readOnly: Boolean = false,
    diagnostics: List<UiTextDiagnostic> = emptyList(),
    inlayHints: List<UiInlayHint> = emptyList(),
    inlayHintsProvider: UiInlayHintsProvider? = null,
    inlayRevision: Long = 0L,
    placeholder: String = "",
    onChange: ((String) -> Unit)? = null,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
) {
    val modifiers = modifier.asList()
    val textFieldModifiers = modifiers + TextFieldDefaultKeyInputModifier
    val values = TextFieldValues(
        value,
        mode,
        filter,
        multiCaret,
        syntaxHighlighter,
        completionContributor,
        indentSize,
        autoPairs,
        readOnly,
        diagnostics,
        inlayHints,
        inlayHintsProvider,
        inlayRevision,
        placeholder,
        onChange,
    )
    ReusableComposeNode<TextFieldNode, HollowUiApplier>(
        factory = {
            TextFieldNode(
                value,
                mode,
                filter,
                multiCaret,
                syntaxHighlighter,
                completionContributor,
                indentSize,
                autoPairs,
                readOnly,
                diagnostics,
                inlayHints,
                inlayHintsProvider,
                onChange,
                id,
                tags,
                textFieldModifiers,
                attributes,
            )
                .also { it.placeholder = placeholder }
        },
        update = {
            update(values) {
                apply(it)
                invalidateLayout()
            }
            updateCommon(textFieldModifiers, attributes, tags)
        },
        content = {
            TextFieldInlayWidgets(value, inlayHints, inlayHintsProvider, inlayRevision)
        },
    )
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun TextFieldInlayWidgets(
    value: String,
    inlayHints: List<UiInlayHint>,
    provider: UiInlayHintsProvider?,
    revision: Long,
) {
    textFieldActiveInlayHints(value, inlayHints, provider).forEachIndexed { index, hint ->
        val widgetId = textFieldInlayWidgetId(hint, index)
        key(widgetId) {
            InlineWidget(
                id = widgetId,
                tags = listOf("text-field-inlay", "code-editor-inlay"),
            ) {
                Text(hint.text, tags = listOf("text-field-inlay-text", "code-editor-inlay-text"))
            }
        }
    }
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
    ReusableComposeNode<ItemNode, HollowUiApplier>(
        factory = { ItemNode(item.bound(), id, tags, modifiers, attributes) },
        update = {
            update(item) {
                this.item = it.bound()
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    ReusableComposeNode<EntityNode, HollowUiApplier>(
        factory = { EntityNode(entity.bound(), id, tags, modifiers, attributes) },
        update = {
            update(entity) {
                this.entity = it.bound()
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    ReusableComposeNode<PopupNode, HollowUiApplier>(
        factory = { PopupNode(anchor, alignment, id, tags, modifiers, attributes) },
        update = {
            update(values) {
                apply(it)
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
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
    val completionContributor: UiCompletionContributor?,
    val indentSize: Int?,
    val autoPairs: Boolean,
    val readOnly: Boolean,
    val diagnostics: List<UiTextDiagnostic>,
    val inlayHints: List<UiInlayHint>,
    val inlayHintsProvider: UiInlayHintsProvider?,
    val inlayRevision: Long,
    val placeholder: String,
    val onChange: ((String) -> Unit)?,
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
    completionContributor = values.completionContributor
    indentSize = values.indentSize
    autoPairs = values.autoPairs
    readOnly = values.readOnly
    diagnostics = values.diagnostics
    inlayHints = values.inlayHints
    inlayHintsProvider = values.inlayHintsProvider
    placeholder = values.placeholder
    onChange = values.onChange
    applyExternalValue(values.value)
}

private fun PopupNode.apply(values: PopupValues) {
    anchor = values.anchor
    alignment = values.alignment
}

fun <T : BaseUiNode> Updater<T>.updateCommon(
    modifiers: List<Modifier>,
    attributes: Map<String, String>,
    tags: Iterable<String>? = null,
) {
    if (tags != null) {
        val normalizedTags = tags.map { it.removePrefix(".") }.toSet()
        update(normalizedTags) {
            if (this.tags == it) return@update
            this.tags.clear()
            this.tags += it
            invalidateLayout()
        }
    }
    update(modifiers) {
        this.modifiers.clear()
        this.modifiers += it
        invalidateLayout()
    }
    update(attributes) {
        replaceCustomAttributes(it)
        invalidateLayout()
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
