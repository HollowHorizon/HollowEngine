import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.Material
import ru.hollowhorizon.hollowengine.client.models.internal.Mesh
import ru.hollowhorizon.hollowengine.client.models.internal.Model
import ru.hollowhorizon.hollowengine.client.models.internal.NodeDefinition
import ru.hollowhorizon.hollowengine.client.models.internal.Primitive
import ru.hollowhorizon.hollowengine.client.models.internal.Scene
import ru.hollowhorizon.hollowengine.client.models.internal.animations.AnimationClip
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimationPose
import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorEvaluationContext
import ru.hollowhorizon.hollowengine.client.models.internal.animator.applyAnimationPose
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelInstanceMaterials
import ru.hollowhorizon.hollowengine.client.models.internal.v2.RuntimeNode
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationPlayMode
import ru.hollowhorizon.hollowengine.common.geary.components.AnimatorComponent
import ru.hollowhorizon.hollowengine.common.geary.components.ClipAnimationLayerSpec
import ru.hollowhorizon.hollowengine.common.geary.components.LayerBlendMode
import ru.hollowhorizon.hollowengine.common.utils.math.TrsTransformF
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class ModelInstanceIsolationTests {
    @Test
    fun `material changes stay inside one model instance`() {
        val sourceMaterial = Material(texture = "test:source".rl)
        val model = modelWith(sourceMaterial, floatArrayOf())
        val first = ModelInstanceMaterials(model)
        val second = ModelInstanceMaterials(model)

        first.values.single().texture = "test:replacement".rl

        assertNotSame(sourceMaterial, first.values.single())
        assertEquals("test:source".rl, sourceMaterial.texture)
        assertEquals("test:source".rl, second.values.single().texture)
    }

    @Test
    fun `morph weights stay inside one runtime node`() {
        val sourceWeights = floatArrayOf(0.25f, 0.75f)
        val definition = modelWith(Material(), sourceWeights).scenes.single().nodes.single()
        val first = RuntimeNode(definition, null)
        val second = RuntimeNode(definition, null)
        val pose = AnimationPose().apply {
            bone(definition.index).weights = floatArrayOf(1f, 0f)
        }

        applyAnimationPose(
            pose = pose,
            nodes = mapOf(definition.index to first),
            blendMode = LayerBlendMode.Override,
            weight = 1f,
        )

        assertContentEquals(floatArrayOf(1f, 0f), first.morphWeights)
        assertContentEquals(sourceWeights, second.morphWeights)
        assertContentEquals(sourceWeights, definition.mesh!!.primitives.single().weights)
    }

    @Test
    fun `model animation advances once per render frame`() {
        val animation = AnimationClip(name = "clock", nodes = emptyMap(), duration = 10f)
        val model = Model(
            scene = 0,
            scenes = listOf(
                Scene(
                    listOf(
                        NodeDefinition(
                            index = 0,
                            name = "Root",
                            children = mutableListOf(),
                            transform = TrsTransformF(),
                        )
                    )
                )
            ),
            materials = emptySet(),
            animations = listOf(animation),
        )
        val attachment = ModelAttachment(MutableStateFlow(AnimatedModel(model)), null)
        attachment.configureAnimator(
            animator = AnimatorComponent(
                layers = listOf(
                    ClipAnimationLayerSpec(
                        id = "clock",
                        animation = animation.name,
                        playMode = AnimationPlayMode.Loop,
                    )
                )
            ),
            key = null,
            context = AnimatorEvaluationContext(deltaTime = 0f, time = 0f),
        )

        repeat(60) { frame ->
            attachment.prepareFrame(dt = 1f / 60f, frame = frame.toLong())
        }
        attachment.prepareFrame(dt = 1f / 60f, frame = 59L)

        assertEquals(1f, attachment.animationTime("clock")!!, 0.0001f)
    }

    private fun modelWith(material: Material, morphWeights: FloatArray): Model {
        val primitive = Primitive(
            material = material,
            morphTargets = List(morphWeights.size) { emptyMap() },
            weights = morphWeights.copyOf(),
        )
        val node = NodeDefinition(
            index = 0,
            name = "Root",
            children = mutableListOf(),
            transform = TrsTransformF(),
            mesh = Mesh(listOf(primitive), morphWeights.copyOf()),
        )
        return Model(
            scene = 0,
            scenes = listOf(Scene(listOf(node))),
            materials = setOf(material),
        )
    }
}
