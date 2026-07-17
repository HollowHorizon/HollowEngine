package ru.hollowhorizon.hollowengine.client.gui.scripting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HollowIdeFileTypeRegistryTest {
    @Test
    fun `higher priority type wins when suffixes overlap`() {
        val registry = HollowIdeFileTypeRegistry()
        registry.register(fileType("json", listOf(".json"), priority = 10))
        registry.register(fileType("geometry", listOf(".geo.json"), priority = 20))

        assertEquals("geometry", registry.find("models/entity.geo.json", byteArrayOf())?.id)
        assertEquals("json", registry.find("data/example.json", byteArrayOf())?.id)
    }

    @Test
    fun `extensions are normalized and matched case insensitively`() {
        val registry = HollowIdeFileTypeRegistry()
        registry.register(fileType("image", listOf("png")))

        assertEquals("image", registry.find("textures/Preview.PNG", byteArrayOf())?.id)
        assertNull(registry.find("textures/Preview.PNG.backup", byteArrayOf()))
    }

    @Test
    fun `registration order resolves equal priorities deterministically`() {
        val registry = HollowIdeFileTypeRegistry()
        registry.register(fileType("first", listOf(".txt")))
        registry.register(fileType("second", listOf(".txt")))

        assertEquals("first", registry.find("notes.txt", byteArrayOf())?.id)
    }

    @Test
    fun `duplicate identifiers are rejected`() {
        val registry = HollowIdeFileTypeRegistry()
        registry.register(fileType("text", listOf(".txt")))

        assertFailsWith<IllegalArgumentException> {
            registry.register(fileType("text", listOf(".md")))
        }
    }

    @Test
    fun `binary fallback rejects control-heavy data`() {
        val registry = HollowIdeFileTypeRegistry()
        registry.register(
            HollowIdeFileType.fallback(
                id = "text",
                matcher = { _, bytes -> bytes.isProbablyText() },
                loader = { _, _ -> TestDocument },
                editor = {},
            ),
        )

        assertEquals("text", registry.find("README", "hello\nworld".toByteArray())?.id)
        assertNull(registry.find("archive.bin", ByteArray(64) { 0 }))
    }

    @Test
    fun `built in registration selects model image and text editors`() {
        val registry = HollowIdeFileTypeRegistry().apply {
            registerBuiltinFileTypes(modelEditor = {}, imageEditor = {}, textEditor = {})
        }

        assertEquals("model", registry.find("assets/demo/models/entity.geo.json", "{}".toByteArray())?.id)
        assertEquals("image", registry.find("assets/demo/textures/icon.PNG", byteArrayOf(0, 1, 2))?.id)
        assertEquals("text", registry.find("scripts/example.kts", "println(1)".toByteArray())?.id)
        assertNull(registry.find("unknown.bin", ByteArray(64) { 0 }))
    }

    private fun fileType(id: String, extensions: List<String>, priority: Int = 0) =
        HollowIdeFileType.extensions(
            id = id,
            extensions = extensions,
            priority = priority,
            loader = { _, _ -> TestDocument },
            editor = {},
        )

    private object TestDocument : HollowIdeFileDocument {
        override val readOnly: Boolean = true

        override fun encode(): ByteArray = byteArrayOf()
    }
}
