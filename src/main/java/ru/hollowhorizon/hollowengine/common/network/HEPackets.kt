package ru.hollowhorizon.hollowengine.common.network

import dev.ftb.mods.ftbteams.FTBTeamsAPI
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent
import net.minecraftforge.network.NetworkDirection
import ru.hollowhorizon.hc.client.models.gltf.manager.GltfManager
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.mod.ModScript
import ru.hollowhorizon.hollowengine.common.scripting.story.runScript
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@HollowPacketV2(toTarget = NetworkDirection.PLAY_TO_CLIENT)
class CopyTextPacket : Packet<String>({ player, value ->
    mc.player!!.sendSystemMessage(Component.translatable("hollowengine.commands.copy", Component.literal(value).apply {
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
        player.sendSystemMessage(
            Component.translatable(
                "hollowengine.commands.model_animations",
                value.substringAfterLast('/')
            )
        )

        model.animationPlayer.nameToAnimationMap.keys.forEach { anim ->
            player.sendSystemMessage(Component.literal("- ").append(Component.literal(anim).apply {
                style = Style.EMPTY
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, "hollowengine.tooltips.copy".mcTranslate))
                    .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, anim))
            }))
        }

        player.sendSystemMessage(
            Component.translatable(
                "hollowengine.commands.model_textures",
                value.substringAfterLast('/')
            )
        )

        model.modelPath.textures.map { it.path.removeSuffix(".png") }.forEach { anim ->
            player.sendSystemMessage(Component.literal("- ").append(Component.literal(anim).apply {
                style = Style.EMPTY
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, "hollowengine.tooltips.copy".mcTranslate))
                    .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, anim))
            }))
        }
    }
})

fun ModScript.script() {
    fun onPlayerJoin(event: PlayerLoggedInEvent) {
        val player = event.entity as ServerPlayer
        if (player.stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) == 0) {
            runScript(player.server, FTBTeamsAPI.getPlayerTeam(player), "scripts/npc_example.se.kts".fromReadablePath())
        }
    }

    FORGE_BUS.register(::onPlayerJoin)
}