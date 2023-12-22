package ru.hollowhorizon.hollowengine.common.network

import net.minecraft.network.chat.*
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.network.NetworkDirection
import ru.hollowhorizon.hc.client.models.gltf.manager.GltfManager
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet
import ru.hollowhorizon.hollowengine.common.events.ServerKeyPressedEvent
import ru.hollowhorizon.hollowengine.common.util.Keybind

@HollowPacketV2(toTarget = NetworkDirection.PLAY_TO_CLIENT)
class CopyTextPacket : Packet<String>({ player, value ->
    mc.player!!.sendMessage(TranslatableComponent("hollowengine.commands.copy", TextComponent(value).apply {
        style = Style.EMPTY
            .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, "hollowengine.tooltips.copy".mcTranslate))
            .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))
    }), mc.player!!.uuid)
    mc.keyboardHandler.clipboard = value
})

@HollowPacketV2(toTarget = NetworkDirection.PLAY_TO_CLIENT)
class ShowModelInfoPacket : Packet<String>({ player, value ->
    val location = value.rl

    GltfManager.getOrCreate(location).let { model ->
        player.sendMessage(
            TranslatableComponent(
                "hollowengine.commands.model_animations",
                value.substringAfterLast('/')
            ), player.uuid
        )

        model.animationPlayer.nameToAnimationMap.keys.forEach { anim ->
            player.sendMessage(TextComponent("- ").append(TextComponent(anim).apply {
                style = Style.EMPTY
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, "hollowengine.tooltips.copy".mcTranslate))
                    .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, anim))
            }), player.uuid)
        }

        player.sendMessage(
            TranslatableComponent(
                "hollowengine.commands.model_textures",
                value.substringAfterLast('/')
            ), player.uuid
        )

        model.modelPath.textures.map { it.path.removeSuffix(".png") }.forEach { anim ->
            player.sendMessage(TextComponent("- ").append(TextComponent(anim).apply {
                style = Style.EMPTY
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, "hollowengine.tooltips.copy".mcTranslate))
                    .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, anim))
            }), player.uuid)
        }
    }
})

@HollowPacketV2(NetworkDirection.PLAY_TO_SERVER)
class KeybindPacket : Packet<Keybind>({ player, value ->
    MinecraftForge.EVENT_BUS.post(ServerKeyPressedEvent(player, value))
})