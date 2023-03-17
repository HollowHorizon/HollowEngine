package ru.hollowhorizon.hollowstory.story.features

import net.minecraft.client.Minecraft
import net.minecraftforge.fml.loading.FMLPaths
import ru.hollowhorizon.hollowstory.common.npcs.IHollowNPC
import ru.hollowhorizon.hollowstory.cutscenes.replay.Replay
import ru.hollowhorizon.hollowstory.cutscenes.replay.ReplayPlayer
import ru.hollowhorizon.hollowstory.cutscenes.replay.ReplayRecorder.Companion.getRecorder

interface IReplayFeature {
    fun playReplay(npc: IHollowNPC, path: String, settings: ReplayPlayer.() -> Unit = {}) {
        val player = ReplayPlayer(npc.npcEntity)
        settings(player)
        player.play(
            npc.npcEntity.level,
            Replay.fromFile(FMLPaths.GAMEDIR.get().resolve("replays").resolve(path).toFile())
        )
    }

    fun startReplaying(name: String) = getRecorder(Minecraft.getInstance().player!!).startRecording(name)
    fun stopRecording() = getRecorder(Minecraft.getInstance().player!!).stopRecording()
}