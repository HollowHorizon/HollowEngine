package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.await
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshotFactory
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForStringUUID
import java.util.*

data class KatariChatMessage(
    val player: Player,
    val message: String,
)

@Serializable
@SerialName("hollowengine:katari/chat_message")
@ScriptType("ChatMessage")
data class ChatMessageSnapshot(
    val uuid: @Serializable(ForStringUUID::class) UUID,
    val message: String,
) : ValueSnapshot(), ScriptSnapshot<KatariChatMessage> {
    override suspend fun restore(context: ValueRestoreContext): KatariChatMessage {
        val event =
            await<PlayerEvent.Join> { it.player.uuid == uuid }
        return KatariChatMessage(event.player, message)
    }

    companion object : ScriptSnapshotFactory<KatariChatMessage, ChatMessageSnapshot> {
        override fun capture(value: KatariChatMessage): ChatMessageSnapshot {
            return ChatMessageSnapshot(value.player.uuid, value.message)
        }
    }
}

class KatariRestoreContext(val server: MinecraftServer) : ValueRestoreContext