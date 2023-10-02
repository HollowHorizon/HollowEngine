package ru.hollowhorizon.hollowengine.common

import net.minecraft.network.chat.*
import net.minecraft.world.entity.player.Player

infix fun Player.sendMessage(message: Component) {
    this.sendMessage(message, this.uuid)
}

fun Player.sendTranslate(message: String, vararg args: Any) = this.sendMessage(TranslatableComponent(message, args))

fun empty(): Component = TextComponent("")

fun literal(srt: String) = TextComponent(srt)