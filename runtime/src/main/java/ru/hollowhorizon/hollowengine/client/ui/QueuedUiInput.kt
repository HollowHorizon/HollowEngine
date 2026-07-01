package ru.hollowhorizon.hollowengine.client.ui

internal sealed interface QueuedUiInput {
    data class MouseClicked(val mouseX: Float, val mouseY: Float, val button: Int) : QueuedUiInput
    data class MouseReleased(val mouseX: Float, val mouseY: Float, val button: Int) : QueuedUiInput
    data class MouseDragged(
        val mouseX: Float,
        val mouseY: Float,
        val button: Int,
        val dragX: Float,
        val dragY: Float,
    ) : QueuedUiInput

    data class MouseScrolled(val mouseX: Float, val mouseY: Float, val scrollX: Float, val scrollY: Float) : QueuedUiInput
    data class CharTyped(val codePoint: Char, val modifiers: Int) : QueuedUiInput
    data class KeyPressed(val keyCode: Int, val scanCode: Int, val modifiers: Int) : QueuedUiInput
}
