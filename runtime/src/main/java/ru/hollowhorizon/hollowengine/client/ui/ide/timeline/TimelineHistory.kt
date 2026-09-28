package ru.hollowhorizon.hollowengine.client.ui.ide.timeline

internal data class KeyframeState(
    val time: Float,
    val value: Float,
    val interpolation: KeyInterpolation,
    val handleMode: HandleMode,
    val incoming: KeyTangent,
    val outgoing: KeyTangent,
) {
    fun toKeyframe() = Keyframe(time, value, interpolation, handleMode, incoming, outgoing)

    companion object {
        fun of(keyframe: Keyframe) = KeyframeState(
            time = keyframe.time,
            value = keyframe.value,
            interpolation = keyframe.interpolation,
            handleMode = keyframe.handleMode,
            incoming = keyframe.incoming,
            outgoing = keyframe.outgoing,
        )
    }
}

internal data class PropertyState(
    val name: String,
    val visible: Boolean,
    val locked: Boolean,
) {
    fun applyTo(property: AnimProperty<*>) {
        property.nameState = name
        property.isVisible = visible
        property.isLocked = locked
    }

    companion object {
        fun of(property: AnimProperty<*>) = PropertyState(
            name = property.nameState,
            visible = property.isVisible,
            locked = property.isLocked,
        )
    }
}

internal data class PropertySnapshot(
    val property: AnimProperty<*>,
    val type: PropertyType<*>,
    val state: PropertyState,
    val curves: List<ChannelCurve>,
    val keys: List<List<KeyframeState>>,
)

internal data class TimelineSnapshot(
    val properties: List<PropertySnapshot>,
    val currentTime: Float,
    val workAreaEnd: Float,
    val extra: Any? = null,
)

class TimelineHistory(private val controller: TimelineController) {
    private val undoStack = ArrayDeque<TimelineSnapshot>()
    private val redoStack = ArrayDeque<TimelineSnapshot>()
    private var transactionStart: TimelineSnapshot? = null

    fun begin(label: String) {
        if (transactionStart == null) {
            transactionStart = controller.createSnapshot()
        }
    }

    fun commit() {
        val before = transactionStart ?: return
        transactionStart = null
        val after = controller.createSnapshot()
        if (before != after) {
            undoStack += before
            redoStack.clear()
        }
    }

    fun <T> record(label: String, block: () -> T): T {
        if (transactionStart != null) {
            return block()
        }
        val before = controller.createSnapshot()
        val result = block()
        val after = controller.createSnapshot()
        if (before != after) {
            undoStack += before
            redoStack.clear()
        }
        return result
    }

    fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        redoStack += controller.createSnapshot()
        controller.restoreSnapshot(snapshot)
    }

    fun redo() {
        val snapshot = redoStack.removeLastOrNull() ?: return
        undoStack += controller.createSnapshot()
        controller.restoreSnapshot(snapshot)
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        transactionStart = null
    }
}
