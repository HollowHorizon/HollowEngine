package ru.hollowhorizon.hollowengine.client.ui.hss

import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.UiState

data class HssDocument(val rules: List<HssRule>)

data class HssRule(
    val selectors: List<HssSelector>,
    val declarations: List<HssDeclaration>,
    val order: Int,
)

data class HssDeclaration(
    val property: String,
    val value: String,
)

data class HssSelector(
    val type: String? = null,
    val id: String? = null,
    val tags: Set<String> = emptySet(),
    val states: Set<UiState> = emptySet(),
) {
    val specificity: Int =
        (if (id != null) 100 else 0) + tags.size * 10 + states.size * 10 + (if (type != null) 1 else 0)

    fun matches(node: UiNode): Boolean {
        if (type != null && node.type != type) return false
        if (id != null && node.id != id) return false
        if (!node.tags.containsAll(tags)) return false
        if (!node.states.containsAll(states)) return false
        return true
    }
}

data class HssParseException(
    val messageText: String,
    val position: Int,
) : IllegalArgumentException("$messageText at $position")
