package ru.hollowhorizon.hollowengine.client.ui.ide.files

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeOpenFile
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import ru.hollowhorizon.hollowengine.client.ui.widgets.*
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private const val AutoSaveDelayMillis = 900L
private const val ListWidth = 220f
private const val AddIcon = "hollowengine:textures/gui/icons/add.svg"
private const val RemoveIcon = "hollowengine:textures/gui/icons/remove.svg"
private const val SoundIcon = "hollowengine:textures/gui/icons/file_sound.png"

private fun key(name: String) = "hollowengine.gui.sounds_editor.$name"

/**
 * Specialized editor for `sounds.json`: a master/detail view over
 * [HollowIdeSoundsDocument] that lets creators add and tune sound events quickly,
 * with debounced auto-save. Styling lives in `sounds-editor.hss`.
 */
@Composable
internal fun HollowIdeSoundsEditor(file: HollowIdeOpenFile) {
    val document = file.document as HollowIdeSoundsDocument
    val listScroll = remember(document) { UiScrollHandle() }
    val detailScroll = remember(document) { UiScrollHandle() }
    var query by remember(document) { mutableStateOf("") }
    var selected by remember(document) { mutableStateOf(document.events.firstOrNull()) }

    val namespaceDir = file.path.substringBeforeLast('/', "")
    val namespace = namespaceDir.substringAfterLast('/').ifEmpty { "minecraft" }
    val availableSounds = remember(file.path) { scanAvailableSounds(namespaceDir, namespace) }

    val soundCompletions: UiCompletionContributor? = remember(availableSounds) {
        if (availableSounds.isEmpty()) null
        else UiCompletionContributor { context ->
            val prefix = context.text.take(context.caret.coerceIn(0, context.text.length))
            availableSounds
                .filter { prefix.isEmpty() || it.startsWith(prefix, ignoreCase = true) }
                .map { UiTextCompletion(label = it, insertText = it, icon = SoundIcon) }
        }
    }

    fun markChanged() {
        document.touch()
        file.updateDirty(document.isModified)
    }

    LaunchedEffect(document.revision) {
        if (!document.isModified) return@LaunchedEffect
        delay(AutoSaveDelayMillis.milliseconds)
        if (document.isModified) file.save()
    }

    val current = selected?.takeIf { it in document.events } ?: document.events.firstOrNull()

    Column(
        tags = listOf("sounds-editor-root"),
        modifier = Modifier.style("hollowengine:ui/styles/sounds-editor.hss")
            .size(100.percent, 100.percent)
            .focusScope(),
    ) {
        Row(tags = listOf("sounds-editor-toolbar"), modifier = Modifier.alignItems(vertical = UiAlign.CENTER)) {
            Text(key("title").lang, tags = listOf("sounds-editor-title"), modifier = Modifier.grow(1f))
            Row(modifier = Modifier.gap(5.px).alignItems(vertical = UiAlign.CENTER)) {
                Box(tags = listOf("sounds-editor-status-dot") + if (file.dirty) listOf("dirty") else listOf("saved"))
                Text(
                    (if (file.dirty) key("unsaved") else key("saved")).lang,
                    tags = listOf("sounds-editor-status") + if (file.dirty) listOf("dirty") else emptyList(),
                )
            }
        }

        Row(modifier = Modifier.size(100.percent, 0.px).grow(1f).gap(6.px)) {
            Column(
                tags = listOf("sounds-editor-list"),
                modifier = Modifier.size(ListWidth.px, 100.percent),
            ) {
                TextField(
                    value = query,
                    onChange = { query = it },
                    placeholder = key("search").lang,
                    tags = listOf("sounds-editor-search"),
                    modifier = Modifier.size(100.percent, 22.px),
                )
                SoundsButton(key("add_event"), AddIcon, modifier = Modifier.size(100.percent, 24.px)) {
                    selected = document.addEvent()
                    file.updateDirty(document.isModified)
                }
                Column(
                    tags = listOf("sounds-editor-list-scroll"),
                    modifier = Modifier.size(100.percent, 0.px).grow(1f).scrollable(horizontal = false, state = listScroll),
                ) {
                    val filtered = document.events.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                    if (filtered.isEmpty()) {
                        Text(key("no_events").lang, tags = listOf("sounds-editor-empty"))
                    }
                    filtered.forEach { event ->
                        EventRow(
                            event = event,
                            selected = event === current,
                            onSelect = { selected = event },
                            onDelete = {
                                if (event === selected) selected = null
                                document.removeEvent(event)
                                file.updateDirty(document.isModified)
                            },
                        )
                    }
                }
            }

            Column(
                tags = listOf("sounds-editor-detail"),
                modifier = Modifier.size(0.px, 100.percent).grow(1f).scrollable(horizontal = false, state = detailScroll),
            ) {
                val event = current
                if (event == null) {
                    Text(key("placeholder").lang, tags = listOf("sounds-editor-placeholder"))
                } else {
                    EventDetail(
                        event = event,
                        soundCompletions = soundCompletions,
                        onChanged = ::markChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    event: SoundEvent,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val tags = buildList {
        add("sounds-editor-event")
        if (selected) add("selected")
    }
    Row(
        tags = tags,
        modifier = Modifier.size(100.percent, 24.px)
            .input(hoverable = true, clickable = true)
            .cursor(UiCursorShape.HAND)
            .alignItems(vertical = UiAlign.CENTER)
            .onClick { event2 ->
                if (event2.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) onSelect()
                event2.consume()
            },
    ) {
        Text(
            event.name.ifBlank { key("unnamed").lang },
            tags = listOf("sounds-editor-event-label"),
            modifier = Modifier.grow(1f),
        )
        Text(key("event_sounds").lang.format(event.sounds.size), tags = listOf("sounds-editor-event-count"))
        SoundsIconButton(RemoveIcon, tags = listOf("sounds-editor-event-delete"), onClick = onDelete)
    }
}

@Composable
private fun EventDetail(
    event: SoundEvent,
    soundCompletions: UiCompletionContributor?,
    onChanged: () -> Unit,
) {
    LabeledField(key("name").lang) {
        TextField(
            value = event.name,
            onChange = { event.name = sanitizeEventName(it); onChanged() },
            placeholder = "block.custom.break",
            tags = listOf("sounds-editor-field"),
            modifier = Modifier.size(100.percent, 22.px),
        )
    }
    LabeledField(key("subtitle").lang) {
        TextField(
            value = event.subtitle,
            onChange = { event.subtitle = it; onChanged() },
            placeholder = "subtitles.block.custom.break",
            tags = listOf("sounds-editor-field"),
            modifier = Modifier.size(100.percent, 22.px),
        )
    }
    Row(tags = listOf("sounds-editor-toggle"), modifier = Modifier.size(100.percent, 20.px).alignItems(vertical = UiAlign.CENTER)) {
        Text(key("replace").lang, tags = listOf("sounds-editor-label"), modifier = Modifier.grow(1f))
        Checkbox(
            checked = event.replace,
            variant = UiCheckboxVariant.SWITCH,
            onCheckedChange = { event.replace = it; onChanged() },
        )
    }

    Row(tags = listOf("sounds-editor-section"), modifier = Modifier.size(100.percent, 24.px).alignItems(vertical = UiAlign.CENTER)) {
        Text(key("sounds").lang.format(event.sounds.size), tags = listOf("sounds-editor-section-title"), modifier = Modifier.grow(1f))
        SoundsButton(key("add_sound"), AddIcon, modifier = Modifier.size(112.px, 22.px)) {
            event.sounds += SoundEntry()
            onChanged()
        }
    }

    event.sounds.forEachIndexed { index, sound ->
        SoundCard(
            sound = sound,
            index = index,
            soundCompletions = soundCompletions,
            onDelete = { event.sounds.remove(sound); onChanged() },
            onChanged = onChanged,
        )
    }
}

@Composable
private fun SoundCard(
    sound: SoundEntry,
    index: Int,
    soundCompletions: UiCompletionContributor?,
    onDelete: () -> Unit,
    onChanged: () -> Unit,
) {
    Column(tags = listOf("sounds-editor-sound-card")) {
        Row(modifier = Modifier.size(100.percent, 22.px).gap(4.px).alignItems(vertical = UiAlign.CENTER)) {
            TextField(
                value = sound.name,
                onChange = { sound.name = sanitizeSoundName(it); onChanged() },
                placeholder = "namespace:path",
                completionContributor = soundCompletions,
                tags = listOf("sounds-editor-field"),
                modifier = Modifier.size(0.px, 22.px).grow(1f),
            )
            SoundsIconButton(RemoveIcon, tags = listOf("sounds-editor-sound-delete"), onClick = onDelete)
        }

        SliderRow(key("volume").lang, sound.volume, 0f, 1f) { sound.volume = it; onChanged() }
        SliderRow(key("pitch").lang, sound.pitch, 0.5f, 2f) { sound.pitch = it; onChanged() }

        Row(modifier = Modifier.size(100.percent, 22.px).gap(6.px).alignItems(vertical = UiAlign.CENTER)) {
            IntField(key("weight").lang, sound.weight, min = 1) { sound.weight = it; onChanged() }
            IntField(key("attenuation").lang, sound.attenuationDistance, min = 0) { sound.attenuationDistance = it; onChanged() }
        }

        Row(modifier = Modifier.size(100.percent, 20.px).gap(10.px).alignItems(vertical = UiAlign.CENTER)) {
            ToggleChip("stream", sound.stream) { sound.stream = it; onChanged() }
            ToggleChip("preload", sound.preload) { sound.preload = it; onChanged() }
            Text("${key("type").lang}:", tags = listOf("sounds-editor-label"))
            val typeId = "sounds-editor-type-$index"
            var typeOpen by remember { mutableStateOf(false) }
            UiDropdown(
                id = typeId,
                label = sound.type.jsonName,
                expanded = typeOpen,
                onExpandedChange = { typeOpen = it },
                items = SoundEntryType.entries.map { type ->
                    UiDropdownItem(
                        label = type.jsonName,
                        checked = sound.type == type,
                        onClick = { sound.type = type; onChanged() },
                    )
                },
                tags = listOf("sounds-editor-type"),
            )
        }
    }
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column(tags = listOf("sounds-editor-labeled")) {
        Text(label, tags = listOf("sounds-editor-label"))
        content()
    }
}

@Composable
private fun SliderRow(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Row(modifier = Modifier.size(100.percent, 20.px).gap(6.px).alignItems(vertical = UiAlign.CENTER)) {
        Text(label, tags = listOf("sounds-editor-label"), modifier = Modifier.size(96.px, 14.px))
        Slider(
            value = value,
            min = min,
            max = max,
            step = 0.05f,
            onValueChange = onChange,
            modifier = Modifier.size(0.px, 14.px).grow(1f),
        )
        Text("%.2f".format(value), tags = listOf("sounds-editor-value"), modifier = Modifier.size(34.px, 14.px))
    }
}

@Composable
private fun IntField(label: String, value: Int, min: Int, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.size(0.px, 22.px).grow(1f).gap(4.px).alignItems(vertical = UiAlign.CENTER)) {
        Text(label, tags = listOf("sounds-editor-label"), modifier = Modifier.grow(1f))
        TextField(
            value = value.toString(),
            filter = UiTextInputFilter.INTEGER,
            onChange = { input -> input.toIntOrNull()?.let { onChange(it.coerceAtLeast(min)) } },
            tags = listOf("sounds-editor-field"),
            modifier = Modifier.size(52.px, 22.px),
        )
    }
}

@Composable
private fun ToggleChip(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.gap(4.px).alignItems(vertical = UiAlign.CENTER)) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, tags = listOf("sounds-editor-label"))
    }
}

@Composable
private fun SoundsButton(labelKey: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        tags = listOf("sounds-editor-button"),
        modifier = modifier.cursor(UiCursorShape.HAND).alignItems(UiAlign.CENTER, UiAlign.CENTER).gap(4.px)
            .onClick { event ->
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) onClick()
                event.consume()
            },
    ) {
        Image(icon, tags = listOf("sounds-editor-button-icon"))
        Text(labelKey.lang, tags = listOf("sounds-editor-button-label"))
    }
}

@Composable
private fun SoundsIconButton(icon: String, tags: List<String>, onClick: () -> Unit) {
    Image(
        icon,
        tags = listOf("sounds-editor-icon-button") + tags,
        modifier = Modifier.size(16.px, 16.px).cursor(UiCursorShape.HAND)
            .onClick { event ->
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) onClick()
                event.consume()
            },
    )
}

/** Enumerates audio files under `<namespace>/sounds/` as `namespace:path` ids for name suggestions. */
private fun scanAvailableSounds(namespaceDir: String, namespace: String): List<String> {
    if (namespaceDir.isEmpty()) return emptyList()
    val soundsRoot = runCatching { "$namespaceDir/sounds".fromReadablePath() }.getOrNull() ?: return emptyList()
    if (!soundsRoot.isDirectory) return emptyList()
    return soundsRoot.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in AudioExtensions }
        .map { relativeSoundId(soundsRoot, it, namespace) }
        .distinct()
        .sorted()
        .toList()
}

private fun relativeSoundId(root: File, file: File, namespace: String): String {
    val relative = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
    val withoutExtension = relative.substringBeforeLast('.')
    return "$namespace:$withoutExtension"
}

/** Keeps event keys to the characters a Minecraft resource path accepts. */
private fun sanitizeEventName(input: String): String =
    input.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it in "_./-" }

/** Same as [sanitizeEventName] but also allows the `:` namespace separator. */
private fun sanitizeSoundName(input: String): String =
    input.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it in "_./-:" }

private val AudioExtensions = setOf("ogg", "wav", "mp3")
