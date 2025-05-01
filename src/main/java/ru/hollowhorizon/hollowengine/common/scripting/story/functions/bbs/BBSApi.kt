package ru.hollowhorizon.hollowengine.common.scripting.story.functions.bbs

import mchorse.bbs_mod.network.ServerNetwork
import net.minecraft.server.level.ServerPlayer

object BBSApi {
    fun startFilm(player: ServerPlayer, filmId: String, withCamera: Boolean = true) {
        ServerNetwork.sendPlayFilm(player, filmId, withCamera)
    }

    fun stopFilm(player: ServerPlayer, filmId: String) {
        ServerNetwork.sendStopFilm(player, filmId)
    }
}