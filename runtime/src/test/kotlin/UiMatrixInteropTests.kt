import org.joml.Vector3f
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiTransform
import ru.hollowhorizon.hollowengine.client.ui.UiVec3
import kotlin.test.assertEquals

/**
 * A UI matrix stores rows where JOML stores columns, and handing one to the other transposes it without
 * complaining: the placement survives, the translation does not, and the content lands somewhere far
 * away. Entities, items and models are placed through this conversion, so it is checked against the UI's
 * own arithmetic rather than against written-out numbers.
 */
class UiMatrixInteropTests {
    private fun assertSamePoint(matrix: UiMatrix4, x: Float, y: Float, z: Float = 0f) {
        val expected = matrix.transform(x, y, z)
        val actual = matrix.toMatrix4f().transformPosition(Vector3f(x, y, z))

        assertEquals(expected.x, actual.x, 0.0001f, "x")
        assertEquals(expected.y, actual.y, 0.0001f, "y")
        assertEquals(expected.z, actual.z, 0.0001f, "z")
    }

    @Test
    fun `a translation converts`() {
        val matrix = UiMatrix4.translation(120f, -40f, 3f)

        assertSamePoint(matrix, 0f, 0f)
        assertSamePoint(matrix, 30f, 60f, 5f)
    }

    @Test
    fun `a scale converts`() {
        assertSamePoint(UiMatrix4.scale(2f, 3f, 1f), 30f, 60f)
    }

    @Test
    fun `a placed and rotated node converts`() {
        val transform = UiTransform(
            translate = UiVec3(12f, -8f, 0f),
            rotate = UiVec3(0f, -20f, 0f),
            scale = UiVec3(1.5f, 1.5f, 1.5f),
        )
        val matrix = UiMatrix4.translation(64f, 128f, 0f) * transform.matrix(UiVec3(15f, 30f, 0f))

        assertSamePoint(matrix, 0f, 0f)
        assertSamePoint(matrix, 15f, 30f)
        assertSamePoint(matrix, 30f, 60f, 4f)
    }
}
