package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.*

enum class KatariInputAction {
    Press,
    Release,
    Repeat,
    Scroll,
}

enum class KatariInputKind {
    Key,
    MouseButton,
    MouseScroll,
}

@Serializable
@SerialName("hollowengine:katari_input")
@ScriptBinding("InputEvent")
@ScriptType("InputEvent")
data class KatariInputSnapshot @ScriptIgnore constructor(
    @property:ScriptIgnore val playerId: String,
    val kind: KatariInputKind,
    val action: KatariInputAction,
    val key: Int = -1,
    val scanCode: Int = -1,
    val button: Int = -1,
    val modifiers: Int = 0,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val scrollX: Double = 0.0,
    val scrollY: Double = 0.0,
) : ValueSnapshot(), ScriptSnapshot<KatariInputSnapshot> {
    @ScriptIgnore
    override suspend fun restore(context: ValueRestoreContext): KatariInputSnapshot {
        return this
    }

    companion object : ScriptSnapshotFactory<KatariInputSnapshot, KatariInputSnapshot> {
        override fun capture(value: KatariInputSnapshot): KatariInputSnapshot {
            return value
        }
    }
}

data class KatariInputEvent(
    val player: ServerPlayer,
    val input: KatariInputSnapshot,
) : Event {
    companion object : EventHandler<KatariInputEvent>()
}

sealed class KatariClientInputEvent : ClientEvent {
    abstract val action: KatariInputAction
    abstract val modifiers: Int

    data class Key(
        val key: Int,
        val scanCode: Int,
        override val action: KatariInputAction,
        override val modifiers: Int,
    ) : KatariClientInputEvent() {
        companion object : EventHandler<Key>()
    }

    data class MouseButton(
        val x: Double,
        val y: Double,
        val button: Int,
        override val action: KatariInputAction,
        override val modifiers: Int,
    ) : KatariClientInputEvent() {
        companion object : EventHandler<MouseButton>()
    }

    data class MouseScroll(
        val x: Double,
        val y: Double,
        val scrollX: Double,
        val scrollY: Double,
        override val action: KatariInputAction = KatariInputAction.Scroll,
        override val modifiers: Int = 0,
    ) : KatariClientInputEvent() {
        companion object : EventHandler<MouseScroll>()
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
data class KatariInputPacket(
    private val input: KatariInputSnapshot,
) : HollowPacket {
    override fun handle(player: Player) {
        val serverPlayer = player as? ServerPlayer ?: return
        KatariInputEvent.post(KatariInputEvent(serverPlayer, input.copy(playerId = serverPlayer.uuid.toString())))
    }

    internal fun snapshotForTests(): KatariInputSnapshot = input
}

internal fun KatariClientInputEvent.toPacket(playerId: String = ""): KatariInputPacket {
    val snapshot = when (this) {
        is KatariClientInputEvent.Key -> KatariInputSnapshot(
            playerId = playerId,
            kind = KatariInputKind.Key,
            action = action,
            key = key,
            scanCode = scanCode,
            modifiers = modifiers,
        )

        is KatariClientInputEvent.MouseButton -> KatariInputSnapshot(
            playerId = playerId,
            kind = KatariInputKind.MouseButton,
            action = action,
            button = button,
            modifiers = modifiers,
            x = x,
            y = y,
        )

        is KatariClientInputEvent.MouseScroll -> KatariInputSnapshot(
            playerId = playerId,
            kind = KatariInputKind.MouseScroll,
            action = action,
            x = x,
            y = y,
            scrollX = scrollX,
            scrollY = scrollY,
        )
    }
    return KatariInputPacket(snapshot)
}
