package ru.hollowhorizon.hollowengine.client.gui.scripting

import androidx.compose.runtime.Composable

internal const val BuiltinTextFileTypeId = "text"
private const val TextDetectionSampleSize = 8192

typealias HollowIdeFileEditor = @Composable (HollowIdeOpenFile) -> Unit

/** Mutable or read-only data owned by an open IDE tab. */
interface HollowIdeFileDocument : AutoCloseable {
    val readOnly: Boolean

    fun encode(): ByteArray

    fun reload(bytes: ByteArray) = Unit

    fun markSaved() = Unit

    override fun close() = Unit
}

/**
 * Complete file-type registration: matching, decoding and rendering are kept together so adding a
 * binary editor does not require changes in [HollowIdeModel] or the IDE overlay.
 */
class HollowIdeFileType(
    val id: String,
    val priority: Int = 0,
    private val matcher: (path: String, bytes: ByteArray) -> Boolean,
    private val loader: (path: String, bytes: ByteArray) -> HollowIdeFileDocument,
    val editor: HollowIdeFileEditor,
    private val pathMatcher: ((path: String) -> Boolean)? = null,
    internal val requiresContent: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "File type ID cannot be blank" }
    }

    fun matches(path: String, bytes: ByteArray): Boolean = matcher(path, bytes)

    internal fun matchesPath(path: String): Boolean? = pathMatcher?.invoke(path)

    fun open(path: String, bytes: ByteArray): HollowIdeOpenFile =
        HollowIdeOpenFile(path, this, loader(path, bytes))

    companion object {
        fun extensions(
            id: String,
            extensions: Collection<String>,
            priority: Int = 0,
            requiresContent: Boolean = true,
            loader: (path: String, bytes: ByteArray) -> HollowIdeFileDocument,
            editor: HollowIdeFileEditor,
        ): HollowIdeFileType {
            val normalized = extensions.map(String::trim).filter(String::isNotEmpty)
                .map(::normalizeExtension).distinct()
            require(normalized.isNotEmpty()) { "At least one extension is required" }
            return HollowIdeFileType(
                id = id,
                priority = priority,
                matcher = { path, _ -> normalized.any { path.endsWith(it, ignoreCase = true) } },
                loader = loader,
                editor = editor,
                pathMatcher = { path -> normalized.any { path.endsWith(it, ignoreCase = true) } },
                requiresContent = requiresContent,
            )
        }

        fun fallback(
            id: String,
            priority: Int = Int.MIN_VALUE,
            matcher: (path: String, bytes: ByteArray) -> Boolean,
            loader: (path: String, bytes: ByteArray) -> HollowIdeFileDocument,
            editor: HollowIdeFileEditor,
        ) = HollowIdeFileType(
            id = id,
            priority = priority,
            matcher = matcher,
            loader = loader,
            editor = editor,
        )
    }
}

class HollowIdeFileTypeRegistry {
    private data class Entry(val type: HollowIdeFileType, val order: Long)

    private val entries = mutableListOf<Entry>()
    private var nextOrder = 0L

    @Synchronized
    fun register(type: HollowIdeFileType) {
        require(entries.none { it.type.id == type.id }) { "File type '${type.id}' is already registered" }
        entries += Entry(type, nextOrder++)
        entries.sortWith(compareByDescending<Entry> { it.type.priority }.thenBy { it.order })
    }

    @Synchronized
    fun find(path: String, bytes: ByteArray): HollowIdeFileType? =
        entries.firstOrNull { it.type.matches(path, bytes) }?.type

    @Synchronized
    fun find(id: String): HollowIdeFileType? = entries.firstOrNull { it.type.id == id }?.type

    fun open(path: String, readContent: () -> ByteArray): HollowIdeOpenFile? {
        val types = synchronized(this) { entries.map(Entry::type) }
        var content: ByteArray? = null
        fun content(): ByteArray = content ?: readContent().also { content = it }

        for (type in types) {
            val matches = type.matchesPath(path) ?: type.matches(path, content())
            if (!matches) continue
            return type.open(path, if (type.requiresContent) content() else EmptyFileContent)
        }
        return null
    }

    @Synchronized
    fun registeredTypes(): List<HollowIdeFileType> = entries.map(Entry::type)
}

private val EmptyFileContent = ByteArray(0)

internal class HollowIdeTextDocument(
    initialText: String,
    override val readOnly: Boolean = false,
) : HollowIdeFileDocument {
    var text: String = initialText.normalizeEditorText()
        private set

    fun update(value: String): Boolean {
        if (readOnly) return false
        val normalized = value.normalizeEditorText()
        if (text == normalized) return false
        text = normalized
        return true
    }

    fun refresh(value: String) {
        text = value.normalizeEditorText()
    }

    override fun encode(): ByteArray = text.toByteArray()

    override fun reload(bytes: ByteArray) = refresh(bytes.toString(Charsets.UTF_8))
}

internal object HollowIdeReadOnlyDocument : HollowIdeFileDocument {
    override val readOnly: Boolean = true

    override fun encode(): ByteArray = ByteArray(0)
}

private fun normalizeExtension(extension: String): String =
    extension.trim().let { if (it.startsWith('.')) it else ".$it" }

private fun String.normalizeEditorText(): String = replace("\r\n", "\n").replace('\r', '\n')

internal fun ByteArray.isProbablyText(): Boolean {
    val sampleSize = minOf(size, TextDetectionSampleSize)
    if (sampleSize == 0) return true
    var nullBytes = 0
    var controlBytes = 0
    for (index in 0 until sampleSize) {
        val value = this[index].toInt() and 0xFF
        if (value == 0) nullBytes++
        if (value < 32 && value != 9 && value != 10 && value != 13) controlBytes++
    }
    return nullBytes <= sampleSize / 100 && controlBytes <= sampleSize / 10
}
