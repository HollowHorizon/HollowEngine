package ru.hollowhorizon.hollowengine.common.scripting.dialogues

interface IDialogueExecutor {
    fun waitAction()

    fun updateScene(scene: DialogueScene)

    fun applyChoice(choices: Collection<String>): Int

    fun stop()
}