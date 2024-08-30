package ru.hollowhorizon.hollowengine.common.story.episode.actions

import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.common.story.episode.Episode

interface Action {
    fun edit(ep: Episode) {}

    suspend fun run(ep: Episode, server: MinecraftServer)
}