package ru.hollowhorizon.hollowengine.client.ui.ide

import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeLanguageService
import ru.hollowhorizon.hollowengine.common.addons.OwnedHollowAddonExtensions
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionSink
import ru.hollowhorizon.hollowengine.common.scripting.ide.InlayHint
import ru.hollowhorizon.hollowengine.common.scripting.ide.PlainTextScriptingAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HollowIdeExtensionApiTest {
    @Test
    fun `custom language is selected and removed with its addon scope`() {
        val scope = OwnedHollowAddonExtensions("quest-addon", javaClass.classLoader)
        val analyzer = PlainTextScriptingAnalyzer
        val language = HollowIdeLanguageService.extensions("quest", listOf("quest")) { analyzer }

        scope.register(HollowIdeExtensionPoints.LANGUAGES, "quest", language)
        assertSame(analyzer, languageServiceForPath("quests/intro.quest").analyzer)

        scope.cleanup()
        assertSame(PlainTextScriptingAnalyzer, languageServiceForPath("quests/intro.quest").analyzer)
    }

    @Test
    fun `code insight contributors are layered and disappear after cleanup`() {
        val scope = OwnedHollowAddonExtensions("quest-addon", javaClass.classLoader)
        val provider = HollowIdeAnalyzerProvider()
        val contributor = object : HollowIdeCodeInsightContributor {
            override fun supports(path: String): Boolean = path.endsWith(".quest")

            override fun completions(path: String, text: String, offset: Int): List<CompletionItem> =
                listOf(CompletionItem.Keyword("spawn", name = "spawn"))

            override fun inlays(path: String, text: String): List<HollowIdePositionedInlayHint> =
                listOf(HollowIdePositionedInlayHint(text.length, InlayHint(0, " quest")))
        }
        scope.register(HollowIdeExtensionPoints.CODE_INSIGHT, "quest", contributor)

        val matching = provider.current(PlainTextScriptingAnalyzer)
        val completions = mutableListOf<CompletionItem>()
        matching.completions("demo.quest", "spa", 3, CompletionSink { items ->
            completions += items
            true
        })
        assertEquals(listOf("spawn"), completions.map(CompletionItem::show))
        assertTrue(matching.highlight("demo.quest", "spawn", 5).single().hints.any { it.text == " quest" })

        val nonMatching = mutableListOf<CompletionItem>()
        matching.completions("demo.txt", "spa", 3, CompletionSink { items ->
            nonMatching += items
            true
        })
        assertTrue(nonMatching.isEmpty())

        scope.cleanup()
        val cleaned = provider.current(PlainTextScriptingAnalyzer)
        val afterCleanup = mutableListOf<CompletionItem>()
        cleaned.completions("demo.quest", "spa", 3, CompletionSink { items ->
            afterCleanup += items
            true
        })
        assertFalse(afterCleanup.any { it.show == "spawn" })
    }
}
