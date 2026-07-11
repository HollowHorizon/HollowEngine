package ru.hollowhorizon.hollowengine.client.ui.style

const val UiCaretBlinkKeyframes = "caret-blink"

const val UiCaretBlinkPeriodMillis = 900L

object UiEngineKeyframes {
    val caretBlink: UiKeyframes = UiKeyframes(
        UiCaretBlinkKeyframes,
        listOf(
            UiKeyframe(0f, UiStylePatch().apply { opacity = 1f }),
            UiKeyframe(0.55f, UiStylePatch().apply { opacity = 1f }),
            UiKeyframe(0.56f, UiStylePatch().apply { opacity = 0f }),
            UiKeyframe(1f, UiStylePatch().apply { opacity = 0f }),
        ),
    )

    fun resolve(name: String): UiKeyframes? = when (name) {
        UiCaretBlinkKeyframes -> caretBlink
        else -> null
    }
}
