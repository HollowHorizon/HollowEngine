package ru.hollowhorizon.hollowengine.story.extensions

import net.minecraft.client.Minecraft
import net.minecraftforge.fml.loading.FMLPaths
import ru.hollowhorizon.hollowengine.common.npcs.IHollowNPC
import ru.hollowhorizon.hollowengine.cutscenes.replay.Replay
import ru.hollowhorizon.hollowengine.cutscenes.replay.ReplayPlayer
import ru.hollowhorizon.hollowengine.cutscenes.replay.ReplayRecorder
import ru.hollowhorizon.hollowengine.story.StoryEvent

fun StoryEvent.playReplay(npc: IHollowNPC, path: String, settings: ReplayPlayer.() -> Unit = {}) {
    val player = ReplayPlayer(npc.npcEntity)
    settings(player)
    player.play(
        npc.npcEntity.level,
        Replay.fromFile(FMLPaths.GAMEDIR.get().resolve("replays").resolve(path).toFile())
    )
}

fun StoryEvent.startReplaying(name: String) = ReplayRecorder.getRecorder(Minecraft.getInstance().player!!).startRecording(name)
fun StoryEvent.stopRecording() = ReplayRecorder.getRecorder(Minecraft.getInstance().player!!).stopRecording()