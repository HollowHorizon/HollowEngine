package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.KatariFunctionDefinition
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariParameterType
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariTypes
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariValue
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.factory.await

internal val KATARI_INPUT = KatariParameterType("InputEvent")

internal fun katariInputFunctions(server: MinecraftServer): List<KatariFunctionDefinition> {
    return listOf(
        suspendable(
            "waitKey",
            server,
            signature = memberSignature(KATARI_PLAYER, KatariTypes.Int).returns(KATARI_INPUT)
        ) { args ->
            val player = args.receiver<Player>("waitKey")
            val key = args.getOrNull(1)?.asInt() ?: error("waitKey(key) expects key code")
            awaitInput(player.uuid.toString()) { input ->
                input.kind == KatariInputKind.Key && input.key == key && input.action == KatariInputAction.Press
            }.toKatariHost()
        },
        suspendable(
            "waitKey",
            server,
            signature = namedMemberSignature(
                KATARI_PLAYER,
                KATARI_INPUT,
                KatariTypes.Int.param("key"),
                KatariTypes.Text.param("action", KatariValue.Text("press")),
            ),
        ) { args ->
            val player = args.receiver<Player>("waitKey")
            val key = args.getOrNull(1)?.asInt() ?: error("waitKey(key, action) expects key code")
            val action = args.getOrNull(2)?.asText().orEmpty().toInputAction()
            awaitInput(player.uuid.toString()) { input ->
                input.kind == KatariInputKind.Key && input.key == key && input.action == action
            }.toKatariHost()
        },
        suspendable(
            "waitClick",
            server,
            signature = memberSignature(KATARI_PLAYER, KatariTypes.Int).returns(KATARI_INPUT)
        ) { args ->
            val player = args.receiver<Player>("waitClick")
            val button = args.getOrNull(1)?.asInt() ?: error("waitClick(button) expects mouse button")
            awaitInput(player.uuid.toString()) { input ->
                input.kind == KatariInputKind.MouseButton &&
                        input.button == button &&
                        input.action == KatariInputAction.Press
            }.toKatariHost()
        },
        suspendable(
            "waitClick",
            server,
            signature = namedMemberSignature(
                KATARI_PLAYER,
                KATARI_INPUT,
                KatariTypes.Int.param("button"),
                KatariTypes.Text.param("action", KatariValue.Text("press")),
            ),
        ) { args ->
            val player = args.receiver<Player>("waitClick")
            val button = args.getOrNull(1)?.asInt() ?: error("waitClick(button, action) expects button")
            val action = args.getOrNull(2)?.asText().orEmpty().toInputAction()
            awaitInput(player.uuid.toString()) { input ->
                input.kind == KatariInputKind.MouseButton && input.button == button && input.action == action
            }.toKatariHost()
        },
        suspendable("waitScroll", server, signature = memberSignature(KATARI_PLAYER).returns(KATARI_INPUT)) { args ->
            val player = args.receiver<Player>("waitScroll")
            awaitInput(player.uuid.toString()) { input ->
                input.kind == KatariInputKind.MouseScroll
            }.toKatariHost()
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

internal suspend fun awaitInput(
    playerId: String,
    predicate: (KatariInputSnapshot) -> Boolean,
): KatariInputSnapshot {
    return KatariInputEvent.await { event ->
        event.player.uuid.toString() == playerId && predicate(event.input)
    }.input
}
