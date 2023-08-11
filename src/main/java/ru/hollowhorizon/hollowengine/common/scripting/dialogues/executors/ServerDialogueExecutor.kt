package ru.hollowhorizon.hollowengine.common.scripting.dialogues.executors

import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.scripting.dialogues.IDialogueExecutor
import ru.hollowhorizon.hollowengine.common.scripting.story.waitForgeEvent

class ServerDialogueExecutor(val player: Player) : IDialogueExecutor {
    init {
        OpenScreenPacket().send(0, player)
    }

    override fun waitAction() {
        WaitActionPacketS2C().send(0, player)
        waitForgeEvent<WaitActionEvent> { it.player == player }
    }

    override fun updateScene(scene: ru.hollowhorizon.hollowengine.common.scripting.dialogues.DialogueScene) {
        UpdateScenePacket().send(scene, player)
        scene.actions.clear() //Необходимо очищать действия, иначе они будут накапливаться
    }

    override fun applyChoice(choices: Collection<String>): Int {
        ApplyChoicePacketS2C().send(ChoicesContainer(choices), player)
        var choice = 0
        waitForgeEvent<ApplyChoiceEvent> {
            choice = it.choice
            it.player == player
        }
        return choice
    }

    override fun stop() {
        CloseScreenPacket().send(0, player)
    }
}