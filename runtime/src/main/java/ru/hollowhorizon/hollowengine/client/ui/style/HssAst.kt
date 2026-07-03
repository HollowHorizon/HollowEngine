package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.UiState
import ru.hollowhorizon.hollowengine.client.ui.attributeValue
import ru.hollowhorizon.hollowengine.client.ui.effectiveStates
import ru.hollowhorizon.hollowengine.client.ui.hasAttribute

data class HssDocument(
    val rules: List<HssRule>,
    val keyframes: List<HssKeyframes> = emptyList(),
)

data class HssRule(
    val selectors: List<HssSelector>,
    val declarations: List<HssDeclaration>,
    val order: Int,
)

data class HssDeclaration(
    val property: String,
    val value: String,
)

data class HssKeyframes(
    val name: String,
    val frames: List<HssKeyframe>,
)

data class HssKeyframe(
    val offsets: List<Float>,
    val declarations: List<HssDeclaration>,
)

data class HssSelector(
    val type: String? = null,
    val id: String? = null,
    val tags: Set<String> = emptySet(),
    val states: Set<UiState> = emptySet(),
    val attributes: Set<HssAttributeSelector> = emptySet(),
    val ancestor: HssSelector? = null,
) {
    val specificity: Int =
        (ancestor?.specificity ?: 0) +
                (if (id != null) 100 else 0) +
                (tags.size + states.size + attributes.size) * 10 +
                if (type != null) 1 else 0

    fun matches(node: UiNode): Boolean {
        if (!matchesSelf(node)) return false
        val requiredAncestor = ancestor ?: return true
        var current = node.layoutState.parentNode
        while (current != null) {
            if (requiredAncestor.matches(current)) return true
            current = current.layoutState.parentNode
        }
        return false
    }

    private fun matchesSelf(node: UiNode): Boolean {
        if (type != null && node.type != type) return false
        if (id != null && node.id != id) return false
        if (!node.tags.containsAll(tags)) return false
        if (!node.effectiveStates().containsAll(states)) return false
        if (attributes.any { !it.matches(node) }) return false
        return true
    }
}

data class HssAttributeSelector(
    val name: String,
    val value: String? = null,
) {
    fun matches(node: UiNode): Boolean {
        if (value == null) return node.hasAttribute(name)
        return node.attributeValue(name) == value
    }
}

data class HssParseException(
    val messageText: String,
    val position: Int,
) : IllegalArgumentException("$messageText at $position")
