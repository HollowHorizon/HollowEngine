package ru.hollowhorizon.hollowstory.cutscenes.replay

import kotlinx.serialization.Serializable
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.INBT
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.vector.Vector3d
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserialize
import ru.hollowhorizon.hc.client.utils.nbt.loadAsNBT
import ru.hollowhorizon.hc.client.utils.nbt.serialize
import ru.hollowhorizon.hc.client.utils.toIS

@Serializable
class Replay {
    val points: ArrayList<ReplayFrame> = ArrayList()

    fun addPoint(point: ReplayFrame) {
        points.add(point)
    }

    fun addPointFromPlayer(recorder: ReplayRecorder, player: PlayerEntity) {
        points.add(ReplayFrame.loadFromPlayer(recorder, player))
    }

    fun clear() {
        points.clear()
    }

    fun copy(): Replay {
        val newReplay = Replay()
        newReplay.points.addAll(points)
        return newReplay
    }

    companion object {
        fun fromNBT(nbt: INBT): Replay {
            return NBTFormat.deserialize(nbt)
        }

        fun toNBT(replay: Replay): INBT {
            return NBTFormat.serialize(replay)
        }

        fun fromResourceLocation(location: ResourceLocation): Replay {
            return fromNBT(location.toIS().loadAsNBT())
        }
    }
}

fun Replay.offset(startPosition: Vector3d): Replay {
    val newReplay = copy()

    val firstPoint = newReplay.points.first()

    val newPoints = arrayListOf<ReplayFrame>()

    newReplay.points.forEach {
        newPoints.add(
            ReplayFrame(
                it.x - firstPoint.x + startPosition.x,
                it.y - firstPoint.y + startPosition.y,
                it.z - firstPoint.z + startPosition.z,

                it.yaw,
                it.headYaw,
                it.pitch,

                it.motionX,
                it.motionY,
                it.motionZ,

                it.isSneaking,
                it.isSprinting,
                it.pose,

                it.brokenBlocks,
                it.placedBlocks,
                it.usedBlocks,

                it.armorAndWeapon
            )
        )
    }
    newReplay.points.clear()
    newReplay.points.addAll(newPoints)
    return newReplay
}