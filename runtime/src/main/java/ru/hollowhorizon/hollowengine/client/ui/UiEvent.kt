package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.client.ui.hss.parseHssSelector

enum class UiEventKind {
    INIT,
    UPDATE,
    CLOSE,
    ENTER,
    EXIT,
    HOVER,
    PRESS,
    CLICK,
    RELEASE,
    DRAG,
    SCROLL,
    CHAR_TYPED,
    KEY_PRESSED,
    FOCUS,
    UNFOCUS;

    val attributeName: String
        get() = name.lowercase().split('_').joinToString("-")

    companion object {
        fun fromAttribute(name: String): UiEventKind? {
            val dashed = name.replace(Regex("([a-z])([A-Z])"), "$1-$2")
                .replace('_', '-')
                .lowercase()
            val normalized = when {
                dashed.startsWith("on-") -> dashed.removePrefix("on-")
                dashed.startsWith("on") -> dashed.removePrefix("on")
                else -> dashed
            }
            val aliased = when (normalized) {
                "pressed" -> "press"
                "released" -> "release"
                "char" -> "char-typed"
                "key" -> "key-pressed"
                "un-focus" -> "unfocus"
                else -> normalized
            }
            return entries.firstOrNull { it.attributeName == aliased }
        }
    }
}

data class UiEvent(
    val kind: UiEventKind,
    val node: UiNode,
    val button: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
    val localX: Float = 0f,
    val localY: Float = 0f,
    val deltaX: Float = 0f,
    val deltaY: Float = 0f,
    val scrollX: Float = 0f,
    val scrollY: Float = 0f,
    val key: Int = 0,
    val scanCode: Int = 0,
    val modifiers: Int = 0,
    val codePoint: Int = 0,
    val released: Boolean = false,
) {
    var consumed: Boolean = false
        private set

    var variables: CompoundTag = CompoundTag()

    fun consume() {
        consumed = true
    }

    fun read(path: String): Any? {
        val normalized = path.removePrefix("it.").removePrefix("event.")
        return when (normalized) {
            "kind" -> kind.name.lowercase()
            "node.id", "id" -> node.id.orEmpty()
            "node.type", "type" -> node.type
            "node.value", "value" -> node.readWidgetValue()
            "node.checked", "checked" -> (node as? CheckboxNode)?.checked
            "node.text", "text" -> (node as? TextFieldNode)?.value ?: (node as? TextNode)?.text?.template
            "button" -> button
            "x" -> x
            "y" -> y
            "localX", "local-x" -> localX
            "localY", "local-y" -> localY
            "deltaX", "delta-x" -> deltaX
            "deltaY", "delta-y" -> deltaY
            "isReleased", "released" -> released
            else -> null
        }
    }

    fun matches(selector: String): Boolean {
        val clean = selector.trim()
        if (clean.isBlank()) return true
        return runCatching { parseHssSelector(clean).matches(node) }.getOrDefault(false) || when {
            clean.startsWith("#") -> node.id == clean.removePrefix("#")
            clean.startsWith(".") -> clean.removePrefix(".") in node.tags
            clean.any { it in "[:#" } -> false
            else -> node.type == clean || node.id == clean || clean in node.tags
        }
    }
}

private fun UiNode.readWidgetValue(): Any? = when (this) {
    is SliderNode -> value
    is CheckboxNode -> checked
    is TextFieldNode -> value
    is TextNode -> text.template
    else -> attributes["value"]
}

fun interface UiEventSink {
    fun emit(payload: CompoundTag)

    companion object {
        val None = UiEventSink {}
    }
}

fun UiNode.dispatch(event: UiEvent): Boolean {
    val handlers = modifiers.flattenModifiers()
        .filterIsInstance<EventModifier>()
        .filter { it.kind == event.kind }
    handlers.forEach {
        if (!event.consumed) it.handler(event)
    }
    return handlers.isNotEmpty()
}
