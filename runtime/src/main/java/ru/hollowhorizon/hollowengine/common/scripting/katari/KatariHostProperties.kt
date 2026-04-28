package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.KatariTypes
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariValue
import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindingsBuilder
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.utils.literal
import java.util.*

internal fun NarrativeBindingsBuilder.registerHollowKatariProperties(server: MinecraftServer) {
    entityProperties(server)
    chatMessageProperties()
    inputProperties(server)
    animatorProperties()
}

private fun NarrativeBindingsBuilder.entityProperties(server: MinecraftServer) {
    extensionProperty(
        name = "name",
        receiver = KATARI_ENTITY,
        valueType = KatariTypes.Text,
        getter = { receiver, _ -> KatariValue.Text(receiver.entity(server).name.string) },
        setter = { receiver, value, _ -> receiver.entity(server).customName = value.asText().literal },
    )
    extensionProperty(
        name = "uuid",
        receiver = KATARI_ENTITY,
        valueType = KatariTypes.Text,
        getter = { receiver, _ -> KatariValue.Text(receiver.entity(server).uuid.toString()) },
        setter = { receiver, value, _ -> receiver.entity(server).uuid = UUID.fromString(value.asText()) },
    )
    extensionProperty(
        name = "customName",
        receiver = KATARI_ENTITY,
        valueType = KatariTypes.Text.nullable(),
        getter = { receiver, _ ->
            receiver.entity(server).customName?.string?.let(KatariValue::Text) ?: KatariValue.Null
        },
        setter = { receiver, value, _ ->
            receiver.entity(server).customName = value.asText().takeIf(String::isNotBlank)?.let(Component::literal)
        },
    )
    extensionProperty(
        name = "alive",
        receiver = KATARI_ENTITY,
        valueType = KatariTypes.Boolean,
        getter = { receiver, _ -> KatariValue.Bool(receiver.entity(server).isAlive) },
    )
    extensionProperty(
        name = "invulnerable",
        receiver = KATARI_ENTITY,
        valueType = KatariTypes.Boolean,
        getter = { receiver, _ -> KatariValue.Bool(receiver.entity(server).isInvulnerable) },
        setter = { receiver, value, _ -> receiver.entity(server).isInvulnerable = value.asBool() ?: false },
    )
    extensionProperty(
        name = "sprinting",
        receiver = KATARI_ENTITY,
        valueType = KatariTypes.Boolean,
        getter = { receiver, _ -> KatariValue.Bool(receiver.entity(server).isSprinting) },
        setter = { receiver, value, _ -> receiver.entity(server).isSprinting = value.asBool() ?: false },
    )
    extensionProperty(
        name = "health",
        receiver = KATARI_ENTITY,
        valueType = KatariTypes.Double,
        getter = { receiver, _ ->
            KatariValue.Float64((receiver.entity(server) as? LivingEntity)?.health?.toDouble() ?: 0.0)
        },
        setter = { receiver, value, _ ->
            (receiver.entity(server) as? LivingEntity)?.health = (value.asDouble() ?: 0.0).toFloat()
        },
    )
    extensionProperty(
        name = "position",
        receiver = KATARI_ENTITY,
        valueType = KATARI_POSITION,
        getter = { receiver, _ -> receiver.entity(server).position().toPositionRef().toKatariHost() },
    )
    extensionProperty(
        name = "dimension",
        receiver = KATARI_ENTITY,
        valueType = KatariTypes.Text,
        getter = { receiver, _ -> KatariValue.Text(receiver.entity(server).level().dimension().location().toString()) },
    )
    extensionProperty(
        name = "mainHand",
        receiver = KATARI_ENTITY,
        valueType = KatariTypes.Text,
        getter = { receiver, _ -> KatariValue.Text(receiver.entity(server).mainHandDescription()) },
    )
}

private fun NarrativeBindingsBuilder.chatMessageProperties() {
    extensionProperty(
        name = "player",
        receiver = KATARI_CHAT_MESSAGE,
        valueType = KATARI_PLAYER,
        getter = { receiver, _ -> KatariValue.HostObject("PlayerRef", receiver.chat().player) },
    )
    extensionProperty(
        name = "text",
        receiver = KATARI_CHAT_MESSAGE,
        valueType = KatariTypes.Text,
        getter = { receiver, _ -> KatariValue.Text(receiver.chat().message) },
    )
}

private fun NarrativeBindingsBuilder.inputProperties(server: MinecraftServer) {
    extensionProperty(
        name = "player",
        receiver = KATARI_INPUT,
        valueType = KATARI_PLAYER.nullable(),
        getter = { receiver, _ ->
            val input = receiver.input()
            server.playerList.getPlayer(UUID.fromString(input.playerId))?.toKatariHost() ?: KatariValue.Null
        },
    )
    inputTextProperty("kind") { kind.name }
    inputTextProperty("action") { action.name }
    inputIntProperty("key") { key }
    inputIntProperty("scanCode") { scanCode }
    inputIntProperty("button") { button }
    inputDoubleProperty("x") { x }
    inputDoubleProperty("y") { y }
    inputDoubleProperty("scrollX") { scrollX }
    inputDoubleProperty("scrollY") { scrollY }
}

private fun NarrativeBindingsBuilder.animatorProperties() {
    extensionProperty(
        name = "enabled",
        receiver = KATARI_ANIMATOR,
        valueType = KatariTypes.Boolean,
        getter = { receiver, _ -> KatariValue.Bool(receiver.animator().enabled) },
        setter = { receiver, value, _ -> receiver.animator().setEnabled(value.asBool() ?: true) },
    )
}

private fun NarrativeBindingsBuilder.inputTextProperty(name: String, getter: KatariInputSnapshot.() -> String) {
    extensionProperty(name, KATARI_INPUT, KatariTypes.Text, getter = { receiver, _ ->
        KatariValue.Text(receiver.input().getter())
    })
}

private fun NarrativeBindingsBuilder.inputIntProperty(name: String, getter: KatariInputSnapshot.() -> Int) {
    extensionProperty(name, KATARI_INPUT, KatariTypes.Int, getter = { receiver, _ ->
        KatariValue.Int32(receiver.input().getter())
    })
}

private fun NarrativeBindingsBuilder.inputDoubleProperty(name: String, getter: KatariInputSnapshot.() -> Double) {
    extensionProperty(name, KATARI_INPUT, KatariTypes.Double, getter = { receiver, _ ->
        KatariValue.Float64(receiver.input().getter())
    })
}

private fun KatariValue.entity(server: MinecraftServer) =
    asHost<KatariEntityRef>("Any", "entity property").resolveNow(server) ?: error("Entity is not available")

private fun KatariValue.chat() = asHost<KatariChatMessage>("ChatMessage", "chat message property")

private fun KatariValue.input() = asHost<KatariInputSnapshot>("InputEvent", "input property")

private fun KatariValue.animator() = asHost<KatariAnimatorBuilder>("AnimatorController", "animator property")

private fun net.minecraft.world.entity.Entity.mainHandDescription(): String {
    val stack = (this as? LivingEntity)?.mainHandItem ?: ItemStack.EMPTY
    return if (stack.isEmpty) "" else stack.item.builtInRegistryHolder().key().location().toString()
}
