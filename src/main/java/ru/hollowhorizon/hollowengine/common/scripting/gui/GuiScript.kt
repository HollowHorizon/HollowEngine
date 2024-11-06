package ru.hollowhorizon.hollowengine.common.scripting.gui

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.imgui.Graphics

interface GuiScript {
    fun Graphics.draw(storage: CompoundTag)
    fun handle(player: Player, storage: CompoundTag)
}