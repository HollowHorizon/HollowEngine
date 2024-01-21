package ru.hollowhorizon.hollowengine.cutscenes.replay

import com.mojang.math.Vector3d
import kotlinx.serialization.Serializable
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserialize
import ru.hollowhorizon.hc.client.utils.nbt.loadAsNBT
import ru.hollowhorizon.hc.client.utils.nbt.serialize
import ru.hollowhorizon.hc.client.utils.toIS
import java.io.File

@Serializable
class Replay {
    val points: ArrayList<ReplayFrame> = ArrayList()

    fun addPoint(point: ReplayFrame) {
        points.add(point)
    }

    fun addPointFromPlayer(recorder: ReplayRecorder, player: Player) {
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
        fun fromNBT(nbt: Tag): Replay {
            return NBTFormat.deserialize(nbt)
        }

        fun toNBT(replay: Replay): Tag {
            return NBTFormat.serialize(replay)
        }

        fun fromResourceLocation(location: ResourceLocation): Replay {
            return fromNBT(location.toIS().loadAsNBT())
        }

        fun fromFile(path: File): Replay {
            return fromNBT(path.inputStream().loadAsNBT())
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
                it.isSwinging,
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