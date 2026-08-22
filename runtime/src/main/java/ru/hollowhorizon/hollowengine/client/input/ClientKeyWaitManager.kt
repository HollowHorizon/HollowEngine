package ru.hollowhorizon.hollowengine.client.input

import org.lwjgl.glfw.GLFW.GLFW_RELEASE
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.player.PlayerKeyPressPacket

/** Tracks server requests for the next physical key press on this client. */
object ClientKeyWaitManager {
    private val requests = LinkedHashMap<Long, Int?>()

    fun request(requestId: Long, key: Int?) {
        requests[requestId] = key
    }

    fun cancel(requestId: Long) {
        requests -= requestId
    }

    /** Sends exactly one reply for every active request and does not consume the key. */
    fun handleKey(key: Int, action: Int) {
        if (action != GLFW_RELEASE || requests.isEmpty()) return

        val matchingRequests = requests.filterValues { expectedKey -> expectedKey == null || expectedKey == key }.keys.toList()
        if (matchingRequests.isEmpty()) return

        matchingRequests.forEach(requests::remove)
        PlayerKeyPressPacket(matchingRequests, key).send()
    }
}
