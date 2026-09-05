package ru.hollowhorizon.hollowengine.client.ui.ide

import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeLanguageService
import ru.hollowhorizon.hollowengine.common.addons.OwnedHollowAddonExtensions
import ru.hollowhorizon.hollowengine.common.scripting.ide.JsonScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.PlainTextScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import kotlin.test.Test
import kotlin.test.assertSame

class HollowIdeExtensionApiTest {
    @Test
    fun `custom language is selected and removed with its addon scope`() {
        val scope = OwnedHollowAddonExtensions("quest-addon", javaClass.classLoader)
        val analyzer = PlainTextScriptingAnalyzer
        val language = HollowIdeLanguageService.extensions("quest", listOf("quest")) { analyzer }

        try {
            scope.registerIdeLanguage(language)
            assertSame(analyzer, languageServiceForPath("quests/intro.quest").analyzer)
        } finally {
            scope.cleanup()
        }

        assertSame(PlainTextScriptingAnalyzer, languageServiceForPath("quests/intro.quest").analyzer)
    }

    @Test
    fun `addon language overrides builtin and cleanup restores it`() {
        val scope = OwnedHollowAddonExtensions("json-addon", javaClass.classLoader)
        val analyzer = distinctAnalyzer()
        val language = HollowIdeLanguageService.extensions("custom-json", listOf("json")) { analyzer }

        try {
            scope.registerIdeLanguage(language)
            assertSame(analyzer, languageServiceForPath("data/example.json").analyzer)
        } finally {
            scope.cleanup()
        }

        assertSame(JsonScriptingAnalyzer, languageServiceForPath("data/example.json").analyzer)
    }

    @Test
    fun `higher priority language wins and cleanup restores previous match`() {
        val lowerScope = OwnedHollowAddonExtensions("lower-addon", javaClass.classLoader)
        val higherScope = OwnedHollowAddonExtensions("higher-addon", javaClass.classLoader)
        val lowerAnalyzer = distinctAnalyzer()
        val higherAnalyzer = distinctAnalyzer()
        val lower = HollowIdeLanguageService.extensions("quest", listOf("quest")) { lowerAnalyzer }
        val higher = HollowIdeLanguageService.extensions("quest", listOf("quest")) { higherAnalyzer }

        try {
            lowerScope.registerIdeLanguage(lower, priority = 10)
            higherScope.registerIdeLanguage(higher, priority = 20)
            assertSame(higherAnalyzer, languageServiceForPath("quests/intro.quest").analyzer)

            higherScope.cleanup()
            assertSame(lowerAnalyzer, languageServiceForPath("quests/intro.quest").analyzer)
        } finally {
            higherScope.cleanup()
            lowerScope.cleanup()
        }
    }

    private fun distinctAnalyzer(): ScriptingAnalyzer = object : ScriptingAnalyzer by PlainTextScriptingAnalyzer {}
}
