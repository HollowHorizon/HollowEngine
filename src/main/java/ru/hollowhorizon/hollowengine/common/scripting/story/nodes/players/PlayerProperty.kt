package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.players

import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.server.level.ServerPlayer
import kotlin.properties.ReadOnlyProperty

typealias PlayerProperty = () -> ServerPlayer

operator fun Team.get(name: () -> String): ReadOnlyProperty<Any?, PlayerProperty> {
    return ReadOnlyProperty<Any?, PlayerProperty> { thisRef, property -> { this.onlineMembers.first { it.name.string == name() } } }
}