package ru.hollowhorizon.hollowengine.common.scripting.story

import net.minecraft.world.entity.player.Player

private val Player.scriptName: String?
    get() = if (this.persistentData.contains("hs_name")) this.persistentData.getString("hs_name") else null