package ru.hollowhorizon.hollowengine.client.ui.style

import kotlin.test.Test
import kotlin.test.assertTrue

class ImageEditorStyleTest {
    @Test
    fun `image editor stylesheet compiles`() {
        val source = requireNotNull(javaClass.getResourceAsStream("/assets/hollowengine/ui/styles/image-editor.hss"))
            .bufferedReader()
            .use { it.readText() }

        assertTrue(compileHss(source).rules.isNotEmpty())
    }
}
