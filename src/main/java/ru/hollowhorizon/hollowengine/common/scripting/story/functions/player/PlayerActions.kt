package ru.hollowhorizon.hollowengine.common.scripting.story.functions.player

import net.minecraft.ChatFormatting
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.common.utils.colored
import ru.hollowhorizon.hc.common.utils.literal
import ru.hollowhorizon.hc.common.utils.plus
import ru.hollowhorizon.hollowengine.client.gui.scripting.sendToast
import ru.hollowhorizon.hollowengine.compiler.suspendable.await
import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun Player.waitPos(pos: Vec3, radius: Float = 1f, inverse: Boolean = false) {
    if (inverse) {
        await(distanceToSqr(pos) >= radius * radius)
    } else {
        await(distanceToSqr(pos) <= radius * radius)
    }
}

fun Player.say(text: String) =
    sendSystemMessage("[".literal.colored(ChatFormatting.GOLD) + name + "] ".literal.colored(ChatFormatting.GOLD) + text.literal)

fun Player.send(text: String) = sendSystemMessage(text.literal)
fun Player.notify(text: String) = sendToast(text.literal)