package ru.hollowhorizon.hollowengine.client.ui.ide.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeFileDocument
import ru.hollowhorizon.hollowengine.common.vfx.VfxEffect
import ru.hollowhorizon.hollowengine.common.vfx.VfxFormat

/**
 * An open `.vfx` file: one [VfxEffect] the editor rewrites whole.
 */
class HollowIdeVfxDocument(bytes: ByteArray) : HollowIdeFileDocument {
    var effect by mutableStateOf(VfxEffect.EMPTY)
        private set

    /** What went wrong reading the file, or null when it read fine. */
    var error by mutableStateOf<String?>(null)
        private set

    private var original: String = ""

    override val readOnly: Boolean get() = error != null

    var isModified by mutableStateOf(false)
        private set

    var revision by mutableStateOf(0)
        private set

    private var editorState: Any? = null

    init {
        load(bytes)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> editorState(create: () -> T): T = (editorState as? T) ?: create().also { editorState = it }

    /** Effects to go back to, newest last; the timeline part is not in them, it has its own history. */
    private val undoStack = ArrayDeque<VfxEffect>()
    private val redoStack = ArrayDeque<VfxEffect>()

    /** The effect as it was when a gesture started; the whole gesture becomes one step back. */
    private var gestureStart: VfxEffect? = null
    private var lastMergeKey: String? = null
    private var lastEditNanos = 0L

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /**
     * Replaces the effect with what [change] makes of it.
     */
    fun edit(mergeKey: String? = null, history: Boolean = true, change: (VfxEffect) -> VfxEffect) {
        if (readOnly) return

        val next = change(effect)
        if (next == effect) return

        if (history && gestureStart == null) {
            val now = System.nanoTime()
            val merges = mergeKey != null && mergeKey == lastMergeKey && now - lastEditNanos < MERGE_WINDOW_NANOS
            if (!merges) remember(effect)
            lastMergeKey = mergeKey
            lastEditNanos = now
        }
        apply(next)
    }

    /** Starts a gesture, such as dragging a handle, that should go back in one step. */
    fun beginGesture() {
        if (gestureStart == null) gestureStart = effect
    }

    fun endGesture() {
        val start = gestureStart ?: return
        gestureStart = null
        lastMergeKey = null
        if (start != effect) remember(start)
    }

    fun undo(): Boolean {
        val previous = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(effect)
        lastMergeKey = null
        apply(previous.copy(timeline = effect.timeline))
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(effect)
        lastMergeKey = null
        apply(next.copy(timeline = effect.timeline))
        return true
    }

    private fun remember(state: VfxEffect) {
        undoStack.addLast(state)
        while (undoStack.size > HISTORY_LIMIT) undoStack.removeFirst()
        redoStack.clear()
        refreshHistoryState()
    }

    private fun apply(next: VfxEffect) {
        effect = next
        isModified = true
        revision++
        refreshHistoryState()
    }

    private fun refreshHistoryState() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    override fun encode(): ByteArray = if (readOnly) original.toByteArray() else VfxFormat.write(effect).toByteArray()

    override fun reload(bytes: ByteArray) {
        if (!readOnly && bytes.toString(Charsets.UTF_8) == VfxFormat.write(effect)) return
        undoStack.clear()
        redoStack.clear()
        refreshHistoryState()
        load(bytes)
        isModified = false
        revision++
    }

    override fun markSaved() {
        isModified = false
    }

    private fun load(bytes: ByteArray) {
        original = bytes.toString(Charsets.UTF_8)
        try {
            effect = VfxFormat.read(original)
            error = null
        } catch (e: Exception) {
            effect = VfxEffect.EMPTY
            error = e.message ?: e::class.simpleName
            HollowEngine.LOGGER.warn("Could not read effect: {}", error)
        }
    }

    private companion object {
        const val HISTORY_LIMIT = 100
        const val MERGE_WINDOW_NANOS = 700_000_000L
    }
}
