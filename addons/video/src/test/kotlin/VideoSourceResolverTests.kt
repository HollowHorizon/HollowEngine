package ru.hollowhorizon.hollowengine.addons.video.api

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoSourceResolverTests {
    @Test
    fun `relative path is resolved inside hollowengine directory`() {
        val source = Path.of("videos", "intro.mp4")

        assertEquals(
            Path.of("").toAbsolutePath().resolve("hollowengine").resolve(source).normalize().toString(),
            VideoSourceResolver.resolve(source),
        )
    }

    @Test
    fun `absolute path remains absolute`() {
        val source = Path.of("videos", "intro.mp4").toAbsolutePath().normalize()

        assertEquals(source.toString(), VideoSourceResolver.resolve(source))
    }

    @Test
    fun `network source remains unchanged`() {
        val source = "https://example.com/video.mp4"

        assertEquals(source, VideoSourceResolver.resolve(source))
    }
}
