package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components

import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ide.JsonScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariScriptingAnalyzer

interface EditorLanguageService {
    val analyzer: ScriptingAnalyzer
}

fun EditorLanguageService(extension: String): EditorLanguageService {
    return when (extension) {
        "kt", "kts" -> KotlinEditorLanguageService
        "ktr" -> KatariEditorLanguageService
        "json" -> JsonEditorLanguageService
        else -> error("Unsupported language: $extension")
    }
}

object KotlinEditorLanguageService : EditorLanguageService {
    override val analyzer: ScriptingAnalyzer
        get() = ScriptingEnvironment.INSTANCE.analyzer
}

object JsonEditorLanguageService : EditorLanguageService {
    override val analyzer: ScriptingAnalyzer
        get() = JsonScriptingAnalyzer
}

object KatariEditorLanguageService : EditorLanguageService {
    override val analyzer: ScriptingAnalyzer
        get() = KatariScriptingAnalyzer
}
