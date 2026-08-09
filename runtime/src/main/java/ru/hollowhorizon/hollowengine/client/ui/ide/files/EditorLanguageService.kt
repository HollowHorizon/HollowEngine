package ru.hollowhorizon.hollowengine.client.ui.ide.files

import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ide.*
import ru.hollowhorizon.hollowengine.common.scripting.ide.story.StoryScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.ui.HssScriptingAnalyzer

interface EditorLanguageService {
    val analyzer: ScriptingAnalyzer
}

fun EditorLanguageService(extension: String): EditorLanguageService {
    return when (extension) {
        "kt", "kts" -> KotlinEditorLanguageService
        "java" -> JavaEditorLanguageService
        "json" -> JsonEditorLanguageService
        "hss" -> HssEditorLanguageService
        "story" -> StoryEditorLanguageService
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

object HssEditorLanguageService : EditorLanguageService {
    override val analyzer: ScriptingAnalyzer
        get() = HssScriptingAnalyzer
}

object StoryEditorLanguageService : EditorLanguageService {
    override val analyzer: ScriptingAnalyzer
        get() = StoryScriptingAnalyzer
}
