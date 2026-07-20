import org.joml.Matrix3f
import org.joml.Matrix4f
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.models.internal.Material
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.InstancedShaderLayoutMode
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.SubmittedInstance
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.normalFor
import kotlin.test.assertSame

class InstancingContractTests {
    @Test
    fun `vanilla and runtime shaders receive normals in their expected spaces`() {
        val modelNormal = Matrix3f().scaling(2f)
        val modelViewNormal = Matrix3f().scaling(3f)
        val instance = SubmittedInstance(
            modelView = Matrix4f(),
            modelNormal = modelNormal,
            modelViewNormal = modelViewNormal,
            overlay = 0,
            light = 0,
            sortKey = 0f,
            material = Material(),
        )

        assertSame(modelNormal, instance.normalFor(InstancedShaderLayoutMode.FIXED))
        assertSame(modelViewNormal, instance.normalFor(InstancedShaderLayoutMode.RUNTIME))
    }
}
