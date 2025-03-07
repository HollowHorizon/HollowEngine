package ru.hollowhorizon.hollowengine.client.gui.scripting

import kotlinx.serialization.Serializable
import de.fabmax.kool.modules.ui2.Colors

@Serializable
data class IdeStyle(
    @Serializable(with = ColorsSerializer::class)
    val ideColors: Colors,
    @Serializable(with = SyntaxHighlightSerializer::class)
    val syntaxHighlight: SyntaxHighlight
)
