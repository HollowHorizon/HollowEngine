package ru.hollowhorizon.hollowengine.client.audio

import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent

/**
 * The one-shot sounds this client has playing.
 *
 * Looping sounds live in [SoundLoops] instead: they are addressed by id and stopped explicitly.
 */
object SoundEffects {
    private val playing = ArrayList<SoundPlayer>()

    /** Plays [sound] once, and frees it once it has finished. */
    fun play(sound: SoundBuffer, tune: SoundPlayer.() -> Unit = {}): SoundPlayer {
        val player = SoundPlayer(sound).apply(tune)
        player.play()
        playing += player
        return player
    }

    /** Drops every one-shot at once. */
    fun clear() {
        playing.forEach {
            it.stop()
            it.delete()
        }
        playing.clear()
    }

    private fun reap() {
        val iterator = playing.iterator()
        while (iterator.hasNext()) {
            val player = iterator.next()
            if (!player.hasFinished) continue
            player.delete()
            iterator.remove()
        }
    }

    private var wasInLevel = false

    @SubscribeEvent
    fun onClientTick(event: TickEvent.Client) {
        if (event.minecraft.level == null) {
            if (wasInLevel) clear()
            wasInLevel = false
            return
        }
        wasInLevel = true
        reap()
    }
}
