package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.*

enum class MathOp(val symbol: String) {
    ADD("+"), SUB("-"), MUL("*"), DIV("/");
}

enum class LogicOp(val symbol: String) {
    EQUALS("=="), GREATER(">"), LESS("<"), AND("&&"), OR("||");
}

@Serializable
@SerialName("hollowengine:math/operation")
class MathBlock(var op: MathOp = MathOp.ADD) : CodeBlock(), ExpressionBlock {
    override val expressionType = ExpressionTypes.NUMBER

    override suspend fun execute(context: BlockContext): Any? {
        val a = inputs["a"]?.execute(context).toString().toDoubleOrNull() ?: 0.0
        val b = inputs["b"]?.execute(context).toString().toDoubleOrNull() ?: 0.0
        return when (op) {
            MathOp.ADD -> a + b
            MathOp.SUB -> a - b
            MathOp.MUL -> a * b
            MathOp.DIV -> a / b
        }
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        InputSlot("a", ExpressionTypes.NUMBER)

        // Кликабельный текст для смены операции
        Box {
            modifier
                .margin(horizontal = 4.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.3f), 4.dp))
                .onClick {
                    // Простая циклическая смена, можно сделать Popup меню
                    val values = MathOp.values()
                    op = values[(op.ordinal + 1) % values.size]
                    notifyChanged()
                }

            Text(op.symbol) { modifier.textColor(Color.WHITE).align(AlignmentX.Center) }
        }

        InputSlot("b", ExpressionTypes.NUMBER)
    }
}

@Serializable
@SerialName("hollowengine:math/logic")
class LogicBlock(var op: LogicOp = LogicOp.EQUALS) : CodeBlock(), ExpressionBlock {
    override val expressionType = ExpressionTypes.BOOLEAN

    override suspend fun execute(context: BlockContext): Any? {
        val resA = inputs["a"]?.execute(context)
        val resB = inputs["b"]?.execute(context)

        // Упрощенная логика сравнения
        return when (op) {
            LogicOp.EQUALS -> resA == resB
            LogicOp.AND -> (resA as? Boolean == true) && (resB as? Boolean == true)
            LogicOp.OR -> (resA as? Boolean == true) || (resB as? Boolean == true)
            LogicOp.GREATER -> (resA.toString().toDoubleOrNull() ?: 0.0) > (resB.toString().toDoubleOrNull() ?: 0.0)
            LogicOp.LESS -> (resA.toString().toDoubleOrNull() ?: 0.0) < (resB.toString().toDoubleOrNull() ?: 0.0)
        }
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        InputSlot("a", ExpressionTypes.NUMBER)
        Text(op.symbol) {
            modifier.margin(horizontal = sizes.smallGap)
                .padding(horizontal = sizes.smallGap)
                .textColor(Color.WHITE)
                .alignY(AlignmentY.Center)
                .onClick {
                    if (it.pointer.isLeftButtonClicked) {
                        val values = LogicOp.values()
                        op = values[(op.ordinal + 1) % values.size]
                    }
                    surface.triggerUpdate()
                    notifyChanged()
                }
                .zLayer(300)
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.2f), sizes.smallGap))
        }
        InputSlot("b", ExpressionTypes.NUMBER)
    }
}

@Serializable
@SerialName("hollowengine:math/test")
class TestBlock : CodeBlock(), ExpressionBlock {
    override val expressionType: ExpressionType
        get() = parentBlock?.expressionTypeOrNull
            ?: inputs["then"]?.expressionTypeOrNull
            ?: inputs["else"]?.expressionTypeOrNull
            ?: AnyType

    override suspend fun execute(context: BlockContext): Any? {
        return if (inputs["test"]?.execute(context) == true) inputs["then"]?.execute(context)
        else inputs["else"]?.execute(context)
    }

    context(editor: BlockEditor)
    override fun UiScope.composeHeaderLayout(
        isHovered: Boolean,
        isGhost: Boolean,
        blockHeaderModifier: UiModifier.() -> Unit,
    ) {
        Column(Grow.Std) {
            modifier.apply(blockHeaderModifier)
            modifier.padding(horizontal = 10.dp, vertical = 6.dp).alignY(AlignmentY.Center)

            editor.InputSlotScope(this, this@TestBlock, isHovered, isGhost).composeContent()
        }
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Row(Grow.Std) {
            Text("Выбрать по") {  }
            Box(Grow.Std) {}
            InputSlot("test", ExpressionTypes.BOOLEAN)
        }
        Box { modifier.size(Grow.Std, sizes.gap) }
        Row(Grow.Std) {
            Text("Если истина") {  }
            Box(Grow.Std) {}
            InputSlot("then", parentBlock.expressionTypeOrNull ?: inputs["else"]?.expressionTypeOrNull ?: AnyType)
        }
        Box { modifier.size(Grow.Std, sizes.gap) }
        Row(Grow.Std) {
            Text("Если ложь") {  }
            Box(Grow.Std) {}
            InputSlot("else", parentBlock.expressionTypeOrNull ?: inputs["then"]?.expressionTypeOrNull ?: AnyType)
        }
    }
}

// Примитивы
@Serializable
@SerialName("hollowengine:number")
class NumberBlock(var value: Double = 0.0) : CodeBlock(), ExpressionBlock {
    override val expressionType = ExpressionTypes.NUMBER

    override suspend fun execute(context: BlockContext) = value
    override fun BlockEditor.InputSlotScope.composeContent() {
        TextField(value.toString()) {
            modifier.width(60.dp)
                .onChange { value = it.toDoubleOrNull() ?: 0.0; notifyChanged() }
                .colors(lineColor = Color.WHITE.withAlpha(0f), textColor = Color.WHITE)
        }
    }
}

@Serializable
@SerialName("hollowengine:boolen")
class BoolBlock(var value: Boolean = true) : CodeBlock(), ExpressionBlock {
    override val expressionType = ExpressionTypes.BOOLEAN

    override suspend fun execute(context: BlockContext) = value
    override fun BlockEditor.InputSlotScope.composeContent() {
        Checkbox(value) {
            modifier.onToggle { this@BoolBlock.value = it; notifyChanged() }
        }
    }
}