package ru.hollowhorizon.hollowengine.cutscenes.replay

import net.minecraft.entity.LivingEntity
import net.minecraft.world.GameType
import net.minecraft.world.World
import net.minecraft.world.server.ServerWorld
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.capabilities.ACTIVE_REPLAYS

class ReplayPlayer(val target: LivingEntity) {
    var replay: Replay? = null
    var currentPoint: Int = 0
    var isPlaying: Boolean = false
    var isPaused: Boolean = false
    var isLooped: Boolean = true
    var isStopped: Boolean = false
    var applyWorldChanges = true
    var waitOthersReplays = true
    var saveEntity = false
    val fakePlayer: FakePlayer = FakePlayerFactory.getMinecraft(target.level as ServerWorld).also {
        it.setGameMode(GameType.CREATIVE)
    }

    fun play(world: World, replay: Replay) {
        reset()

        ACTIVE_REPLAYS.add(this)

        if (isStopped) throw IllegalStateException("ReplayPlayer is stopped")
        this.target.isInvulnerable = true
        world.addFreshEntity(this.target)
        this.target.moveTo(
            replay.points[0].x,
            replay.points[0].y,
            replay.points[0].z,
            replay.points[0].yaw,
            replay.points[0].pitch
        )
        this.replay = replay
        isPlaying = true
        isPaused = false
        MinecraftForge.EVENT_BUS.register(this)
    }

    fun togglePause() {
        isPaused = !isPaused
    }

    fun resume() {
        isPaused = false
    }

    fun destroy() {
        reset()
        isPlaying = false
        isPaused = false
        if(!saveEntity) target.remove()
        MinecraftForge.EVENT_BUS.unregister(this)
    }

    fun destroyWithClear() {
        destroy()
        ACTIVE_REPLAYS.remove(this)
    }

    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.END && isPlaying) {
            update()
        }
    }

    fun update() {
        if (isPlaying && !isPaused) {
            if (currentPoint < replay!!.points.size) {
                val point = replay!!.points[currentPoint]
                point.apply(target, fakePlayer)
                currentPoint++
            } else {
                if(!waitOthersReplays) {
                    if (isLooped) {
                        reset()
                    } else {
                        destroyWithClear()
                    }
                } else {
                    isPaused = true
                }
            }
        }

        if(isPaused && waitOthersReplays) {
            if(ACTIVE_REPLAYS.all { it.isPaused || it.isStopped }) {
                waitOthersReplays = false
                isPaused = false
            }
        }
    }

    fun reset() {
        currentPoint = 0

        if (!applyWorldChanges || isLooped) {
            replay?.points?.forEach { point ->
                point.brokenBlocks.forEach { block ->
                    block.placeWorld(target.level)
                }
                point.placedBlocks.forEach { block ->
                    block.destroyWorld(target.level)
                }
            }
        }
    }
}