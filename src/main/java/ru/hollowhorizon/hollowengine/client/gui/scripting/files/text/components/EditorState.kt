package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components

import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.CompiledFileProvider
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.EditorAnalysisState
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextEditorHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextLineProvider
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment

data class TextEditorConfig(
    var showLineNumbers: Boolean = true,
    var showBackground: Boolean = true,
    var showVerticalScrollbar: Boolean = true,
    var showHorizontalScrollbar: Boolean = true,
    var showSelectionAndCaret: Boolean = true,
    var singleLine: Boolean = false,
    var enableKeyMap: Boolean = true,
    var enableAutoBrackets: Boolean = true,
    var fontSize: Float = Dimensions.FontNormal,
    var font: MsdfFont = MsdfFont(ColorTheme.Fonts.MONOCRAFT, Dimensions.FontNormal),
    var indentSize: Int = 4,
)

interface TextSource {
    val name: String
    val text: String
    fun save(text: String) {}

    class File(val file: java.io.File) : TextSource {
        override val name: String
            get() = file.name
        override val text: String
            get() = file.readText().replace("\r\n", "\n")

        override fun save(text: String) {
            file.writeText(text)
        }
    }

    class Memory(override val name: String, override val text: String) : TextSource
}

val TextSource.extension get() = name.substringAfterLast('.')

class EditorState(
    val source: TextSource,
    val language: EditorLanguageService = EditorLanguageService(source.extension),
    val config: TextEditorConfig = TextEditorConfig(),
) {
    val provider: CompiledFileProvider = CompiledFileProvider(source, this, ScriptingEnvironment.INSTANCE.analyzer)

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
