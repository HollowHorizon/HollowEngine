package ru.hollowhorizon.hollowengine.common.scripting.story

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.nbt.ForUuid
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowengine.common.npcs.ICharacter
import java.util.*

private val Player.scriptName: String?
    get() = if (this.persistentData.contains("hs_name")) this.persistentData.getString("hs_name") else null

@Serializable
class StoryPlayer(val uuid: @Serializable(ForUuid::class) UUID) : ICharacter {
    constructor(player: Player) : this(player.uuid) {
        this.mcPlayer = player
    }

    @Transient
    var mcPlayer: Player? =
        if (FMLEnvironment.dist.isClient) {
            Minecraft.getInstance().singleplayerServer?.playerList?.getPlayer(uuid)
        } else {
            ServerLifecycleHooks.getCurrentServer().playerList.getPlayer(uuid)
        }
        set(value) {
            field = value
            world = mcPlayer?.commandSenderWorld ?: ServerLifecycleHooks.getCurrentServer().overworld()
        }

    @Transient
    var world: Level? =
        if (FMLEnvironment.dist.isClient) {
            Minecraft.getInstance().level
        } else {
            mcPlayer?.commandSenderWorld ?: ServerLifecycleHooks.getCurrentServer().overworld()
        }

    var name: String = mcPlayer?.name?.string ?: "Unknown"
    var isHost: Boolean = false

    infix fun send(message: String) {
        if (mcPlayer == null) {
            HollowCore.LOGGER.warn("Player $name is not online!")
            return
        }
        mcPlayer?.sendMessage(message.toSTC(), mcPlayer!!.uuid)
    }

    fun isOnline(): Boolean {
        return mcPlayer != null
    }

    fun isOffline(): Boolean {
        return mcPlayer == null
    }

    fun pos(): Position {
        return if (mcPlayer == null) {
            HollowCore.LOGGER.warn("Player $name is not online!")
            Position(0, 0, 0)
        } else {
            val pos = mcPlayer!!.blockPosition()
            Position(pos.x, pos.y, pos.z)
        }
    }

    fun distTo(x: Int, y: Int, z: Int): Float {
        return Mth.sqrt(distToSqr(x, y, z))
    }

    fun distToSqr(x: Int, y: Int, z: Int): Float {
        return this.mcPlayer?.distanceToSqr(x.toDouble(), y.toDouble(), z.toDouble())?.toFloat() ?: run {
            HollowCore.LOGGER.warn("Player $name is not online!")
            0f
        }
    }

    fun distToSqr(x: Double, y: Double, z: Double): Float {
        return this.mcPlayer?.distanceToSqr(x, y, z)?.toFloat() ?: run {
            HollowCore.LOGGER.warn("Player $name is not online!")
            0f
        }
    }

    data class Position(val x: Int, val y: Int, val z: Int)

    override val characterName: String
        get() = this.name
    override val entityType: Player
        get() = this.mcPlayer!!
}