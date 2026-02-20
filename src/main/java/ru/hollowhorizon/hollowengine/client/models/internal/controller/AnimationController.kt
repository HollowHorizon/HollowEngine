package ru.hollowhorizon.hollowengine.client.models.internal.controller

import kotlinx.coroutines.launch
import net.minecraft.world.entity.LivingEntity

open class AnimationController(val system: AnimationSystem) {

    companion object {
        const val ENTRY = "__entry__"
        const val ANY = "__any__"
    }

    data class State(
        val name: String,
        val animationName: String,
        val wrapMode: WrapMode = WrapMode.Loop,
        val speed: Float = 1f,
        val weight: Float = 1f,
        val priority: Int = 0,
        val overrideTranslation: Boolean = true,
        val overrideRotation: Boolean = true,
        val overrideScale: Boolean = true,
        val onEnter: (suspend (entity: LivingEntity) -> Unit)? = null,
        val onExit: (suspend (entity: LivingEntity) -> Unit)? = null,
        val onUpdate: (suspend (entity: LivingEntity, dt: Float) -> Unit)? = null,
    )

    data class Transition(
        val fromState: String,
        val toState: String,
        val condition: (LivingEntity.() -> Boolean) = { true },
        val duration: Float = 0f,
        val priority: Int = 0,
        internal val order: Int = 0,
    ) {
        val key: String = "$fromState->$toState"
    }

    @AnimControllerDsl
    class Builder {
        private val states = LinkedHashMap<String, State>()
        private var entryTarget: String? = null
        private val transitions = LinkedHashSet<Transition>()
        private var insertionOrder = 0

        fun state(
            name: String,
            animationName: String,
            wrapMode: WrapMode = WrapMode.Loop,
            speed: Float = 1f,
            weight: Float = 1f,
            priority: Int = 0,
            overrideTranslation: Boolean = true,
            overrideRotation: Boolean = true,
            overrideScale: Boolean = true,
            onEnter: (suspend (LivingEntity) -> Unit)? = null,
            onExit: (suspend (entity: LivingEntity) -> Unit)? = null,
            onUpdate: (suspend (entity: LivingEntity, dt: Float) -> Unit)? = null,
        ) {
            states[name] = State(
                name = name,
                animationName = animationName,
                wrapMode = wrapMode,
                speed = speed,
                weight = weight,
                priority = priority,
                overrideTranslation = overrideTranslation,
                overrideRotation = overrideRotation,
                overrideScale = overrideScale,
                onEnter = onEnter,
                onExit = onExit,
                onUpdate = onUpdate,
            )
        }

        fun entry(toState: String) {
            if (entryTarget == null) {
                entryTarget = toState
            }
        }

        fun transition(
            fromState: String,
            toState: String,
            duration: Float = 0f,
            priority: Int = 0,
            condition: LivingEntity.() -> Boolean,
        ) {
            val transition = Transition(
                fromState = fromState,
                toState = toState,
                condition = condition,
                duration = duration,
                priority = priority,
                order = insertionOrder,
            )
            transitions += transition
            insertionOrder++
        }

        fun any(
            toState: String,
            duration: Float = 0f,
            priority: Int = 0,
            condition: LivingEntity.() -> Boolean,
        ) {
            transition(
                fromState = ANY,
                toState = toState,
                duration = duration,
                priority = priority,
                condition = condition,
            )
        }

        fun build(): Definition {
            val entry = entryTarget ?: states.keys.firstOrNull()
            return Definition(
                states = states.toMap(),
                entryState = entry,
                transitions = transitions.toList(),
            )
        }
    }

    data class Definition(
        val states: Map<String, State>,
        val entryState: String?,
        val transitions: List<Transition>,
    )

    @DslMarker
    annotation class AnimControllerDsl

    private var definition: Definition = Definition(emptyMap(), null, emptyList())
    private var initialized = false

    var currentState: State? = null
        private set

    fun configure(block: Builder.() -> Unit) {
        definition = Builder().apply(block).build()
        initialized = false
        currentState = null
    }

    open fun update(entity: LivingEntity, dt: Float) {
        if (!initialized) {
            val entry = definition.entryState
            currentState = entry?.let { definition.states[it] } ?: definition.states.values.firstOrNull()
            initialized = true
        }

        val state = currentState ?: return

        val selected = selectTransition(entity, state.name)
        if (selected != null) {
            val target = definition.states[selected.toState] ?: return
            system.scope.launch {
                state.onExit?.invoke(entity)
                system.transition(
                    from = state.animationName,
                    to = target.animationName,
                    duration = selected.duration,
                )
                currentState = target
                target.onEnter?.invoke(entity)
            }
            return
        }

        system.scope.launch {
            state.onUpdate?.invoke(entity, dt)
        }
    }

    private fun selectTransition(entity: LivingEntity, current: String): Transition? {
        val transitions = definition.transitions
        if (transitions.isEmpty()) return null

        return transitions
            .asSequence()
            .filter { t ->
                when (t.fromState) {
                    ANY -> t.toState != current
                    else -> t.fromState == current
                }
            }
            .filter { t -> t.condition(entity)  }
            .sortedWith(
                compareByDescending<Transition> { it.priority }
                    .thenBy { it.order }
            )
            .firstOrNull()
    }
}