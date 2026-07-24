package ru.hollowhorizon.hollowengine.client.gui.scripting

import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeSoundsDocument
import ru.hollowhorizon.hollowengine.client.ui.ide.files.SoundEntry
import ru.hollowhorizon.hollowengine.client.ui.ide.files.SoundEntryType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HollowIdeSoundsDocumentTest {
    @Test
    fun `parses shorthand and full sound entries`() {
        val document = HollowIdeSoundsDocument(SAMPLE.toByteArray())

        assertEquals(2, document.events.size)
        val first = document.events[0]
        assertEquals("block.custom.break", first.name)
        assertEquals("subtitles.block.custom.break", first.subtitle)
        assertEquals(2, first.sounds.size)
        assertTrue(first.sounds[0].isDefaultExceptName)

        val full = first.sounds[1]
        assertEquals(0.5f, full.volume)
        assertEquals(1.2f, full.pitch)
        assertEquals(3, full.weight)
        assertTrue(full.stream)
        assertEquals(8, full.attenuationDistance)
        assertTrue(full.preload)
        assertEquals(SoundEntryType.EVENT, full.type)

        assertTrue(document.events[1].replace)
    }

    @Test
    fun `encode round-trips through a re-parse`() {
        val once = HollowIdeSoundsDocument(SAMPLE.toByteArray()).encode()
        val twice = HollowIdeSoundsDocument(once).encode()
        assertEquals(once.decodeToString(), twice.decodeToString())
    }

    @Test
    fun `blank content yields no events`() {
        assertEquals(0, HollowIdeSoundsDocument("{\n}\n".toByteArray()).events.size)
        assertEquals(0, HollowIdeSoundsDocument(ByteArray(0)).events.size)
    }

    @Test
    fun `defaults are omitted and shorthand is used`() {
        val document = HollowIdeSoundsDocument(ByteArray(0))
        val event = document.addEvent("test.event")
        event.sounds += SoundEntry(name = "modid:test")

        val text = document.encode().decodeToString()
        assertTrue(text.contains("\"modid:test\""), "expected shorthand string entry")
        assertFalse(text.contains("volume"), "default volume must be omitted")
        assertFalse(text.contains("\"replace\""), "default replace must be omitted")
    }

    private companion object {
        val SAMPLE = """
            {
              "block.custom.break": {
                "subtitle": "subtitles.block.custom.break",
                "sounds": [
                  "modid:block/custom1",
                  {
                    "name": "modid:block/custom2",
                    "volume": 0.5,
                    "pitch": 1.2,
                    "weight": 3,
                    "stream": true,
                    "attenuation_distance": 8,
                    "preload": true,
                    "type": "event"
                  }
                ]
              },
              "ambient.custom": {
                "replace": true,
                "sounds": [
                  "modid:ambient/one"
                ]
              }
            }
        """.trimIndent()
    }
}
