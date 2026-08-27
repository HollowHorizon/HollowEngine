// Запуск: /hollowengine scripting run hollowengine-debug-command:formatted_dialogue_demo.node.kts
// Повторный запуск: сначала /hollowengine scripting stop с тем же путём.

onStart {
    val player = server.playerList.players.firstOrNull() ?: return@onStart
    DialogueController("hollowengine-debug-command:formatted_dialogue.story").start(player) {
        character("Виталик", DialogueCharacter.of("Виталик"))
    }
}
