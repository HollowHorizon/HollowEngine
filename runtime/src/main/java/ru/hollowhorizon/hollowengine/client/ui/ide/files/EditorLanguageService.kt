package ru.hollowhorizon.hollowengine.client.ui.ide.files

import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ide.*
import ru.hollowhorizon.hollowengine.common.scripting.ide.story.StoryScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.ui.HssScriptingAnalyzer

interface EditorLanguageService {
    val analyzer: ScriptingAnalyzer
}

class HollowIdeLanguageService(
    val id: String,
    private val matcher: (path: String) -> Boolean,
    private val analyzerProvider: () -> ScriptingAnalyzer,
) : EditorLanguageService {
    init {
        require(id.isNotBlank()) { "IDE language ID cannot be blank" }
    }

    override val analyzer: ScriptingAnalyzer
        get() = analyzerProvider()

    fun matches(path: String): Boolean = matcher(path)

    companion object {
        fun extensions(
            id: String,
            extensions: Collection<String>,
            analyzer: () -> ScriptingAnalyzer,
        ): HollowIdeLanguageService {
            val normalized = extensions.map { it.trim().removePrefix(".").lowercase() }
                .filter(String::isNotBlank)
                .distinct()
            require(normalized.isNotEmpty()) { "At least one language extension is required" }
            return HollowIdeLanguageService(
                id = id,
                matcher = { path -> path.fileExtension() in normalized },
                analyzerProvider = analyzer,
            )
        }
    }
}

fun EditorLanguageService(extension: String): EditorLanguageService {
    val path = "file.$extension"
    return BuiltinLanguages.firstOrNull { language -> language.matches(path) }
        ?: error("Unsupported language: $extension")
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

internal val BuiltinLanguages = listOf(
    HollowIdeLanguageService.extensions("kotlin", listOf("kt", "kts")) { KotlinEditorLanguageService.analyzer },
    HollowIdeLanguageService.extensions("java", listOf("java")) { JavaEditorLanguageService.analyzer },
    HollowIdeLanguageService.extensions("json", listOf("json")) { JsonEditorLanguageService.analyzer },
    HollowIdeLanguageService.extensions("hss", listOf("hss")) { HssEditorLanguageService.analyzer },
    HollowIdeLanguageService.extensions("story", listOf("story")) { StoryEditorLanguageService.analyzer },
)

private fun String.fileExtension(): String {
    val fileName = substringBefore('?').substringBefore('#').substringAfterLast('/')
    return fileName.substringAfterLast('.', "").lowercase()
}
