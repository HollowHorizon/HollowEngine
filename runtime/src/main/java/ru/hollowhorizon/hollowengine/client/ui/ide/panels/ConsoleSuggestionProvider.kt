package ru.hollowhorizon.hollowengine.client.ui.ide.panels

import com.mojang.brigadier.suggestion.Suggestion
import net.minecraft.client.Minecraft

object ConsoleSuggestionProvider {
    fun suggest(text: String, cursor: Int): List<Suggestion> {
        val connection = Minecraft.getInstance().connection ?: return emptyList()
        val dispatcher = connection.commands
        val parseResult = dispatcher.parse(text, connection.suggestionsProvider)
        val suggestions = dispatcher.getCompletionSuggestions(parseResult, cursor.coerceAtLeast(0)).get()
        return suggestions.list
    }
}