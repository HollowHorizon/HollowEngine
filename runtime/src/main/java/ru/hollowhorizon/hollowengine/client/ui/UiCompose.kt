package ru.hollowhorizon.hollowengine.client.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.*
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.ui.layout.*
import ru.hollowhorizon.hollowengine.client.ui.style.DefaultUiFontSize
import ru.hollowhorizon.hollowengine.client.ui.style.UiCaretBlinkKeyframes
import ru.hollowhorizon.hollowengine.client.ui.text.Shadow
import ru.hollowhorizon.hollowengine.client.ui.style.UiCaretBlinkPeriodMillis
import ru.hollowhorizon.hollowengine.client.ui.style.UiStylesheetReference
import ru.hollowhorizon.hollowengine.client.ui.widgets.*
import kotlin.coroutines.CoroutineContext

typealias HollowUiContent = @Composable () -> Unit

val LocalUiFrameTimeNanos = staticCompositionLocalOf { 0L }
val LocalStylesheets = staticCompositionLocalOf<List<UiStylesheetReference>> { emptyList() }

private fun Modifier?.styleReferences(): List<UiStylesheetReference> = when (this) {
    null -> emptyList()
    is CompositeModifier -> flatten().filterIsInstance<StyleImportModifier>().map { it.reference }
    is StyleImportModifier -> listOf(reference)
    else -> emptyList()
}

class HollowUiComposition(
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
) : AutoCloseable {
    private val frameClock = BroadcastFrameClock()
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext + frameClock)
    private val recomposer = Recomposer(scope.coroutineContext)
    private val rootNode = BoxNode(
        measurePolicy = UiMeasurePolicies.box(UiBoxMode.STACK),
        modifiers = listOf(Modifier.alignItems(UiAlign.STRETCH, UiAlign.STRETCH).focusScope()),
    )
    private val applier = HollowUiApplier(rootNode)
    private val composition = Composition(applier, recomposer)
    private val recomposerJob: Job = scope.launch {
        try {
            recomposer.runRecomposeAndApplyChanges()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            HollowEngine.LOGGER.error("Critical error in Compose UI: $e")
        }
    }

    @Volatile
    private var observedChangeCount = recomposer.changeCount

    fun setContent(content: HollowUiContent): BoxNode {
        composition.setContent(content)
        return rootNode
    }

    fun frameRoot(nowNanos: Long = System.nanoTime()): BoxNode {
        applyPendingChanges(nowNanos)
        return rootNode
    }

    fun applyPendingChanges(nowNanos: Long = System.nanoTime()): Boolean {
        pumpPendingChanges(nowNanos)
        val changed = recomposer.changeCount != observedChangeCount
        observedChangeCount = recomposer.changeCount
        return changed
    }

    private fun pumpPendingChanges(nowNanos: Long) {
        Snapshot.sendApplyNotifications()
        if (frameClock.hasAwaiters) frameClock.sendFrame(nowNanos)
    }

    override fun close() {
        scope.cancel()
        composition.dispose()
        recomposer.cancel()
        recomposerJob.cancel()
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
fun Layout(
    content: HollowUiContent,
    modifier: Modifier? = null,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    attributes: Map<String, String> = emptyMap(),
    measurePolicy: UiMeasurePolicy,
) {
    val modifiers = modifier.asList()
    val imports = modifier.styleReferences()
    val scoped: HollowUiContent =
        if (imports.isEmpty()) content
        else {
            {
                CompositionLocalProvider(LocalStylesheets provides LocalStylesheets.current + imports) {
                    content()
                }
            }
        }
    ReusableComposeNode<BoxNode, HollowUiApplier>(
        factory = { BoxNode(id, measurePolicy, tags, modifiers, attributes) },
        update = {
            update(measurePolicy) {
                this.measurePolicy = it
                invalidateLayout()
            }
            updateCommon(modifiers, attributes, tags)
        },
        content = scoped,
    )
}


@Composable
fun Box(
    id: String? = null,
    mode: UiBoxMode = UiBoxMode.FREE,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) = Layout(content, modifier, id, tags, attributes, UiMeasurePolicies.box(mode))

@Composable
fun Column(
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) = Layout(content, modifier, id, tags, attributes, UiMeasurePolicies.Column)

@Composable
fun Row(
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) = Layout(content, modifier, id, tags, attributes, UiMeasurePolicies.Row)

@Composable
fun Span(
    value: String,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
) {
    val modifiers = modifier.asList()
    ReusableComposeNode<SpanNode, HollowUiApplier>(
        factory = { SpanNode(value, id, tags, modifiers) },
        update = {
            update(value) {
                text = it
                invalidateLayout()
            }
            updateCommon(modifiers, emptyMap(), tags)
        },
    )
}

@Composable
fun Caret(
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
) = Box(
    id = id,
    tags = tags,
    modifier = Modifier
        .animation(UiCaretBlinkKeyframes, UiCaretBlinkPeriodMillis, iterationCount = Float.POSITIVE_INFINITY)
        .then(modifier ?: Modifier),
)

@Composable
fun LazyColumn(
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) = Layout(content, modifier, id, tags, attributes, UiMeasurePolicies.LazyColumn)

@Composable
fun LazyRow(
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) = Layout(content, modifier, id, tags, attributes, UiMeasurePolicies.LazyRow)

/**
 * A run of text. Built on the inline-flow framework: the literal becomes a [Span] and any
 * [content] (inline widgets, images, nested spans) flows alongside it. Text style props set
 * on the container (font, colour, effects, size) inherit down to the span.
 */
@Composable
fun Text(
    value: String,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent = {},
) = Layout(
    content = {
        Span(value)
        content()
    },
    modifier = modifier,
    id = id,
    tags = tags,
    attributes = attributes,
    measurePolicy = UiMeasurePolicies.InlineFlow,
)

/** An inline-flow container: compose [Span]s, images and inline widgets inside it directly. */
@Composable
fun Text(
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    content: HollowUiContent,
) = Layout(content, modifier, id, tags, attributes, UiMeasurePolicies.InlineFlow)

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
        factory = { ImageNode(source, id, tags, modifiers, attributes) },
        update = {
            update(source) {
                this.source = it
                invalidateDraw()
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
                invalidateInput()
                invalidateDraw()
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
                invalidateDraw()
            }
            updateCommon(modifiers, attributes, tags)
        },
    )
}

/**
 * Value/onChange convenience wrapper over [EditableTextField]: owns a [TextFieldState], mirrors
 * external [value] changes into it and reports edits back through [onChange]. The state (carets,
 * selection, undo history) lives for as long as the composition does.
 */
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
    completionRevision: Long = 0L,
    placeholder: String = "",
    wrap: Boolean? = null,
    fontSize: Float = DefaultUiFontSize,
    fontFamily: String? = null,
    textShadow: Shadow? = Shadow(offsetX = 1f, offsetY = 1f),
    caretColor: UiColor? = null,
    selectionColor: UiColor? = null,
    lineNumbers: Boolean = false,
    lineNumberColor: UiColor? = null,
    indentGuides: Boolean = false,
    indentGuideColor: UiColor? = null,
    onChange: ((String) -> Unit)? = null,
    state: TextFieldState? = null,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
) {
    val multiline = mode == UiTextFieldMode.MULTI_LINE
    val internalState = remember {
        state ?: TextFieldState(
            initialText = value,
            multiline = multiline,
            readOnly = readOnly,
            filter = filter,
            indentSize = indentSize,
            autoPairs = autoPairs,
            multiCaret = multiCaret,
            fontSize = fontSize,
            fontFamily = fontFamily,
            wrap = wrap ?: multiline,
        )
    }
    val fieldState = state ?: internalState
    fieldState.multiline = multiline
    fieldState.readOnly = readOnly
    fieldState.filter = filter
    fieldState.indentSize = indentSize
    fieldState.autoPairs = autoPairs
    fieldState.multiCaret = multiCaret
    fieldState.fontSize = fontSize
    fieldState.fontFamily = fontFamily
    fieldState.wrap = wrap ?: multiline
    fieldState.textShadow = textShadow
    if (caretColor != null) fieldState.caretColor = caretColor
    if (selectionColor != null) fieldState.selectionColor = selectionColor

    val sync = remember { TextFieldValueSync(value) }
    // Adopt an external value only when the parameter itself changed; edits made here win otherwise.
    if (value != sync.lastExternal) {
        val echoedNotification = sync.acknowledge(value)
        if (!echoedNotification && value != fieldState.text) {
            fieldState.setText(value, moveCaretToEnd = false)
            sync.reset(fieldState.text)
        }
    }
    sync.updateExternal(value)
    val text = fieldState.text
    SideEffect {
        if (text != sync.lastNotified) {
            sync.recordNotification(text)
            onChange?.invoke(text)
        }
    }

    EditableTextField(
        state = fieldState,
        modifier = modifier,
        id = id,
        tags = tags,
        syntaxHighlighter = syntaxHighlighter,
        inlayHints = inlayHints,
        inlayHintsProvider = inlayHintsProvider,
        inlayRevision = inlayRevision,
        completionContributor = completionContributor,
        completionRevision = completionRevision,
        diagnostics = diagnostics,
        placeholder = placeholder,
        lineNumbers = lineNumbers,
        lineNumberColor = lineNumberColor ?: EditableFieldLineNumberColor,
        indentGuides = indentGuides,
        indentGuideColor = indentGuideColor ?: EditableFieldIndentGuideColor,
        attributes = attributes,
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
    ReusableComposeNode<ItemNode, HollowUiApplier>(
        factory = { ItemNode(item, id, tags, modifiers, attributes) },
        update = {
            update(item) {
                this.item = it
                invalidateDraw()
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
        factory = { EntityNode(entity, id, tags, modifiers, attributes) },
        update = {
            update(entity) {
                this.entity = it
                invalidateDraw()
            }
            updateCommon(modifiers, attributes, tags)
        },
    )
}

/** Popups render above everything; the OverlayHost carries this layer so all passes prefer it. */
const val OverlayLayer = Int.MAX_VALUE / 2

/** Handle passed to a [Popup]'s content so it can close itself (`dismiss()`) without external state. */
interface PopupScope {
    fun dismiss()
}

typealias PopupContent = @Composable PopupScope.() -> Unit

/**
 * An overlay anchored to [anchorBounds] in root coordinates (from `Modifier.onPlaced` or a cursor
 * point). Composed out of line by the surface's [OverlayHost] so it is never clipped and sits on top;
 * registers with the [OverlayManager] so an outside click or Escape closes it (when [dismissOnOutside]).
 */
@Composable
fun Popup(
    anchorBounds: UiRect,
    alignment: UiPopupAlignment = UiPopupAlignment.BelowStart,
    layer: Int = 0,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    attributes: Map<String, String> = emptyMap(),
    dismissOnOutside: Boolean = true,
    onDismiss: (() -> Unit)? = null,
    content: PopupContent = {},
) {
    val manager = LocalOverlayManager.current ?: return
    val anchor = rememberUpdatedState(anchorBounds)
    val align = rememberUpdatedState(alignment)
    val popupTags = rememberUpdatedState(tags)
    val popupModifier = rememberUpdatedState(modifier)
    val popupAttributes = rememberUpdatedState(attributes)
    val popupContent = rememberUpdatedState(content)
    val dismiss = rememberUpdatedState(onDismiss)
    val stylesheets = rememberUpdatedState(LocalStylesheets.current)

    val scope = remember {
        object : PopupScope {
            override fun dismiss() {
                dismiss.value?.invoke()
            }
        }
    }
    val entry = remember {
        PopupEntry(Any()).apply {
            this.content = {
                PopupNodeEmitter(
                    anchorBounds = anchor.value,
                    alignment = align.value,
                    id = id,
                    tags = popupTags.value,
                    modifier = popupModifier.value,
                    attributes = popupAttributes.value,
                    stylesheets = stylesheets.value,
                ) { popupContent.value.invoke(scope) }
            }
        }
    }
    SideEffect {
        entry.layer = layer
        entry.dismissOnOutside = dismissOnOutside
        entry.onDismiss = onDismiss
    }
    DisposableEffect(manager, entry) {
        val unregister = manager.register(entry)
        onDispose { unregister() }
    }
}

@Composable
private fun PopupNodeEmitter(
    anchorBounds: UiRect,
    alignment: UiPopupAlignment,
    id: String?,
    tags: Iterable<String>,
    modifier: Modifier?,
    attributes: Map<String, String>,
    stylesheets: List<UiStylesheetReference>,
    content: HollowUiContent,
) {
    val modifiers = stylesheets.fold((modifier ?: Modifier).focusScope().input(hoverable = true)) { acc, ref ->
        acc.style(ref)
    }.asList()
    val values = PopupValues(anchorBounds, alignment)
    ReusableComposeNode<PopupNode, HollowUiApplier>(
        factory = { PopupNode(anchorBounds, alignment, id, tags, modifiers, attributes) },
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

/**
 * Renders the surface's overlays above the content. While any popup is open it composes a top-layer
 * stack holding: an optional full-screen dismiss catcher (a [focusScope], hence always key-active, so
 * it closes every dismissable overlay on an outside click or Escape)
 */
@Composable
fun OverlayHost() {
    val manager = LocalOverlayManager.current ?: return
    if (manager.popups.isEmpty()) return
    Box(mode = UiBoxMode.STACK, modifier = Modifier.size(100.percent, 100.percent).layer(OverlayLayer)) {
        if (manager.hasDismissable) {
            Box(
                tags = listOf("overlay-dismiss"),
                modifier = Modifier.size(100.percent, 100.percent)
                    .focusScope()
                    .onClick { manager.dismissAll() }
                    .onKeyInput { input ->
                        if (input.key == GLFW.GLFW_KEY_ESCAPE) {
                            manager.dismissAll()
                            input.consume()
                        }
                    },
            )
        }
        Layout(
            content = {
                for (entry in manager.popups.sortedBy { it.layer }) {
                    key(entry.key) { entry.content() }
                }
            },
            modifier = Modifier.size(100.percent, 100.percent),
            measurePolicy = PopupOverlayMeasurePolicy,
        )
    }
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

private data class PopupValues(
    val anchorBounds: UiRect,
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

private fun PopupNode.apply(values: PopupValues) {
    anchorBounds = values.anchorBounds
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
        }
    }
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
    UiSliderType -> setOf("value", "min", "max", "step")
    UiCheckboxType -> setOf("checked", "variant")
    else -> emptySet()
}

private fun Modifier?.asList(): List<Modifier> = this?.let(::listOf).orEmpty()
