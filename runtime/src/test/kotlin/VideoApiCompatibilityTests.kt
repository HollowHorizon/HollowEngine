
import ru.hollowhorizon.hollowengine.api.VideoApi
import ru.hollowhorizon.hollowengine.api.VideoPlaybackOptions
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoApiCompatibilityTests {
    @Test
    fun `string sources fall back to the original path contract`() {
        var receivedPath: Path? = null
        val api = object : VideoApi {
            override fun play(path: Path, options: VideoPlaybackOptions) {
                receivedPath = path
            }
        }

        api.play("movies/intro.mp4")

        assertEquals(Path.of("movies/intro.mp4"), receivedPath)
    }
}
