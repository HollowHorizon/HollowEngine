package ru.hollowhorizon.hollowengine.common.scripting.components

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import kotlin.reflect.typeOf
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.baseClass

abstract class ScriptableComponent<T : Any> : Component<T>() {
    private var onAttaches = HashSet<() -> Unit>()
    private var onDetachs = HashSet<() -> Unit>()
    private var onTicks = HashSet<() -> Unit>()
    private var onEnableds = HashSet<() -> Unit>()
    private var onDisableds = HashSet<() -> Unit>()
    private var onSaves = HashSet<CompoundTag.() -> Unit>()
    private var onLoads = HashSet<CompoundTag.() -> Unit>()

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

    override fun saveExtras(tag: CompoundTag) {
        onSaves.forEach { it(tag) }
    }

    override fun loadExtras(tag: CompoundTag) {
        onLoads.forEach { it(tag) }
    }

    fun onAttach(action: () -> Unit) {
        onAttaches.add(action)
    }

    fun onDetach(action: () -> Unit) {
        onDetachs.add(action)
    }

    fun onUpdate(action: () -> Unit) {
        onTicks.add(action)
    }

    fun onEnabled(action: () -> Unit) {
        onEnableds.add(action)
    }

    fun onDisabled(action: () -> Unit) {
        onDisableds.add(action)
    }

    fun onSave(action: CompoundTag.() -> Unit) {
        onSaves.add(action)
    }

    fun onLoad(action: CompoundTag.() -> Unit) {
        onLoads.add(action)
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

@KotlinScript(
    "Server Component",
    "server-component.kts",
    compilationConfiguration = HollowScriptConfiguration::class
)
abstract class ServerComponent : ScriptableComponent<MinecraftServer>()

