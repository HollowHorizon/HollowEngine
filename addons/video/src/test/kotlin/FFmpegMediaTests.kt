import ru.hollowhorizon.hollowengine.addons.video.decode.FFmpegMedia
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FFmpegMediaTests {
    @Test
    fun `bundled ffmpeg opens and inspects a local video frame`() {
        val directory = Files.createTempDirectory("hollowengine-video-test")
        val image = directory.resolve("frame.ppm")
        try {
            Files.write(image, ppmImage())
            val info = FFmpegMedia.readInfo(image.toString())

            assertEquals(2, info.width)
            assertEquals(2, info.height)
            assertFalse(info.hasAudio)
        } finally {
            Files.deleteIfExists(image)
            Files.deleteIfExists(directory)
        }
    }

    private fun ppmImage(): ByteArray {
        val header = "P6\n2 2\n255\n".encodeToByteArray()
        val pixels = byteArrayOf(
            -1, 0, 0,
            0, -1, 0,
            0, 0, -1,
            -1, -1, -1,
        )
        return header + pixels
    }
}
