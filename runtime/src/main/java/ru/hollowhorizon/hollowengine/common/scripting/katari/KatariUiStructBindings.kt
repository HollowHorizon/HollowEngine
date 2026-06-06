package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindingsBuilder
import com.sunnychung.lib.multiplatform.kotlite.model.CustomFunctionParameter
import com.sunnychung.lib.multiplatform.kotlite.model.IntValue
import com.sunnychung.lib.multiplatform.kotlite.model.NullValue
import com.sunnychung.lib.multiplatform.kotlite.model.RuntimeValue
import com.sunnychung.lib.multiplatform.kotlite.model.STRUCT_VALUE_TYPE_ID
import com.sunnychung.lib.multiplatform.kotlite.model.StringValue
import com.sunnychung.lib.multiplatform.kotlite.model.StructValue
import com.sunnychung.lib.multiplatform.kotlite.model.XmlValue
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.ui.scripting.KatariUiDisplayMode
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlTree
import ru.hollowhorizon.hollowengine.client.ui.xml.from
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.KatariGeneratedBindingRuntime

fun NarrativeBindingsBuilder.registerKatariUiStructBindings() {
    fun screenFunction(name: String, mode: KatariUiDisplayMode) {
        fun registerScreenFunction(
            playerType: String,
            sender: suspend (KatariUiDocument, RuntimeValue, StructValue) -> Unit,
        ) = immediateFunction(
            name = name,
            receiverType = "Ui",
            valueParameters = listOf(
                CustomFunctionParameter("player", playerType),
                CustomFunctionParameter("variables", STRUCT_VALUE_TYPE_ID),
            ),
        ) { arguments, _ ->
            val ui = KatariGeneratedBindingRuntime.awaitHost<KatariUiDocument>(arguments[0], "Ui", "receiver")
            val variables = arguments[2] as? StructValue ?: error("$name variables expects StructValue")
            sender(ui, arguments[1], variables)
            NullValue
        }

        registerScreenFunction("Player") { ui, playerValue, variables ->
            val player = KatariGeneratedBindingRuntime.awaitHost<Player>(playerValue, "Player", "player")
            ui.send(player, mode, variables.toCompoundTag())
        }
        registerScreenFunction("List<Player>") { ui, playersValue, variables ->
            val players = KatariGeneratedBindingRuntime.awaitList(playersValue, "players") { value, index ->
                KatariGeneratedBindingRuntime.awaitHost<Player>(value, "Player", "players[$index]")
            }
            ui.send(players, mode, variables.toCompoundTag())
        }
    }

    screenFunction("openScreen", KatariUiDisplayMode.SCREEN)
    screenFunction("showScreen", KatariUiDisplayMode.SCREEN)
    screenFunction("showOverlay", KatariUiDisplayMode.OVERLAY)
    registerXmlAttributeMutations()
    register(KatariUiAwaitCallable)
}

private fun NarrativeBindingsBuilder.registerXmlAttributeMutations() {
    immediateFunction(
        name = "insertAt",
        receiverType = "Ui",
        returnType = "Ui",
        valueParameters = xmlTargetChildAttributesParameters(),
    ) { arguments, _ ->
        val ui = arguments.uiReceiver()
        ui.insertAt(arguments.target(), UiXmlTree.from(arguments.child()), arguments.attributes())
        arguments[0]
    }
    immediateFunction(
        name = "replaceAt",
        receiverType = "Ui",
        returnType = "Ui",
        valueParameters = xmlTargetChildAttributesParameters(),
    ) { arguments, _ ->
        val ui = arguments.uiReceiver()
        ui.replaceAt(arguments.target(), UiXmlTree.from(arguments.child()), arguments.attributes())
        arguments[0]
    }
    immediateFunction(
        name = "replaceChildrenAt",
        receiverType = "Ui",
        returnType = "Ui",
        valueParameters = xmlTargetChildAttributesParameters(),
    ) { arguments, _ ->
        val ui = arguments.uiReceiver()
        ui.replaceChildrenAt(arguments.target(), UiXmlTree.from(arguments.child()), arguments.attributes())
        arguments[0]
    }
    immediateFunction(
        name = "modify",
        receiverType = "Ui",
        returnType = "Ui",
        valueParameters = targetAttributesParameters(),
    ) { arguments, _ ->
        arguments.uiReceiver().modify(arguments.target(), arguments.attributes())
        arguments[0]
    }
    immediateFunction(
        name = "modifyAll",
        receiverType = "Ui",
        returnType = "Int",
        valueParameters = targetAttributesParameters(),
    ) { arguments, context ->
        IntValue(arguments.uiReceiver().modifyAll(arguments.target(), arguments.attributes()), context.symbolTable)
    }
}

private fun xmlTargetChildAttributesParameters(): List<CustomFunctionParameter> {
    return listOf(
        CustomFunctionParameter("target", "String"),
        CustomFunctionParameter("child", "XmlValue"),
        CustomFunctionParameter("attributes", STRUCT_VALUE_TYPE_ID),
    )
}

private fun targetAttributesParameters(): List<CustomFunctionParameter> {
    return listOf(
        CustomFunctionParameter("target", "String"),
        CustomFunctionParameter("attributes", STRUCT_VALUE_TYPE_ID),
    )
}

private fun List<RuntimeValue>.uiReceiver(): KatariUiDocument {
    return KatariGeneratedBindingRuntime.asHost(this[0], "Ui", "receiver")
}

private fun List<RuntimeValue>.target(): String {
    return (this[1] as? StringValue)?.value ?: error("UI mutation target expects String")
}

private fun List<RuntimeValue>.child(): XmlValue {
    return this[2] as? XmlValue ?: error("UI mutation child expects XmlValue")
}

private fun List<RuntimeValue>.attributes(): Map<String, String> {
    val value = last() as? StructValue ?: error("UI mutation attributes expects StructValue")
    return value.toUiAttributes()
}

private fun StructValue.toUiAttributes(): Map<String, String> {
    return fields.mapValues { (_, value) ->
        when (value) {
            is StringValue -> value.value
            else -> value.convertToString()
        }
    }
}
