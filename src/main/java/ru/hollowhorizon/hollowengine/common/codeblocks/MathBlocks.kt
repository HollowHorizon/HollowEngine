package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor

enum class MathOp(val symbol: String) {
    ADD("+"), SUB("-"), MUL("*"), DIV("/");
}

enum class LogicOp(val symbol: String) {
    EQUALS("=="), GREATER(">"), LESS("<"), AND("&&"), OR("||");
}

class MathBlock(var op: MathOp = MathOp.ADD) : CodeBlock(MdColor.BLUE), ExpressionBlock {
    override val expressionType = ExpressionTypes.NUMBER

    // Dropdown state
    var expanded = mutableStateOf(false)

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
                }

            Text(op.symbol) { modifier.textColor(Color.WHITE).align(AlignmentX.Center) }
        }

        InputSlot("b", ExpressionTypes.NUMBER)
    }
}

class LogicBlock(var op: LogicOp = LogicOp.EQUALS) : CodeBlock(MdColor.INDIGO), ExpressionBlock {
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
                }
                .zLayer(300)
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.2f), sizes.smallGap))
        }
        InputSlot("b", ExpressionTypes.NUMBER)
    }
}

// Примитивы
class NumberBlock(var value: Double = 0.0) : CodeBlock(MdColor.AMBER), ExpressionBlock {
    override val expressionType = ExpressionTypes.NUMBER

    override suspend fun execute(context: BlockContext) = value
    override fun BlockEditor.InputSlotScope.composeContent() {
        TextField(value.toString()) {
            modifier.width(60.dp)
                .onChange { value = it.toDoubleOrNull() ?: 0.0 }
                .colors(lineColor = Color.WHITE.withAlpha(0f), textColor = Color.WHITE)
        }
    }
}

class BoolBlock(var value: Boolean = true) : CodeBlock(MdColor.AMBER), ExpressionBlock {
    override val expressionType = ExpressionTypes.BOOLEAN

    override suspend fun execute(context: BlockContext) = value
    override fun BlockEditor.InputSlotScope.composeContent() {
        Checkbox(value) {
            modifier.onToggle { this@BoolBlock.value = it }
        }
        Box {
            modifier
                .size(20.dp, 20.dp)
                .border(RectBorder(Color.WHITE, 2.dp))
                .background(if (value) RectBackground(Color.WHITE) else null)
                .onClick { value = !value }
        }
    }
}