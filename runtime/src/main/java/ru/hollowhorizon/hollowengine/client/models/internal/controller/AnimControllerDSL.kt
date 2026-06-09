package ru.hollowhorizon.hollowengine.client.models.internal.controller

import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.scene.TrsTransformF
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.models.internal.NodeDefinition
import ru.hollowhorizon.hollowengine.client.models.internal.animations.Animation
import ru.hollowhorizon.hollowengine.client.models.internal.controller.BlendMode.Additive
import ru.hollowhorizon.hollowengine.client.models.internal.controller.BlendMode.Override
import ru.hollowhorizon.hollowengine.client.models.internal.controller.Controller.Companion.AUTOMATIC_LAYER
import ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode.*
import ru.hollowhorizon.hollowengine.common.utils.molang.compiler.BoolExpr
import ru.hollowhorizon.hollowengine.common.utils.molang.compiler.MolangCompiler
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.MolangContext
import java.util.*

@DslMarker
annotation class AnimControllerDSL

private fun Float.modPositive(divisor: Float): Float =
    (this % divisor + divisor) % divisor

/**
 * Режимы воспроизведения анимации
 * @property Once Проиграть анимацию 1 раз.
 * @property Loop Проигрывать анимацию в цикле.
 * @property PingPong Проигрывать анимацию до последнего кадра и в обратном порядке зациклено.
 * @property ClampForever Проигрывать анимацию остановившись на последнем кадре.
 */
@AnimControllerDSL
enum class WrapMode(val wrapTime: (Animation, Float) -> Float) {
    Once({ animation, time ->
        if (time < 0) animation.duration - (-time).coerceAtMost(animation.duration)
        else time.coerceAtMost(animation.duration)
    }),
    Loop({ animation, time ->
        time.modPositive(animation.duration)
    }),
    PingPong({ animation, time ->
        val period = animation.duration * 2
        val m = time.modPositive(period)
        if (m <= animation.duration) m else period - m
    }),
    ClampForever({ animation, time ->
        if (time < 0) animation.duration - (-time).coerceAtMost(animation.duration)
        else time.coerceAtMost(animation.duration)
    });
}

/**
 * Режимы смешивания анимаций
 * @property Override Переопределяет анимацию полностью заменяя прошлую позу текущей.
 * @property Additive Добавляет к прошлой позе текущую, накапливая эффект. Рекомендуется использовать вместе с референс-позой.
 */
@AnimControllerDSL
enum class BlendMode { Override, Additive }

@Serializable
@AnimControllerDSL
data class Mask(val includes: Set<String>, val excludes: Set<String>) {
    @Transient
    private var bakedNodes: Set<NodeDefinition>? = null

    companion object {
        fun full() = Mask(emptySet(), emptySet())
        fun of(vararg bones: String): Mask {
            val include = mutableSetOf<String>()
            val exclude = mutableSetOf<String>()

            for (pattern in bones) {
                val isExclude = pattern.startsWith("!")
                val raw = if (isExclude) pattern.drop(1) else pattern
                if (isExclude) exclude += raw else include += raw
            }
            return Mask(include, exclude)
        }
    }

    fun bake(root: NodeDefinition): Set<NodeDefinition> {
        bakedNodes?.let { return it }
        val allBones = root.allBones()
        val result = mutableSetOf<NodeDefinition>()

        for (bone in allBones) {
            val path = bone.path
            if (includes.isEmpty() || includes.any { it.endsWith(path) }) {
                if (excludes.none { it.endsWith(path) }) {
                    result += bone
                }
            }
        }
        bakedNodes = result
        return result
    }

    fun reset() {
        bakedNodes = null
    }
}

@Serializable
@AnimControllerDSL
data class Controller(var layers: MutableList<Layer>) {
    companion object {
        const val AUTOMATIC_LAYER = "__Automatic__"
    }

    init {
        layers.sortBy { it.priority }
    }

    suspend fun init() {
        try {
            layers.forEach { it.mask.reset() }
        } catch (e: Exception) {
            HollowCore.LOGGER.error("Error while compiling math expressions!", e)
        }
    }

    fun uploadAnimations(animations: Map<String, Animation>) {
        layers.forEach {
            it.stateMachine.uploadAnimations(animations)
            animations[it.referencePose]?.let { animation ->
                //it.referencePoseRef = { animation.compute(it, 0f) }
            } ?: run { it.referencePoseRef = { TrsTransformF() } }
        }
    }

    fun update(node: NodeDefinition, context: MolangContext, time: Float) {
        layers.removeIf { layer ->
            if (node !in layer.mask.bake(node.root)) return@removeIf false

            layer.update(node, context, time)?.let { transform ->
                node.transform.apply {
                    when (layer.blendMode) {
                        Additive -> applyAdditiveLayer(layer, node, transform, time)
                        Override -> applyOverrideLayer(layer, node, transform)
                    }
                }
            }
            layer.stateMachine.isEnded
        }
    }

    private fun TrsTransformF.applyAdditiveLayer(
        layer: Layer,
        node: NodeDefinition,
        transform: TrsTransformF,
        time: Float,
    ) {
        var reference = layer.referencePoseRef(node) ?: TrsTransformF()
        val transition = layer.stateMachine.currentTransition
        if (transition != null) { // Без этого он будет переходить из Т-позы в начальную. Нам же надо, чтобы он всегда начинал с референса
            if (transition.from == "*") {
                val duration = transition.let { (time - it.startTime) / it.duration }
                reference = reference.mix(TrsTransformF(), 1f - duration) ?: TrsTransformF()
            } else if (transition.to == "*" || transition.to == "__end__") {
                val duration = transition.let { (time - it.startTime) / it.duration }
                reference = reference.mix(TrsTransformF(), duration) ?: TrsTransformF()
            }
        }
        translate(Vec3f.ZERO.mix(transform.translation - reference.translation, layer.weight))
        rotate(
            QuatF.IDENTITY.mix(reference.rotation.invert().mul(transform.rotation), layer.weight)
        )
        scale(Vec3f.ONES.mix(transform.scale / reference.scale, layer.weight))
    }

    private fun TrsTransformF.applyOverrideLayer(
        layer: Layer,
        node: NodeDefinition,
        transform: TrsTransformF,
    ) {
        val base = node.baseTransform
        translation.set(base.translation + Vec3f.ZERO.mix(transform.translation, layer.weight))
        rotation.set(base.rotation * QuatF.IDENTITY.mix(transform.rotation, layer.weight))
        scale.set(base.scale * Vec3f.ONES.mix(transform.scale, layer.weight))
    }

    fun transferFrom(old: Controller?) {
        if (old == null) return
        val oldLayers = old.layers.associateBy { it.name }
        for (layer in layers) {
            val oldLayer = oldLayers[layer.name] ?: continue

            layer.stateMachine.transferStateFrom(oldLayer.stateMachine)
        }
    }
}

@AnimControllerDSL
class ControllerBuilder {
    private val layers = mutableListOf<Layer>()

    fun layer(layer: Layer) = layers.add(layer)

    fun automatic(priority: Int = 0, weight: Float = 1f, blendMode: BlendMode = Override) =
        layers.add(
            Layer(
                AUTOMATIC_LAYER,
                priority,
                weight,
                Mask.full(),
                blendMode,
                StateMachine(listOf(), mutableListOf())
            )
        )

    fun head(name: String = "Head") {
        layers.add(
            Layer(
                "__HeadLayer__",
                1,
                1f,
                Mask.of(name),
                Override,
                StateMachine(listOf(), mutableListOf())
            )
        )
    }

    fun layer(
        name: String,
        priority: Int = 0,
        weight: Float = 1f,
        mask: Mask = Mask.full(),
        blendMode: BlendMode = Additive,
        referencePose: String = "",
        block: LayerBuilder.() -> Unit,
    ) {
        layers.removeIf { it.name == name }
        layers += LayerBuilder(name, priority, weight, blendMode, mask, referencePose).apply(block).build()
    }

    fun build(): Controller = Controller(layers)
}

@Serializable
@AnimControllerDSL
data class Layer(
    val name: String,
    val priority: Int,
    val weight: Float,
    val mask: Mask,
    val blendMode: BlendMode,
    var stateMachine: StateMachine,
    var referencePose: String = "",
) {
    @Transient
    lateinit var referencePoseRef: (NodeDefinition) -> TrsTransformF?

    fun update(node: NodeDefinition, context: MolangContext, time: Float): TrsTransformF? {
        return stateMachine.update(node, context, time)
    }
}

@AnimControllerDSL
class LayerBuilder(
    val name: String,
    val priority: Int,
    val weight: Float,
    val blend: BlendMode,
    val mask: Mask,
    val referencePose: String,
) {
    private var stateMachine = StateMachineBuilder().build()
    fun stateMachine(block: StateMachineBuilder.() -> Unit) {
        stateMachine = StateMachineBuilder().apply(block).build()
    }

    fun build() = Layer(name, priority, weight, mask, blend, stateMachine, referencePose)
}

@Serializable
@AnimControllerDSL
data class State(
    val name: String,
    val clip: ClipNode?,
    val blendTree: BlendTree?,
) {
    @Transient
    var animations: Map<String, Animation> = mutableMapOf()

    fun reset(query: MolangContext, time: Float) {
        clip?.reset(query, time)
        blendTree?.reset(query, time)
    }

    fun update(node: NodeDefinition, query: MolangContext, time: Float): TrsTransformF? {
        clip?.let { clip ->
            return clip.update(animations, query, node, time)
        }
        blendTree?.let {
            return it.update(animations, query, node, time)
        }
        return null
    }

    fun transferStateFrom(oldState: State) {
        oldState.clip?.let { clip?.transferFrom(it) }
        oldState.blendTree?.let { blendTree?.transferFrom(it) }
    }

}

@Serializable
@AnimControllerDSL
data class Transition(
    val from: String,
    val to: String,
    private val function: String,
    val duration: Float,
    val transitionClip: ClipNode?,
    val deltaClip: DeltaNode?,
    var exitTime: Float,
) {
    @Transient
    val condition: BoolExpr = MolangCompiler.compileBoolean(function)

    @Transient
    var fromRef: State? = null

    @Transient
    var toRef: State? = null

    var startTime = 0f
    var isFinished = false

    fun start(query: MolangContext, time: Float) {
        startTime = time
        isFinished = false
        toRef?.reset(query, time)
    }

    fun update(node: NodeDefinition, query: MolangContext, time: Float): TrsTransformF? {
        val from = fromRef?.update(node, query, time)
        val to = toRef?.update(node, query, time)

        val factor = ((time - startTime) / duration).coerceIn(0f..1f)
        if (factor == 1f) isFinished = true

        return from.mix(to, factor)
    }

    fun canExit(currentState: State?, query: MolangContext, time: Float): Boolean {
        currentState?.clip?.let {
            if (it.wrap != WrapMode.Once) return true

            if (exitTime == 0f) return true
            val rawTime = it.rawTime(query, time)
            if (exitTime < 0f) return rawTime >= currentState.animations[it.name]?.duration ?: 0f

            return rawTime >= exitTime
        }
        return true
    }
}

fun TrsTransformF?.mix(other: TrsTransformF?, factor: Float): TrsTransformF? {
    if (this == null) return other?.let { TrsTransformF().mix(it, factor) }
    if (other == null) return this.mix(TrsTransformF(), factor)

    setCompositionOf(
        translation.mix(other.translation, factor),
        rotation.mix(other.rotation, factor),
        scale.mix(other.scale, factor)
    )
    return this
}

@Serializable
@AnimControllerDSL
data class StateMachine(
    val states: List<State>,
    val transitions: MutableList<Transition>,
    private var initialState: String? = null
) {
    @Transient
    var currentState: State? = null

    @Transient
    var currentTransition: Transition? = null

    @Transient
    internal var isEnded: Boolean = false


    init {
        transitions.forEach { transition ->
            transition.fromRef = states.firstOrNull { it.name == transition.from }
            transition.toRef = states.firstOrNull { it.name == transition.to }
        }
        currentState = states.firstOrNull { it.name == initialState }
    }

    fun update(node: NodeDefinition, context: MolangContext, time: Float): TrsTransformF? {
        if (currentTransition == null) {
            transitions.firstOrNull {
                (it.from == currentState?.name || it.from == "*" && it.to != currentState?.name) &&
                        it.condition.getBoolean(context.query, context.variables) && it.canExit(currentState, context, time)
            }?.let { transition ->
                transition.fromRef = states.firstOrNull { it.name == transition.from } ?: currentState
                transition.toRef = states.firstOrNull { it.name == transition.to }
                currentTransition = transition
                transition.start(context, time)
            }

            if (transitions.isEmpty() && currentState == null) currentState = states.firstOrNull()
        }

        currentTransition?.let { transition ->
            if (transition.isFinished) {
                currentTransition = null
                if (transition.to == "__end__") {
                    isEnded = true
                } else {
                    currentState = states.firstOrNull { it.name == transition.to }
                }
                return@let
            }
            return transition.update(node, context, time)
        }

        return currentState?.update(node, context, time)
    }

    fun transferStateFrom(old: StateMachine) {
        val oldStates = old.states.associateBy { it.name }

        for (state in states) {
            val oldState = oldStates[state.name] ?: continue
            state.transferStateFrom(oldState)
        }

        currentState = old.currentState
    }

    fun uploadAnimations(animations: Map<String, Animation>) {
        states.forEach { it.animations = animations }
        currentState?.animations = animations
    }
}

@AnimControllerDSL
class StateMachineBuilder {
    private val states = mutableListOf<State>()
    private val transitions = mutableListOf<Transition>()
    private var initialState: String? = null

    fun state(name: String, block: StateBuilder.() -> Unit): State {
        val state = StateBuilder(name).apply(block).build()
        states += state
        return state
    }

    fun initialState(name: String) {
        initialState = name
    }

    fun transition(from: String, to: String, block: TransitionBuilder.() -> Unit) {
        transitions += TransitionBuilder(from, to).apply(block).build()
    }

    fun exit(from: String, duration: Float, condition: String = "true") {
        transitions += TransitionBuilder(from, "__end__").apply {
            condition(condition)
            duration(duration)
            exitTime(true)
        }.build()
    }

    fun build() = StateMachine(states, transitions, initialState)
}

@AnimControllerDSL
class StateBuilder(val name: String) {
    private var clip: ClipNode? = null
    private var blendTree: BlendTree? = null

    fun clip(
        name: String,
        wrap: WrapMode = WrapMode.Loop,
        speed: String = "1f",
    ) {
        clip = ClipNode(name, wrap, speed)
    }

    fun blendTree(block: BlendTreeBuilder.() -> Unit) {
        blendTree = BlendTreeBuilder().apply(block).build()
    }

    fun build() = State(name, clip, blendTree)
}

// BlendTree


@Serializable
@AnimControllerDSL
data class BlendTree(
    private val function: String,
    val nodes: List<BlendNode>,
    private val smoothingTime: Float = 0f,
) {
    @Transient
    private val factor = MolangCompiler.compileFloat(function)

    private val keys = nodes.map { it.threshold }.toFloatArray()
    private var lastFiltered: Float = 0f
    private var lastTime: Float = 0f

    fun reset(context: MolangContext, time: Float) {
        nodes.forEach { it.reset(context, time) }
        lastTime = time
        lastFiltered = factor.getFloat(context.query, context.variables)
    }

    fun update(animations: Map<String, Animation>, context: MolangContext, node: NodeDefinition, time: Float): TrsTransformF? {
        if (nodes.isEmpty()) return null
        // Сглаживание фактора
        val raw = factor.getFloat(context.query, context.variables)
        val filtered = if (smoothingTime > 0f && lastTime != 0f) {
            val dt = (time - lastTime).coerceAtLeast(0f)
            val alpha = (dt / smoothingTime).coerceIn(0f..1f)
            lastFiltered + (raw - lastFiltered) * alpha
        } else raw
        lastFiltered = filtered
        lastTime = time

        // Поиск узлов
        return when {
            filtered <= keys.first() || keys.size == 1 -> nodes.first().update(animations, context, node, time)
            filtered >= keys.last() -> nodes.last().update(animations, context, node, time)
            else -> {
                val idx = Arrays.binarySearch(keys, filtered).let { if (it >= 0) it else -it - 2 }
                val prev = nodes[idx]
                val next = nodes[idx + 1]
                val local = filtered - prev.threshold
                val delta = next.threshold - prev.threshold
                val t = (local / delta).coerceIn(0f..1f)
                val first = prev.update(animations, context, node, time)
                val second = next.update(animations, context, node, time)
                first.mix(second, t)
            }
        }
    }

    fun transferFrom(old: BlendTree) {
        old.nodes
        nodes.forEachIndexed { i, node ->
            old.nodes.getOrNull(i)?.let { oldNode ->
                node.transferFrom(oldNode)
            }
        }
        lastTime = old.lastTime
        lastFiltered = old.lastFiltered
    }
}

@AnimControllerDSL
class BlendTreeBuilder {
    private val nodes = mutableListOf<BlendNode>()
    private var factor: String = "1f"
    private var smoothingTime: Float = 0f

    fun clip(
        name: String,
        threshold: Float,
        wrap: WrapMode = WrapMode.Loop,
        speed: String,
    ) {
        nodes += BlendNode(ClipNode(name, wrap, speed), threshold)
    }

    fun factor(function: String, smoothingTime: Float = 0f) {
        this.factor = function
        this.smoothingTime = smoothingTime
    }

    fun build(): BlendTree = BlendTree(factor, nodes.sortedBy { it.threshold }, smoothingTime)
}

@Serializable
@AnimControllerDSL
data class BlendNode(val clip: ClipNode, val threshold: Float) {
    fun update(animations: Map<String, Animation>, query: MolangContext, node: NodeDefinition, time: Float): TrsTransformF? {
        return clip.update(animations, query, node, time)
    }

    fun reset(query: MolangContext, time: Float) {
        clip.reset(query, time)
    }

    fun transferFrom(oldNode: BlendNode) {
        clip.transferFrom(oldNode.clip)
    }

}

@Serializable
@AnimControllerDSL
data class ClipNode(
    val name: String,
    val wrap: WrapMode,
    private val function: String,
) {
    @Transient
    private val speed = MolangCompiler.compileFloat(function)

    private var pausedAnimTime: Float = 0f
    private var startTime = 0f
    private var oldSpeed = 0f


    fun reset(context: MolangContext, time: Float) {
        pausedAnimTime = 0f
        startTime = time
        oldSpeed = speed.getFloat(context.query, context.variables)
    }

    fun rawTime(context: MolangContext, time: Float): Float {
        val newSpeed = speed.getFloat(context.query, context.variables)

        if (newSpeed != oldSpeed) {
            val currentRaw = (time - startTime) * oldSpeed

            if (newSpeed == 0f) {
                pausedAnimTime = currentRaw
            } else {
                startTime = time - currentRaw / newSpeed
            }

            oldSpeed = newSpeed
        }

        return if (oldSpeed == 0f) pausedAnimTime
        else (time - startTime) * oldSpeed
    }

    fun update(animations: Map<String, Animation>, query: MolangContext, node: NodeDefinition, time: Float): TrsTransformF? {
        val rawTime = rawTime(query, time)

        val animation = animations[name] ?: return null

        val sampleTime = wrap.wrapTime(animation, rawTime)
        TODO()
//        animation.computeWeights(node, sampleTime)?.let { weights ->
//            node.mesh?.weights?.let { nodeWeights ->
//                weights.copyInto(nodeWeights)
//            }
//        }
//        return animation.compute(node.index, sampleTime)
    }

    fun transferFrom(old: ClipNode) {
        pausedAnimTime = old.pausedAnimTime
        startTime = old.startTime
        oldSpeed = old.oldSpeed
    }

}

@Serializable
@AnimControllerDSL
data class DeltaNode(val targetPose: String)

// Transition builder
@AnimControllerDSL
class TransitionBuilder(val from: String, val to: String) {
    internal var condition: String = "false"
    internal var duration: Float = 0.2f
    private var transitionClip: ClipNode? = null
    private var deltaClip: DeltaNode? = null
    private var exitTime = 0f

    fun condition(block: String) {
        condition = block
    }

    fun duration(sec: Float) {
        duration = sec
    }

    fun clip(name: String, wrap: WrapMode = WrapMode.Once, speed: String = "1f") {
        transitionClip = ClipNode(name, wrap, speed)
    }

    fun deltaClip(targetPose: String) {
        deltaClip = DeltaNode(targetPose)
    }

    fun exitTime(time: Float) {
        exitTime = time
    }

    fun exitTime(hasExitTime: Boolean) {
        exitTime = if (hasExitTime) -1f else 0f
    }

    fun build() = Transition(from, to, condition, duration, transitionClip, deltaClip, exitTime)
}
