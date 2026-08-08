package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

import ru.hollowhorizon.hollowengine.client.ui.UiBuiltInElementTypes
import ru.hollowhorizon.hollowengine.client.ui.UiState
import ru.hollowhorizon.hollowengine.client.ui.style.HssProperty
import ru.hollowhorizon.hollowengine.client.ui.style.HssSchema
import ru.hollowhorizon.hollowengine.client.ui.style.HssSlot
import ru.hollowhorizon.hollowengine.client.ui.style.HssValueKind

/**
 * The IDE's view of the HSS language.
 */
object UiLanguageCatalog {
    val elementTypes: List<String> get() = UiBuiltInElementTypes

    val states: List<String> get() = UiState.builtIns.map { it.name }

    fun property(name: String): HssProperty? = HssSchema.find(name)

    /**
     * Values offered for the slot the caret sits on. Whole-value examples come first while
     * the value is still empty, so `margin: ` immediately suggests a usable shorthand.
     */
    fun valueCompletions(
        property: String,
        value: String,
        offset: Int,
        keyframes: Collection<String> = emptyList(),
    ): List<String> {
        val declaration = property(property) ?: return emptyList()
        val slot = declaration.slotAt(value, offset)
        val slotValues = slot?.let { candidatesFor(it, keyframes) }.orEmpty()
        val atStart = value.take(offset.coerceIn(0, value.length)).isBlank()
        val examples = if (atStart) declaration.examples else emptyList()
        return (examples + slotValues).distinct()
    }

    private fun candidatesFor(slot: HssSlot, keyframes: Collection<String>): List<String> = when (slot.kind) {
        HssValueKind.KEYFRAMES -> slot.keywords + keyframes
        HssValueKind.STYLE_PROPERTY -> slot.keywords + HssSchema.transitionTargets
        else -> slot.candidates()
    }
}
