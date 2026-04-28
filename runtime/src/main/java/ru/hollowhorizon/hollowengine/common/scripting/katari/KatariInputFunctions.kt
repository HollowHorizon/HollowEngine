package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.KatariCallableSignature
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariFunctionDefinition
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariParameterType
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariTypes
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariValue
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.common.events.await
import java.util.UUID

internal val KATARI_INPUT = KatariParameterType("InputEvent")

internal fun katariInputFunctions(server: MinecraftServer): List<KatariFunctionDefinition> {
    return listOf(
        suspendable("waitKey", server, signature = memberSignature(KATARI_PLAYER, KatariTypes.Int)) { args ->
            val player = args.receiver<KatariPlayerRef>("waitKey").resolvePlayer(server)
            val key = args.getOrNull(1)?.asInt() ?: error("waitKey(key) expects key code")
            awaitInput(player.uuid.toString()) { input ->
                input.kind == KatariInputKind.Key && input.key == key && input.action == KatariInputAction.Press
            }.toKatariHost()
        },
        suspendable("waitKey", server, signature = memberSignature(KATARI_PLAYER, KatariTypes.Int, KatariTypes.Text)) { args ->
            val player = args.receiver<KatariPlayerRef>("waitKey").resolvePlayer(server)
            val key = args.getOrNull(1)?.asInt() ?: error("waitKey(key, action) expects key code")
            val action = args.getOrNull(2)?.asText().orEmpty().toInputAction()
            awaitInput(player.uuid.toString()) { input ->
                input.kind == KatariInputKind.Key && input.key == key && input.action == action
            }.toKatariHost()
        },
        suspendable("waitClick", server, signature = memberSignature(KATARI_PLAYER, KatariTypes.Int)) { args ->
            val player = args.receiver<KatariPlayerRef>("waitClick").resolvePlayer(server)
            val button = args.getOrNull(1)?.asInt() ?: error("waitClick(button) expects mouse button")
            awaitInput(player.uuid.toString()) { input ->
                input.kind == KatariInputKind.MouseButton &&
                    input.button == button &&
                    input.action == KatariInputAction.Press
            }.toKatariHost()
        },
        suspendable("waitClick", server, signature = memberSignature(KATARI_PLAYER, KatariTypes.Int, KatariTypes.Text)) { args ->
            val player = args.receiver<KatariPlayerRef>("waitClick").resolvePlayer(server)
            val button = args.getOrNull(1)?.asInt() ?: error("waitClick(button, action) expects button")
            val action = args.getOrNull(2)?.asText().orEmpty().toInputAction()
            awaitInput(player.uuid.toString()) { input ->
                input.kind == KatariInputKind.MouseButton && input.button == button && input.action == action
            }.toKatariHost()
        },
        suspendable("waitScroll", server, signature = memberSignature(KATARI_PLAYER)) { args ->
            val player = args.receiver<KatariPlayerRef>("waitScroll").resolvePlayer(server)
            awaitInput(player.uuid.toString()) { input ->
                input.kind == KatariInputKind.MouseScroll
            }.toKatariHost()
        },
        immediate("player", signature = memberSignature(KATARI_INPUT)) { args ->
            val input = args.receiver<KatariInputSnapshot>("player")
            server.playerList.getPlayer(UUID.fromString(input.playerId))?.toKatariHost() ?: KatariValue.Null
        },
        immediate("kind", signature = memberSignature(KATARI_INPUT)) { args ->
            KatariValue.Text(args.receiver<KatariInputSnapshot>("kind").kind.name)
        },
        immediate("action", signature = memberSignature(KATARI_INPUT)) { args ->
            KatariValue.Text(args.receiver<KatariInputSnapshot>("action").action.name)
        },
        immediate("key", signature = memberSignature(KATARI_INPUT)) { args ->
            KatariValue.Int32(args.receiver<KatariInputSnapshot>("key").key)
        },
        immediate("button", signature = memberSignature(KATARI_INPUT)) { args ->
            KatariValue.Int32(args.receiver<KatariInputSnapshot>("button").button)
        },
        immediate("x", signature = memberSignature(KATARI_INPUT)) { args ->
            KatariValue.Float64(args.receiver<KatariInputSnapshot>("x").x)
        },
        immediate("y", signature = memberSignature(KATARI_INPUT)) { args ->
            KatariValue.Float64(args.receiver<KatariInputSnapshot>("y").y)
        },
        immediate("scrollX", signature = memberSignature(KATARI_INPUT)) { args ->
            KatariValue.Float64(args.receiver<KatariInputSnapshot>("scrollX").scrollX)
        },
        immediate("scrollY", signature = memberSignature(KATARI_INPUT)) { args ->
            KatariValue.Float64(args.receiver<KatariInputSnapshot>("scrollY").scrollY)
        },
    )
}

internal fun KatariInputSnapshot.toKatariHost() = KatariValue.HostObject("InputEvent", this)

internal fun String.toInputAction(): KatariInputAction = when (lowercase()) {
    "", "press", "pressed", "down" -> KatariInputAction.Press
    "release", "released", "up" -> KatariInputAction.Release
    "repeat", "repeated" -> KatariInputAction.Repeat
    "scroll" -> KatariInputAction.Scroll
    else -> error("Unknown input action `$this`")
}

private suspend fun awaitInput(
    playerId: String,
    predicate: (KatariInputSnapshot) -> Boolean,
): KatariInputSnapshot {
    return await<KatariInputEvent> { event ->
        event.player.uuid.toString() == playerId && predicate(event.input)
    }.input
}
