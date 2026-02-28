package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.walk

@Serializable
@SerialName("hollowengine:events/set_entity")
class SetEntityVarBlock(var varName: String = "var") : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val expressionType get() = (inputs["value"] as? ExpressionBlock)?.expressionType
    val entity by input<LivingEntity>("entity")
    val value by input<Any>("value")

    @OptIn(InternalSerializationApi::class)
    override suspend fun execute() {
        val value = value()

        TODO()
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.for_entity".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.variable_name".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }

        // Поле ввода имени переменной
        TextField(varName) {
            modifier.width(FitContent).margin(horizontal = 5.dp.scaled())
                .alignY(AlignmentY.Center)
                .onChange { varName = it }
                .hint("hollowengine.gui.codeblocks.label.variable_name".lang).font(font)
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(value)
    }
}

@Serializable
@SerialName("hollowengine:variables/get_entity")
class GetEntityVarBlock(var varName: String = "var") : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    override val expressionType: ExpressionType
        get() {
            val setVar = scope?.walk()?.filterIsInstance<SetVarBlock>()?.find { it.variableName == varName }
            return setVar?.expressionType ?: AnyType
        }

    val entity by input<LivingEntity>("entity")

    override suspend fun execute(): Any? {
        return TODO()
    }

    override fun InputSlotScope.composeContent() {
        TextField(varName) {
            modifier.width(FitContent).margin(start = 5.dp.scaled())
                .alignY(AlignmentY.Center)
                .onChange { varName = it }
                .hint("hollowengine.gui.codeblocks.label.variable_name".lang).font(font)
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
        Text("hollowengine.gui.codeblocks.label.for_entity".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:variables/get_inline_entity")
class GetEntityVarInlineBlock(val name: String) : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    override val expressionType: ExpressionType
        get() {
            val setVar = scope?.walk()?.filterIsInstance<SetVarBlock>()?.find { it.variableName == name }
            return setVar?.expressionType ?: AnyType
        }

    val entity by input<LivingEntity>("entity")

    override suspend fun execute(): Any? {
        return TODO()
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.variable_name".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        Text("\"$name\" " + "hollowengine.gui.codeblocks.label.for_entity".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center)
                .margin(start = 5.dp.scaled()).regular()
        }
        InputSlot(entity)
    }
}
