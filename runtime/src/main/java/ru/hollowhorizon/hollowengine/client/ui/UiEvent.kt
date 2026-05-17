package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.nbt.CompoundTag

enum class UiEventKind {
    CLICK,
    DRAG
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
    val released: Boolean = false,
) {
    fun read(path: String): Any? {
        val normalized = path.removePrefix("it.").removePrefix("event.")
        return when (normalized) {
            "kind" -> kind.name.lowercase()
            "node.id", "id" -> node.id.orEmpty()
            "node.type", "type" -> node.type
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
    handlers.forEach { it.handler(event) }
    return handlers.isNotEmpty()
}
