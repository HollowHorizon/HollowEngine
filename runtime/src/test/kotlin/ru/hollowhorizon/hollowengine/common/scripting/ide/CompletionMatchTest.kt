package ru.hollowhorizon.hollowengine.common.scripting.ide

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompletionMatchTest {
    @Test
    fun `camel hump input matches separated name parts`() {
        val match = assertNotNull(matchCompletion("HollEng", "HollowEngine"))

        assertEquals(listOf(0..3, 6..8), match.ranges)
    }

    @Test
    fun `characters must stay in source order`() {
        assertNull(matchCompletion("EngineHollow", "HollowEngine"))
    }

    @Test
    fun `a compact prefix ranks ahead of a longer prefix`() {
        val short = assertNotNull(matchCompletion("Minecr", "Minecraft"))
        val long = assertNotNull(matchCompletion("Minecr", "MinecraftHssResourceLoader"))

        assertTrue(short.score < long.score)
    }

    @Test
    fun `prefix ranks ahead of a subsequence`() {
        val prefix = assertNotNull(matchCompletion("Core", "CoreLoader"))
        val subsequence = assertNotNull(matchCompletion("Core", "HollowCore"))

        assertTrue(prefix.score < subsequence.score)
    }
}
