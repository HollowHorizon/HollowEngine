package ru.hollowhorizon.hollowengine.common.network

import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraftforge.network.NetworkDirection
import ru.hollowhorizon.hc.client.models.gltf.manager.GltfManager
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet

@HollowPacketV2(toTarget = NetworkDirection.PLAY_TO_CLIENT)
class CopyTextPacket : Packet<String>({ player, value ->
    mc.player!!.sendSystemMessage(Component.translatable("hollowengine.commands.copy", Component.literal(value).apply{
        style = Style.EMPTY
            .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, "hollowengine.tooltips.copy".mcTranslate))
            .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))
    }))
    mc.keyboardHandler.clipboard = value
})

@HollowPacketV2(toTarget = NetworkDirection.PLAY_TO_CLIENT)
class ShowModelInfoPacket : Packet<String>({ player, value ->
    val location = value.rl

    GltfManager.getOrCreate(location).let { model ->
        player.sendSystemMessage(Component.translatable("hollowengine.commands.model_animations", value.substringAfterLast('/')))

        model.animationPlayer.nameToAnimationMap.keys.forEach { anim ->
            player.sendSystemMessage(Component.literal("- ").append(Component.literal(anim).apply {
                style = Style.EMPTY
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, "hollowengine.tooltips.copy".mcTranslate))
                    .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, anim))
            }))
        }

        player.sendSystemMessage(Component.translatable("hollowengine.commands.model_textures", value.substringAfterLast('/')))

        model.modelPath.textures.map { it.path.removeSuffix(".png") }.forEach { anim ->
            player.sendSystemMessage(Component.literal("- ").append(Component.literal(anim).apply {
                style = Style.EMPTY
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, "hollowengine.tooltips.copy".mcTranslate))
                    .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, anim))
            }))
        }
    }
})