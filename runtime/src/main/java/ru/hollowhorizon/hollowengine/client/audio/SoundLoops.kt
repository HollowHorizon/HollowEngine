package ru.hollowhorizon.hollowengine.client.audio

import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent

/**
 * The looping sounds this client has running, keyed by the id the server named them with.
 */
object SoundLoops {
    private class Loop(val player: SoundPlayer, var target: Float, var fadeIn: Float, var fadeOut: Float) {
        var volume = 0f
        var stopping = false
    }

    private val loops = HashMap<String, Loop>()

    /**
     * Starts the loop [id], or retunes it when it is already running - a second start is how a script
     * changes volume or position without a gap in the sound.
     */
    fun start(
        id: String,
        wave: Wave,
        volume: Float,
        pitch: Float,
        fadeIn: Float,
        fadeOut: Float,
        position: Vec3?,
        relative: Boolean,
    ) {
        val existing = loops[id]
        if (existing != null) {
            existing.target = volume
            existing.fadeIn = fadeIn
            existing.fadeOut = fadeOut
            existing.stopping = false
            existing.player.tune(pitch, position, relative)
            return
        }

        val player = SoundPlayer(SoundBuffer(wave))
        player.setLooping(true)
        player.setVolume(0f)
        player.tune(pitch, position, relative)
        player.play()
        loops[id] = Loop(player, volume, fadeIn, fadeOut)
    }

    /** Fades the loop [id] out and frees it once it is silent. Unknown ids are ignored. */
    fun stop(id: String) {
        loops[id]?.stopping = true
    }

    /** Drops every loop at once, without a fade; for leaving a world. */
    fun clear() {
        loops.values.forEach { it.player.release() }
        loops.clear()
    }

    private fun tick(seconds: Float) {
        if (loops.isEmpty()) return
        val finished = ArrayList<String>()
        loops.forEach { (id, loop) ->
            val goal = if (loop.stopping) 0f else loop.target
            val fade = if (loop.stopping) loop.fadeOut else loop.fadeIn
            loop.volume = approach(loop.volume, goal, fade, seconds)
            loop.player.setVolume(loop.volume)
            if (loop.stopping && loop.volume <= 0f) {
                loop.player.release()
                finished += id
            }
        }
        finished.forEach(loops::remove)
    }

    /** Moves [current] towards [goal] at the rate a full ramp over [fade] seconds implies. */
    private fun approach(current: Float, goal: Float, fade: Float, seconds: Float): Float {
        if (fade <= 0f) return goal
        val step = seconds / fade
        return if (current < goal) (current + step).coerceAtMost(goal) else (current - step).coerceAtLeast(goal)
    }

    private fun SoundPlayer.tune(pitch: Float, position: Vec3?, relative: Boolean) {
        setPitch(pitch)
        setRelative(relative)
        position?.let { setPosition(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) }
    }

    private fun SoundPlayer.release() {
        stop()
        delete()
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
        tick(TICK_SECONDS)
    }

    private const val TICK_SECONDS = 0.05f
}
