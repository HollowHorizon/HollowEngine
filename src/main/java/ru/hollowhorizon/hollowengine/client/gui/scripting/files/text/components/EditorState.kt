package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.CompiledFileProvider
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.EditorAnalysisState
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextEditorHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextLineProvider
import java.io.File
 
data class TextEditorConfig(
    val showLineNumbers: Boolean = true,
    val showBackground: Boolean = true,
    val showVerticalScrollbar: Boolean = true,
    val showHorizontalScrollbar: Boolean = true,
    val showSelectionAndCaret: Boolean = true,
    val singleLine: Boolean = false,
    val enableKeyMap: Boolean = true,
    val enableAutoBrackets: Boolean = true,
)

class EditorState(
    val file: File,
    val language: EditorLanguageService = EditorLanguageService(file.extension),
    val config: TextEditorConfig = TextEditorConfig(),
) {
    val provider: CompiledFileProvider = CompiledFileProvider(file, language.analyzer)

    val lines: TextLineProvider
        get() = provider

    val editor: TextEditorHandler
        get() = provider

    val analysis: EditorAnalysisState
        get() = provider.analysisState

    fun saveToDisk() {
        provider.saveToDisk()
    }

    fun dispose() {
        provider.dispose()
    }
}
