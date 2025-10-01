package ru.hollowhorizon.hollowengine.common.scripting.components

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import kotlin.script.experimental.annotations.KotlinScript

abstract class ScriptableComponent<T: Any> : Component<T>() {
    private var onAttaches = HashSet<() -> Unit>()
    private var onDetachs = HashSet<() -> Unit>()
    private var onTicks = HashSet<() -> Unit>()
    private var onEnableds = HashSet<() -> Unit>()
    private var onDisableds = HashSet<() -> Unit>()

    override fun onAttach() {
        onAttaches.forEach { it() }
    }

    override fun onDetach() {
        onDetachs.forEach { it() }
    }

    override fun onTick() {
        onTicks.forEach { it() }
    }

    override fun onEnabled() {
        onEnableds.forEach { it() }
    }

    override fun onDisabled() {
        onDisableds.forEach { it() }
    }

    fun onAttach(action: () -> Unit) {
        onAttaches.add(action)
    }

    fun onDetach(action: () -> Unit) {
        onDetachs.add(action)
    }

    fun onTick(action: () -> Unit) {
        onTicks.add(action)
    }

    fun onEnabled(action: () -> Unit) {
        onEnableds.add(action)
    }

    fun onDisabled(action: () -> Unit) {
        onDisableds.add(action)
    }
}

@KotlinScript(
    "Entity Component",
    "entity-component.kts",
    compilationConfiguration = HollowScriptConfiguration::class
)
abstract class EntityComponent : ScriptableComponent<LivingEntity>()

@KotlinScript(
    "Entity Component",
    "level-component.kts",
    compilationConfiguration = HollowScriptConfiguration::class
)
abstract class LevelComponent : ScriptableComponent<Level>()
