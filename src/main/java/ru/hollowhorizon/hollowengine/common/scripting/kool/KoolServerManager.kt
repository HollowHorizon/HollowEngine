package ru.hollowhorizon.hollowengine.common.scripting.kool

import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hollowengine.common.capabilities.HollowCapability
import ru.hollowhorizon.hollowengine.common.coroutines.scopeAsync
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.get
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import kotlin.script.experimental.api.valueOrThrow

object KoolServerManager {
    fun updateScene(player: ServerPlayer, name: String, tag: CompoundTag = CompoundTag()) {
        player[KoolScenesCapability::class].scenes[name] = KoolScenesCapability.Wrapper(tag)
        UpdateScenePacket(name, tag).send(player)
    }

    fun removeScene(player: ServerPlayer, name: String) {
        player[KoolScenesCapability::class].scenes.remove(name)
        RemoveScenePacket(name).send(player)
    }
}

@SubscribeEvent
fun onPlayerJoin(event: PlayerEvent.Join) {
    event.player[KoolScenesCapability::class].scenes.forEach { script, tag ->
        UpdateScenePacket(script, tag.tag).send(event.player as ServerPlayer)
    }
}

@HollowCapability(Player::class)
class KoolScenesCapability : CapabilityInstance() {
    val scenes by syncableMap<String, Wrapper>()

    @Serializable
    class Wrapper(val tag: @Serializable(ForCompoundNBT::class) CompoundTag)
}

@HollowPacketHandler
@Serializable
class UpdateScenePacket(val name: String, val tag: @Serializable(ForCompoundNBT::class) CompoundTag) :
    HollowPacket {
    override fun handle(player: Player) {
        val file = name.fromReadablePath()

        if (name in KoolClientManager) {
            KoolClientManager.updateScene(name, tag)
            return
        }

        scopeAsync {
            val jar = ScriptingCompiler.compileFile<KoolScript>(file)

            val result = jar.execute()
            val event = result.valueOrThrow().returnValue.scriptInstance as? KoolScript
                ?: error("Script instance is null")

            event.tag = tag

            KoolClientManager.addScene(name, event)
        }
    }
}

@HollowPacketHandler
@Serializable
class RemoveScenePacket(val name: String) : HollowPacket {
    override fun handle(player: Player) {
        KoolClientManager.removeScene(name)
    }
}