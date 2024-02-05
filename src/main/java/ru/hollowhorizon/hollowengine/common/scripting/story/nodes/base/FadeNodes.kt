package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hollowengine.client.screen.FadeOverlayScreenPacket
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder

open class FadeContainer {
    var text = ""
    var subtitle = ""
    var texture = ""
    var color = 0xFFFFFF
    open var time = 0
}

fun IContextBuilder.fadeInOut(block: FadeContainer.() -> Unit) {
    +WaitNode {
        val container = FadeContainer().apply(block)
        stateMachine.team.onlineMembers.forEach {
            FadeOverlayScreenPacket(
                true,
                container.text,
                container.subtitle,
                container.color,
                container.texture,
                container.time / 2
            ).send(PacketDistributor.PLAYER.with { it })
        }
        container.time / 2
    }
    +WaitNode {
        val container = FadeContainer().apply(block)
        stateMachine.team.onlineMembers.forEach {
            FadeOverlayScreenPacket(
                false,
                container.text,
                container.subtitle,
                container.color,
                container.texture,
                container.time / 2
            ).send(PacketDistributor.PLAYER.with { it })
        }
        container.time / 2
    }
}

fun IContextBuilder.fadeIn(block: FadeContainer.() -> Unit) = +WaitNode {
    val container = FadeContainer().apply(block)
    stateMachine.team.onlineMembers.forEach {
        FadeOverlayScreenPacket(
            true,
            container.text,
            container.subtitle,
            container.color,
            container.texture,
            container.time
        ).send(PacketDistributor.PLAYER.with { it })
    }
    container.time
}

fun IContextBuilder.fadeOut(block: FadeContainer.() -> Unit) = +WaitNode {
    val container = FadeContainer().apply(block)
    stateMachine.team.onlineMembers.forEach {
        FadeOverlayScreenPacket(
            false,
            container.text,
            container.subtitle,
            container.color,
            container.texture,
            container.time
        ).send(PacketDistributor.PLAYER.with { it })
    }
    container.time
}