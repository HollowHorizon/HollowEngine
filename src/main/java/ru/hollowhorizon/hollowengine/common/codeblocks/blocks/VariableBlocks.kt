package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.*
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.blockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.LivingEntityContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.SerializableVariableContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.isSerializable

@Serializable
@SerialName("hollowengine:events/set")
class SetVarBlock(var varName: String = "var") : StatementBlock() {
    val value by input<Any>("value")

    @OptIn(InternalSerializationApi::class)
    override suspend fun execute() {
        val value = value()

        blockContext().variables[varName] = when(value) {
            isSerializable() -> SerializableVariableContainer(value::class.serializer())
            is LivingEntity -> LivingEntityContainer()
            else -> throw IllegalArgumentException("Variable '$varName' cannot be serialized!")
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("Присвоить:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        // Поле ввода имени переменной
        TextField(varName) {
            modifier.width(FitContent).margin(horizontal = 5.dp)
                .alignY(AlignmentY.Center)
                .onChange { varName = it }
                .hint("Имя переменной").font(font)
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(value)
    }
}

@Serializable
@SerialName("hollowengine:variables/get")
class GetVarBlock(var varName: String = "var") : ExpressionBlock() {
    @Transient
    override val expressionType = AnyType

    override suspend fun execute(): Any? {
        return blockContext().variables[varName]
    }

    override fun InputSlotScope.composeContent() {
        TextField(varName) {
            modifier.width(FitContent).margin(start = 5.dp)
                .alignY(AlignmentY.Center)
                .onChange { varName = it }
                .hint("Имя переменной").font(font)
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
    }
}

@Serializable
@SerialName("hollowengine:variables/get_inline")
class GetVarInlineBlock(val name: String) : ExpressionBlock() {
    @Transient
    override val expressionType = AnyType

    override suspend fun execute(): Any? {
        return blockContext().variables[name]
    }

    override fun InputSlotScope.composeContent() {
        Text("Значение переменной") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        Text("\"$name\"") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center)
                .margin(start = 5.dp)
        }
    }
}