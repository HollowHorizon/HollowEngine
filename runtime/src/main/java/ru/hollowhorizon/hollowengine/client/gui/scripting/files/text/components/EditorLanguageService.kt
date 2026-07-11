package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components

import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ide.JavaScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.JsonScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.PlainTextScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.UnavailableKotlinScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.ui.HssScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.ui.UiXmlScriptingAnalyzer

interface EditorLanguageService {
    val analyzer: ScriptingAnalyzer
}

fun EditorLanguageService(extension: String): EditorLanguageService {
    return when (extension) {
        "kt", "kts" -> KotlinEditorLanguageService
        "java" -> JavaEditorLanguageService
        "json" -> JsonEditorLanguageService
        "ui" -> UiXmlEditorLanguageService
        "hss" -> HssEditorLanguageService
        else -> error("Unsupported language: $extension")
    }
}

object KotlinEditorLanguageService : EditorLanguageService {
    override val analyzer: ScriptingAnalyzer
        get() = ScriptingEnvironment.currentOrNull()?.analyzer ?: UnavailableKotlinScriptingAnalyzer
}

object JavaEditorLanguageService : EditorLanguageService {
    override val analyzer: ScriptingAnalyzer
        get() = JavaScriptingAnalyzer
}

object PlainEditorLanguageService : EditorLanguageService {
    override val analyzer: ScriptingAnalyzer
        get() = PlainTextScriptingAnalyzer
}

object JsonEditorLanguageService : EditorLanguageService {
    override val analyzer: ScriptingAnalyzer
        get() = JsonScriptingAnalyzer
}

object UiXmlEditorLanguageService : EditorLanguageService {
    override val analyzer: ScriptingAnalyzer
        get() = UiXmlScriptingAnalyzer
}

object HssEditorLanguageService : EditorLanguageService {
    override val analyzer: ScriptingAnalyzer
        get() = HssScriptingAnalyzer
}
