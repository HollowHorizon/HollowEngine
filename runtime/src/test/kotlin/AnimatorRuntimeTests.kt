
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.scene.TrsTransformF
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.models.internal.NodeDefinition
import ru.hollowhorizon.hollowengine.client.models.internal.animations.Animation
import ru.hollowhorizon.hollowengine.client.models.internal.animations.AnimationData
import ru.hollowhorizon.hollowengine.client.models.internal.animations.interpolations.Interpolator
import ru.hollowhorizon.hollowengine.client.models.internal.animator.*
import ru.hollowhorizon.hollowengine.client.models.internal.v2.RuntimeNode
import ru.hollowhorizon.hollowengine.common.geary.components.*
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.models.ServerModelAnimationMetadata
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.MolangContext
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimatorRuntimeTests {
    private val animatorId = "hollowengine:animator".rl
    private val hideVanillaModelId = "hollowengine:hide_vanilla_entity_model".rl

    @AfterEach
    fun cleanup() {
        ComponentDescriptorRegistry.unregisterDescriptor(animatorId)
        ComponentDescriptorRegistry.unregisterDescriptor(hideVanillaModelId)
    }

    @Test
    fun `once playback clamps and marks ended`() {
        val state = LayerRuntimeState()

        val sampleTime = state.advance(
            duration = 1f,
            playMode = AnimationPlayMode.Once,
            speed = 1f,
            deltaTime = 2f,
        )

        assertEquals(1f, sampleTime)
        assertEquals(1f, state.time)
        assertTrue(state.ended)
        assertEquals(1f, state.advance(1f, AnimationPlayMode.Once, 1f, 1f))
    }

    @Test
    fun `loop playback wraps without ending`() {
        val state = LayerRuntimeState()

        val sampleTime = state.advance(
            duration = 1f,
            playMode = AnimationPlayMode.Loop,
            speed = 1f,
            deltaTime = 1.25f,
        )

        assertEquals(0.25f, sampleTime)
        assertEquals(0.25f, state.time)
        assertFalse(state.ended)
    }

    @Test
    fun `clamp forever holds final frame without ending`() {
        val state = LayerRuntimeState()

        val sampleTime = state.advance(
            duration = 1f,
            playMode = AnimationPlayMode.ClampForever,
            speed = 1f,
            deltaTime = 2f,
        )

        assertEquals(1f, sampleTime)
        assertEquals(1f, state.time)
        assertFalse(state.ended)
    }

    @Test
    fun `ping pong reflects time and toggles direction`() {
        val state = LayerRuntimeState()

        val sampleTime = state.advance(
            duration = 1f,
            playMode = AnimationPlayMode.PingPong,
            speed = 1f,
            deltaTime = 1.25f,
        )

        assertEquals(0.75f, sampleTime)
        assertEquals(0.75f, state.time)
        assertTrue(state.reversed)
        assertFalse(state.ended)
    }

    @Test
    fun `component helpers replace and remove layers by stable ids`() {
        val first = clip(id = "manual:idle", animation = "idle")
        val replacement = clip(id = "manual:idle", animation = "walk", priority = 2)
        val attack = clip(id = "manual:attack", animation = "attack")

        val animator = AnimatorComponent()
            .withLayer(first)
            .withLayer(attack)
            .withLayer(replacement)
            .withoutClip("attack")

        assertEquals(listOf(replacement), animator.layers)
    }

    @Test
    fun `animator component roundtrips through snapshot serializers`() {
        registerAnimatorDescriptor()
        registerHideVanillaModelDescriptor()
        val snapshot = EntitySnapshot(
            components = listOf(
                AnimatorComponent(
                    enabled = true,
                    layers = listOf(
                        clip(
                            id = "manual:wave",
                            animation = "wave",
                            priority = 7,
                            weight = AnimationExpression("weight_value"),
                            blendMode = LayerBlendMode.Additive,
                            playMode = AnimationPlayMode.PingPong,
                        )
                    ),
                ),
                HideVanillaEntityModelComponent(),
            ),
        )

        val yamlDecoded = EntitySerialization.deserializeFromYaml(EntitySerialization.serializeToYaml(snapshot))
        val nbtDecoded = EntitySerialization.deserializeFromNbt(EntitySerialization.serializeToNbt(snapshot))

        assertEquals(snapshot, yamlDecoded)
        assertEquals(snapshot, nbtDecoded)
    }

    @Test
    fun `standard player preset expressions evaluate through kotlite`() {
        val animator = StandardPlayerAnimatorPreset.create()
        val controller = animator.layers.filterIsInstance<AnimationControllerLayerSpec>().single()
        val procedural = animator.layers.filterIsInstance<ProceduralLayerSpec>().single()
        val evaluator = AnimationExpressionEvaluator()
        val context = AnimatorEvaluationContext(
            deltaTime = 0.05f,
            time = 10f,
            values = mapOf(
                "is_alive" to 1f,
                "is_sneaking" to 0f,
                "is_sprinting" to 1f,
                "is_on_ground" to 1f,
                "horizontal_speed" to 0.12f,
                "movement_animation_speed" to -1.2f,
                "velocity_y" to 0f,
                "head_body_y_delta" to 30f,
                "head_x_rotation" to -10f,
            ),
        )

        val runTransition = controller.transitions.single { it.to == "run" }
        val idleTransition = controller.transitions.single { it.to == "idle" }
        val walkState = controller.states.single { it.id == "walk" }
        val runState = controller.states.single { it.id == "run" }
        val headTransform = procedural.transforms.single { it.bone == "Head" }
        val leftEyeTransform = procedural.transforms.single { it.bone == "LeftEye" }
        val rightEyeTransform = procedural.transforms.single { it.bone == "RightEye" }

        assertTrue(evaluator.boolean(runTransition.condition, context))
        assertFalse(evaluator.boolean(idleTransition.condition, context))
        assertEquals(-0.6f, evaluator.float(walkState.speed, context), 0.0001f)
        assertEquals(-0.6f, evaluator.float(runState.speed, context), 0.0001f)
        assertEquals(6.5f, evaluator.vector(headTransform.rotation!!, context).x, 0.0001f)
        assertEquals(-18.6f, evaluator.vector(headTransform.rotation, context).y, 0.0001f)
        assertEquals(0f, evaluator.vector(leftEyeTransform.translation!!, context).x, 0.0001f)
        assertEquals(0.0648f, evaluator.vector(rightEyeTransform.translation!!, context).x, 0.0001f)
        assertEquals(-0.01f, evaluator.vector(leftEyeTransform.translation, context).y, 0.0001f)
    }

    @Test
    fun `negative loop speed samples animation backwards`() {
        val state = LayerRuntimeState()

        val sampleTime = state.advance(
            duration = 1f,
            playMode = AnimationPlayMode.Loop,
            speed = -1f,
            deltaTime = 0.25f,
        )

        assertEquals(0.75f, sampleTime)
        assertEquals(0.75f, state.time)
        assertFalse(state.ended)
    }

    @Test
    fun `layer fade in scales clip influence`() {
        val runtime = AnimatorRuntime()
        val node = testNode()

        runtime.apply(
            animator = AnimatorComponent(
                layers = listOf(
                    clip(id = "manual:wave", animation = "wave").copy(fadeIn = 1f)
                ),
            ),
            rootNodes = listOf(node),
            animations = mapOf("wave" to constantTranslationAnimation()),
            context = AnimatorEvaluationContext(deltaTime = 0.5f, time = 1f),
        )

        assertEquals(0.5f, node.transform.translation.x, 0.0001f)
    }

    @Test
    fun `once layer fades out after final frame`() {
        val runtime = AnimatorRuntime()
        val node = testNode()

        runtime.apply(
            animator = AnimatorComponent(
                layers = listOf(
                    clip(id = "manual:wave", animation = "wave").copy(fadeOut = 0.5f)
                ),
            ),
            rootNodes = listOf(node),
            animations = mapOf("wave" to constantTranslationAnimation()),
            context = AnimatorEvaluationContext(deltaTime = 1.25f, time = 1f),
        )

        assertEquals(0.5f, node.transform.translation.x, 0.0001f)
        assertTrue(runtime.stateFor("manual:wave")?.ended == true)
    }

    @Test
    fun `controller keeps current state when it is the highest priority matching transition`() {
        val runtime = AnimatorRuntime()
        val layer = AnimationControllerLayerSpec(
            id = "controller:locomotion",
            entryState = "run",
            states = listOf(
                AnimationControllerStateSpec(id = "walk", animation = "walk"),
                AnimationControllerStateSpec(id = "run", animation = "run"),
            ),
            transitions = listOf(
                AnimationControllerTransitionSpec(
                    from = ANY_STATE,
                    to = "run",
                    condition = AnimationExpression("is_sprinting != 0.0 && horizontal_speed > 0.02"),
                    priority = 70,
                ),
                AnimationControllerTransitionSpec(
                    from = ANY_STATE,
                    to = "walk",
                    condition = AnimationExpression("horizontal_speed > 0.02"),
                    priority = 50,
                ),
            ),
        )

        runtime.apply(
            animator = AnimatorComponent(layers = listOf(layer)),
            rootNodes = emptyList(),
            animations = emptyMap(),
            context = AnimatorEvaluationContext(
                deltaTime = 0.05f,
                time = 1f,
                values = mapOf("is_sprinting" to 1f, "horizontal_speed" to 0.5f),
            ),
        )

        val state = runtime.stateFor(layer.id)
        assertEquals("run", state?.currentState)
        assertEquals(null, state?.transition)
    }

    @Test
    fun `animation expressions reuse compiled evaluator with changed context`() {
        val evaluator = AnimationExpressionEvaluator()
        val expression = AnimationExpression("horizontal_speed > 0.02 && is_sprinting != 0.0")

        assertTrue(
            evaluator.boolean(
                expression,
                AnimatorEvaluationContext( 0.05f, 1f, mapOf("horizontal_speed" to 0.12f, "is_sprinting" to 1f)),
            )
        )
        assertFalse(
            evaluator.boolean(
                expression,
                AnimatorEvaluationContext( 0.05f, 2f, mapOf("horizontal_speed" to 0.0f, "is_sprinting" to 1f)),
            )
        )
    }

    @Test
    fun `animation expressions can use game time for fade out`() {
        val evaluator = AnimationExpressionEvaluator()
        val expression = AnimationExpression("clamp(1 - (game_time - 10) / 40.0, 0, 1)")

        assertEquals(
            0.5f,
            evaluator.float(expression, AnimatorEvaluationContext( 0.05f, 1f, mapOf("game_time" to 30f))),
            0.0001f,
        )
    }

    @Test
    fun `server model metadata reads animation duration from gltf assets`() {
        val duration = ServerModelAnimationMetadata.animationDuration(StandardPlayerAnimatorPreset.MODEL, "walk")

        assertTrue(duration != null && duration > 0f)
    }

    @Test
    fun `pose translation channels apply as local deltas from base pose`() {
        val baseTransform = TrsTransformF().apply {
            translate(Vec3f(0f, 10f, 0f))
        }
        val node = RuntimeNode(
            NodeDefinition(
                index = 0,
                name = "Bone",
                children = mutableListOf(),
                transform = baseTransform,
            ),
            parent = null,
        )
        val pose = AnimationPose().apply {
            bone(0).translation = Vec3f(0f, 1f, 0f)
        }

        applyAnimationPose(
            pose = pose,
            nodes = mapOf(0 to node),
            blendMode = LayerBlendMode.Override,
            weight = 1f,
        )

        assertEquals(10f, node.definition.baseTransform.translation.y, 0.0001f)
        assertEquals(11f, node.transform.translation.y, 0.0001f)
    }

    private fun clip(
        id: String,
        animation: String,
        priority: Int = 0,
        weight: AnimationExpression = AnimationExpression("1"),
        blendMode: LayerBlendMode = LayerBlendMode.Override,
        playMode: AnimationPlayMode = AnimationPlayMode.Once,
    ) = ClipAnimationLayerSpec(
        id = id,
        animation = animation,
        weight = weight,
        priority = priority,
        blendMode = blendMode,
        playMode = playMode,
    )

    private fun testNode(): RuntimeNode =
        RuntimeNode(
            NodeDefinition(
                index = 0,
                name = "Bone",
                children = mutableListOf(),
                transform = TrsTransformF(),
            ),
            parent = null,
        )

    private fun constantTranslationAnimation(): Animation =
        Animation(
            name = "wave",
            nodes = mapOf(
                0 to AnimationData(
                    translation = ConstantVec3fInterpolator(Vec3f(1f, 0f, 0f), 1f),
                    rotation = null,
                    scale = null,
                    weights = null,
                )
            ),
            duration = 1f,
        )

    private fun registerAnimatorDescriptor() {
        if (ComponentDescriptorRegistry.descriptorOrNull(animatorId) != null) return
        ComponentDescriptorRegistry.register(
            ComponentDescriptor(
                id = animatorId,
                value = AnimatorComponent::class,
                serializer = AnimatorComponent.serializer(),
            )
        )
    }

    private fun registerHideVanillaModelDescriptor() {
        if (ComponentDescriptorRegistry.descriptorOrNull(hideVanillaModelId) != null) return
        ComponentDescriptorRegistry.register(
            ComponentDescriptor(
                id = hideVanillaModelId,
                value = HideVanillaEntityModelComponent::class,
                serializer = HideVanillaEntityModelComponent.serializer(),
            )
        )
    }
}

private class ConstantVec3fInterpolator(
    private val value: Vec3f,
    override val duration: Float,
) : Interpolator<Vec3f> {
    override fun compute(time: Float, context: MolangContext): Vec3f = value
}
