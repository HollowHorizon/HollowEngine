package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.client.ui.style.parseHssSelector
import ru.hollowhorizon.hollowengine.client.ui.widgets.CheckboxNode
import ru.hollowhorizon.hollowengine.client.ui.widgets.SliderNode
import ru.hollowhorizon.hollowengine.client.ui.widgets.TextFieldDefaultKeyInputModifier
import ru.hollowhorizon.hollowengine.client.ui.widgets.TextFieldNode
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiKeyInput
import ru.hollowhorizon.hollowengine.client.ui.widgets.handleDefaultTextFieldKeyInput

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

    val isPointerEvent: Boolean
        get() = when (this) {
            ENTER,
            EXIT,
            HOVER,
            PRESS,
            CLICK,
            RELEASE,
            DRAG,
            SCROLL -> true

            INIT,
            UPDATE,
            CLOSE,
            CHAR_TYPED,
            KEY_PRESSED,
            FOCUS,
            UNFOCUS -> false
        }

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
    val frame: HollowUiFrame? = null,
    val button: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
    val localX: Float = 0f,
    val localY: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val parentLocalX: Float = localX,
    val parentLocalY: Float = localY,
    val parentWidth: Float = 0f,
    val parentHeight: Float = 0f,
    val rootLocalX: Float = x,
    val rootLocalY: Float = y,
    val ancestorLocalPositions: Map<String, UiVec3> = emptyMap(),
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

    var changed: Boolean = false
        private set

    fun consume() {
        consumed = true
    }

    fun markChanged() {
        changed = true
    }

    fun localXInAncestor(identifier: String): Float? = ancestorLocalPositions[identifier]?.x

    fun localYInAncestor(identifier: String): Float? = ancestorLocalPositions[identifier]?.y

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
            "width" -> width
            "height" -> height
            "parentLocalX", "parent-local-x" -> parentLocalX
            "parentLocalY", "parent-local-y" -> parentLocalY
            "parentWidth", "parent-width" -> parentWidth
            "parentHeight", "parent-height" -> parentHeight
            "rootLocalX", "root-local-x" -> rootLocalX
            "rootLocalY", "root-local-y" -> rootLocalY
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
    var handled = false
    modifiers.flattenModifiers().forEach { modifier ->
        if (event.consumed) return@forEach
        when (modifier) {
            is EventModifier -> if (modifier.kind == event.kind) {
                handled = true
                if (event.kind.isPointerEvent) modifier.onPointerEvent(event) else modifier.handler(event)
            }

            is PointerInputModifierNode -> if (event.kind.isPointerEvent) {
                handled = true
                modifier.onPointerEvent(event)
            }

            is KeyInputModifier -> if (event.kind == UiEventKind.KEY_PRESSED) {
                handled = true
                if (modifier.handler(UiKeyInput(event))) event.consume()
            }

            TextFieldDefaultKeyInputModifier -> if (event.kind == UiEventKind.KEY_PRESSED && this is TextFieldNode) {
                handled = true
                if (handleDefaultTextFieldKeyInput(UiKeyInput(event))) event.consume()
            }

            else -> Unit
        }
    }
    return handled
}
