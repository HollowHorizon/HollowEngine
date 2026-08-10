import ru.hollowhorizon.hollowengine.common.dialogue.DialoguePhase
import ru.hollowhorizon.hollowengine.common.dialogue.DialogueUiKeys

screen("hollowengine:dialogue") {
    title = "Dialogue"
    closeOnEscape = false
    pausesGame = false

    content {
        val line = data[DialogueUiKeys.Line]
        val choices = data[DialogueUiKeys.Choices]
        val closing = data[DialogueUiKeys.Phase] == DialoguePhase.CLOSING

        var root = Modifier
            .style("hollowengine:ui/styles/dialogue.hss")
            .focusScope()
            .input(clickable = true)
            .onClick { send { putString(DialogueUiKeys.Action, DialogueUiKeys.AdvanceAction) } }
        if (closing) root = root.state(UiState.CLOSING)

        Box(id = "dialogue-root", mode = UiBoxMode.FREE, modifier = root) {
            if (choices.options.isNotEmpty()) {
                Column(id = "choice-list") {
                    choices.options.forEach { option ->
                        val voted = choices.myVote >= 0
                        var button = Modifier
                        if (voted && choices.myVote != option.index) button = button.state(UiState.DISABLED)
                        if (choices.myVote == option.index) button = button.state(UiState.SELECTED)
                        if (!voted) {
                            button = button.input(hoverable = true, clickable = true).onClick {
                                send {
                                    putString(DialogueUiKeys.Action, DialogueUiKeys.ChooseAction)
                                    putInt(DialogueUiKeys.Index, option.index)
                                }
                            }
                        }

                        Box(tags = listOf("choice"), modifier = button) {
                            Text(option.text, tags = listOf("choice-text"))
                        }
                    }
                }
            }

            Column(id = "dialogue-box") {
                if (line.speaker.isNotEmpty()) {
                    Box(id = "dialogue-name") { Text(line.speaker, tags = listOf("dialogue-name-text")) }
                }
                Text(line.text, tags = listOf("dialogue-text"))
            }
        }
    }
}
