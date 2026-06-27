package ru.hollowhorizon.hollowengine.client.ui.scripting

import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.UiState


class UiScriptElement(private val node: UiNode) {
    val id: String get() = node.id.orEmpty()
    val type: String get() = node.type
    val tags: List<String> get() = node.tags.toList()
    fun attribute(name: String): String = node.attributes[name].orEmpty()

    fun modify(attribute: String, value: String): UiScriptElement {
        node.attributes[attribute] = value
        return this
    }

    fun removeAttribute(attribute: String): UiScriptElement {
        node.attributes.remove(attribute)
        return this
    }

    var enabled: Boolean
        get() = UiState.DISABLED !in node.states
        set(value) {
            if (value) node.states -= UiState.DISABLED else node.states += UiState.DISABLED
        }
}
