package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components

import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer

interface EditorLanguageService {
    val analyzer: ScriptingAnalyzer
}

object KotlinEditorLanguageService : EditorLanguageService {
    override val analyzer: ScriptingAnalyzer
        get() = ScriptingEnvironment.INSTANCE.analyzer
}
