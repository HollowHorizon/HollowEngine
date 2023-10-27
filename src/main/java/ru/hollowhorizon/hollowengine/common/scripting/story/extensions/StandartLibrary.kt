package ru.hollowhorizon.hollowengine.common.scripting.story.extensions

/**
 * Если в параметрах ничего, то принимается любое сообщение, если есть строки, то допускаются только они, другие сообщения игнорируются
 */
//fun StoryEvent.input(vararg values: String, onlyHostMode: Boolean = false): String {
//    var input = ""
//
//    if(team.getHost().isOnline()) OpenChatPacket().send("", team.getHost().mcPlayer!!)
//    else if(!onlyHostMode) OpenChatPacket().send("", *team.getAllOnline().map { it.mcPlayer!! }.toTypedArray())
//
//    fun canChoice(player: ServerPlayer): Boolean {
//        return if(onlyHostMode) team.isHost(player) else team.isFromTeam(player)
//    }
//
//    waitForgeEvent<ServerChatEvent> { event ->
//        input = event.message.string
//
//        return@waitForgeEvent (values.isEmpty() || values.any { it.equals(input, true) }) || !canChoice(event.player)
//
//    }
//
//    return input
//}