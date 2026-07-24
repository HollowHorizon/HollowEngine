package ru.hollowhorizon.hollowengine.client.ui.ide.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeFileDocument

internal class HollowIdeSoundsDocument(bytes: ByteArray) : HollowIdeFileDocument {
    override val readOnly: Boolean = false

    val events: SnapshotStateList<SoundEvent> = mutableStateListOf()

    /** Bumped on every edit; the editor keys its debounced auto-save on this value. */
    var revision by mutableStateOf(0)
        private set
    private var savedRevision = 0

    val isModified: Boolean get() = revision != savedRevision

    init {
        events += parse(bytes)
    }

    /** Records that the in-memory model changed. Call after any structural or field edit. */
    fun touch() {
        revision++
    }

    fun addEvent(name: String = "new_event"): SoundEvent {
        val event = SoundEvent(name = uniqueEventName(name))
        events += event
        touch()
        return event
    }

    fun removeEvent(event: SoundEvent) {
        if (events.remove(event)) touch()
    }

    override fun encode(): ByteArray = serialize().toByteArray()

    override fun reload(bytes: ByteArray) {
        events.clear()
        events += parse(bytes)
        revision++
        savedRevision = revision
    }

    override fun markSaved() {
        savedRevision = revision
    }

    private fun uniqueEventName(base: String): String {
        if (events.none { it.name == base }) return base
        var index = 1
        while (events.any { it.name == "$base.$index" }) index++
        return "$base.$index"
    }

    private fun serialize(): String {
        val root = JsonObject()
        for (event in events) {
            val name = event.name.trim()
            if (name.isEmpty() || root.has(name)) continue
            val obj = JsonObject()
            if (event.replace) obj.addProperty("replace", true)
            if (event.subtitle.isNotBlank()) obj.addProperty("subtitle", event.subtitle.trim())
            val sounds = JsonArray()
            for (sound in event.sounds) {
                serializeSound(sound)?.let(sounds::add)
            }
            obj.add("sounds", sounds)
            root.add(name, obj)
        }
        return GSON.toJson(root) + "\n"
    }

    private fun serializeSound(sound: SoundEntry): JsonElement? {
        val name = sound.name.trim()
        if (name.isEmpty()) return null
        if (sound.isDefaultExceptName) return JsonPrimitive(name)
        val obj = JsonObject()
        obj.addProperty("name", name)
        if (sound.volume != 1f) obj.addProperty("volume", sound.volume)
        if (sound.pitch != 1f) obj.addProperty("pitch", sound.pitch)
        if (sound.weight != 1) obj.addProperty("weight", sound.weight)
        if (sound.stream) obj.addProperty("stream", true)
        if (sound.attenuationDistance != 16) obj.addProperty("attenuation_distance", sound.attenuationDistance)
        if (sound.preload) obj.addProperty("preload", true)
        if (sound.type != SoundEntryType.SOUND) obj.addProperty("type", sound.type.jsonName)
        return obj
    }

    private fun parse(bytes: ByteArray): List<SoundEvent> {
        val text = bytes.toString(Charsets.UTF_8).trim()
        if (text.isEmpty()) return emptyList()
        val root = runCatching { JsonParser.parseString(text) }.getOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return emptyList()

        val result = mutableListOf<SoundEvent>()
        for ((key, value) in root.entrySet()) {
            if (!value.isJsonObject) continue
            val obj = value.asJsonObject
            val event = SoundEvent(
                name = key,
                replace = obj.boolean("replace") ?: false,
                subtitle = obj.string("subtitle") ?: "",
            )
            obj.get("sounds")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
                parseSound(element)?.let { event.sounds += it }
            }
            result += event
        }
        return result
    }

    private fun parseSound(element: JsonElement): SoundEntry? = when {
        element.isJsonPrimitive -> SoundEntry(name = element.asString)
        element.isJsonObject -> element.asJsonObject.let { obj ->
            SoundEntry(
                name = obj.string("name") ?: "",
                volume = obj.float("volume") ?: 1f,
                pitch = obj.float("pitch") ?: 1f,
                weight = obj.int("weight") ?: 1,
                stream = obj.boolean("stream") ?: false,
                attenuationDistance = obj.int("attenuation_distance") ?: 16,
                preload = obj.boolean("preload") ?: false,
                type = if (obj.string("type") == "event") SoundEntryType.EVENT else SoundEntryType.SOUND,
            )
        }

        else -> null
    }

    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        private fun JsonObject.member(key: String): JsonElement? =
            get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }

        private fun JsonObject.string(key: String): String? = member(key)?.asString

        private fun JsonObject.boolean(key: String): Boolean? =
            member(key)?.let { runCatching { it.asBoolean }.getOrNull() }

        private fun JsonObject.int(key: String): Int? =
            member(key)?.let { runCatching { it.asInt }.getOrNull() }

        private fun JsonObject.float(key: String): Float? =
            member(key)?.let { runCatching { it.asFloat }.getOrNull() }
    }
}

internal enum class SoundEntryType(val jsonName: String) {
    SOUND("sound"),
    EVENT("event"),
}

/** A single sound event (top-level key in `sounds.json`). */
internal class SoundEvent(
    name: String = "",
    replace: Boolean = false,
    subtitle: String = "",
) {
    var name by mutableStateOf(name)
    var replace by mutableStateOf(replace)
    var subtitle by mutableStateOf(subtitle)
    val sounds: SnapshotStateList<SoundEntry> = mutableStateListOf()
}

/** A single entry inside an event's `sounds` array. */
internal class SoundEntry(
    name: String = "",
    volume: Float = 1f,
    pitch: Float = 1f,
    weight: Int = 1,
    stream: Boolean = false,
    attenuationDistance: Int = 16,
    preload: Boolean = false,
    type: SoundEntryType = SoundEntryType.SOUND,
) {
    var name by mutableStateOf(name)
    var volume by mutableStateOf(volume)
    var pitch by mutableStateOf(pitch)
    var weight by mutableStateOf(weight)
    var stream by mutableStateOf(stream)
    var attenuationDistance by mutableStateOf(attenuationDistance)
    var preload by mutableStateOf(preload)
    var type by mutableStateOf(type)

    /** True when only [name] carries information, so the entry can be written as a bare string. */
    val isDefaultExceptName: Boolean
        get() = volume == 1f && pitch == 1f && weight == 1 && !stream &&
            attenuationDistance == 16 && !preload && type == SoundEntryType.SOUND
}
