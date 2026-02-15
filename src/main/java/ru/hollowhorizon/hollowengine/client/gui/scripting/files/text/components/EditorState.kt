package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.CompiledFileProvider
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.EditorAnalysisState
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextEditorHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextLineProvider
import java.io.File

class EditorState(
    val file: File,
    val language: EditorLanguageService = KotlinEditorLanguageService,
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
