package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.KatariTypes
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariValue
import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindingsBuilder

internal fun NarrativeBindingsBuilder.registerHollowKatariProperties() {
    chatMessageProperties()
    animatorProperties()
}

private fun NarrativeBindingsBuilder.chatMessageProperties() {
    extensionProperty(
        name = "player",
        receiver = KATARI_CHAT_MESSAGE,
        valueType = KATARI_PLAYER,
        getter = { receiver, _ -> KatariValue.HostObject("Player", receiver.chat().player) },
    )
    extensionProperty(
        name = "text",
        receiver = KATARI_CHAT_MESSAGE,
        valueType = KatariTypes.Text,
        getter = { receiver, _ -> KatariValue.Text(receiver.chat().message) },
    )
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

private fun KatariValue.chat() = asHost<KatariChatMessage>("ChatMessage", "chat message property")

private fun KatariValue.animator() = asHost<KatariAnimatorBuilder>("AnimatorController", "animator property")
