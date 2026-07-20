package ru.hollowhorizon.hollowengine.client.ui.style

import kotlin.test.Test
import kotlin.test.assertTrue

class IdeStyleTest {
    @Test
    fun `ide stylesheet with console panel compiles`() {
        val source = requireNotNull(javaClass.getResourceAsStream("/assets/hollowengine/ui/styles/ide.hss"))
            .bufferedReader()
            .use { it.readText() }

        assertTrue(compileHss(source).rules.isNotEmpty())
    }
}
