package ru.hollowhorizon.hollowengine.common.network

import kotlinx.serialization.Serializable
import net.minecraft.network.chat.*
import net.minecraft.world.entity.player.Player
import net.minecraftforge.common.MinecraftForge
import ru.hollowhorizon.hc.client.models.gltf.manager.GltfManager
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.common.events.ServerKeyPressedEvent
import ru.hollowhorizon.hollowengine.common.util.Keybind

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class CopyTextPacket(val text: String) : HollowPacketV3<CopyTextPacket> {
    override fun handle(player: Player, data: CopyTextPacket) {
        player.sendMessage(TranslatableComponent("hollowengine.commands.copy", TextComponent(data.text).apply {
            style = Style.EMPTY
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, "hollowengine.tooltips.copy".mcTranslate))
                .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, data.text))
        }), player.uuid)
        mc.keyboardHandler.clipboard = data.text
    }

}

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class ShowModelInfoPacket(val model: String) : HollowPacketV3<ShowModelInfoPacket> {
    override fun handle(player: Player, data: ShowModelInfoPacket) {
        val location = data.model.rl

        GltfManager.getOrCreate(location).let { model ->
            player.sendMessage(
                TranslatableComponent(
                    "hollowengine.commands.model_animations",
                    data.model.substringAfterLast('/')
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
                    data.model.substringAfterLast('/')
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
    }

}

@HollowPacketV2(HollowPacketV2.Direction.TO_SERVER)
@Serializable
class KeybindPacket(private val key: Keybind) : HollowPacketV3<KeybindPacket> {
    override fun handle(player: Player, data: KeybindPacket) {
        MinecraftForge.EVENT_BUS.post(ServerKeyPressedEvent(player, data.key))
    }
}