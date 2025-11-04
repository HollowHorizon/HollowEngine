package ru.hollowhorizon.hollowengine.common.scripting.components

import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import kotlin.script.experimental.annotations.KotlinScript


@KotlinScript(
    "Entity Component", "entity-component.kts", compilationConfiguration = HollowScriptConfiguration::class
)
abstract class EntityComponent : Component<LivingEntity>()

@KotlinScript(
    "Entity Component", "level-component.kts", compilationConfiguration = HollowScriptConfiguration::class
)
abstract class LevelComponent : Component<Level>()

@KotlinScript(
    "Server Component", "server-component.kts", compilationConfiguration = HollowScriptConfiguration::class
)
abstract class ServerComponent : Component<MinecraftServer>()

