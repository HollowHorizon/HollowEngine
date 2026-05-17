package ru.hollowhorizon.hollowengine.client.ui.xml

data class UiMarkupDocument(
    val nodes: List<UiMarkupNode>,
)

sealed interface UiMarkupNode

data class UiMarkupElement(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<UiMarkupNode> = emptyList(),
) : UiMarkupNode

data class UiMarkupText(
    val value: String,
) : UiMarkupNode

class UiMarkupParseException(
    val messageText: String,
    val position: Int,
) : IllegalArgumentException("$messageText at $position")
