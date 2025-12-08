package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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
    @Transient
    override val expressionType = typeOf<Number>()

    val a by input<Number>("a")
    val b by input<Number>("a")

    override suspend fun BlockContext.execute(): Any? {
        val a = a().toDouble()
        val b = b().toDouble()
        return when (op) {
            MathOp.ADD -> a + b
            MathOp.SUB -> a - b
            MathOp.MUL -> a * b
            MathOp.DIV -> a / b
        }
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        InputSlot(a)

        // Кликабельный текст для смены операции
        Box {
            modifier
                .margin(horizontal = 4.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.3f), sizes.largeGap))
                .onClick {
                    // Простая циклическая смена, можно сделать Popup меню
                    val values = MathOp.entries
                    op = values[(op.ordinal + 1) % values.size]
                    notifyChanged()
                }

            Text(op.symbol) { modifier.textColor(Color.WHITE).align(AlignmentX.Center).bold() }
        }

        InputSlot(b)
    }
}

@Serializable
@SerialName("hollowengine:math/logic")
class LogicBlock(var op: LogicOp = LogicOp.EQUALS) : CodeBlock(), ExpressionBlock {
    @Transient
    override val expressionType = typeOf<Boolean>()

    val a by input<Boolean>("a")
    val b by input<Boolean>("b")

    override suspend fun BlockContext.execute(): Any? {
        val resA = a()
        val resB = b()

        // Упрощенная логика сравнения
        return when (op) {
            LogicOp.EQUALS -> resA == resB
            LogicOp.AND -> resA && resB
            LogicOp.OR -> resA || resB
            LogicOp.GREATER -> (resA.toString().toDoubleOrNull() ?: 0.0) > (resB.toString().toDoubleOrNull() ?: 0.0)
            LogicOp.LESS -> (resA.toString().toDoubleOrNull() ?: 0.0) < (resB.toString().toDoubleOrNull() ?: 0.0)
        }
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        InputSlot(a)
        Box {
            modifier
                .margin(horizontal = 4.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.3f), sizes.largeGap))
                .onClick {
                    if (it.pointer.isLeftButtonClicked) {
                        val values = LogicOp.entries
                        op = values[(op.ordinal + 1) % values.size]
                    }
                    surface.triggerUpdate()
                    notifyChanged()
                }

            Text(op.symbol) {
                modifier.textColor(Color.WHITE).alignX(AlignmentX.Center).bold()
            }
        }

        InputSlot(b)
    }
}

@Serializable
@SerialName("hollowengine:math/test")
class TestBlock : CodeBlock(), ExpressionBlock {
    override val expressionType: ExpressionType
        get() {
            val parentType = parentBlock?.expressionTypeOrNull
            if (parentType != null && parentType != AnyType) return parentType

            val thenType = inputs["then"]?.expressionTypeOrNull
            val elseType = inputs["else"]?.expressionTypeOrNull

            return thenType ?: elseType ?: AnyType
        }

    val test by input<Boolean>("test")
    val thenBranch by input<Any>("then")
    val elseBranch by input<Any>("else")

    override suspend fun BlockContext.execute(): Any? {
        return if (test()) thenBranch()
        else elseBranch()
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Column(Grow.Std) {
            modifier.padding(horizontal = 10.dp, vertical = 6.dp).alignY(AlignmentY.Center)

            Row(Grow.Std) {
                Text("Выбрать по") { modifier.bold() }
                Box(Grow.Std) {}
                InputSlot(test)
            }
            Box { modifier.size(Grow.Std, sizes.gap) }
            Row(Grow.Std) {
                Text("Если истина") { modifier.bold() }
                Box(Grow.Std) {}
                InputSlot(thenBranch)
            }
            Box { modifier.size(Grow.Std, sizes.gap) }
            Row(Grow.Std) {
                Text("Если ложь") { modifier.bold() }
                Box(Grow.Std) {}
                InputSlot(elseBranch)
            }
        }
    }
}

@Serializable
@SerialName("hollowengine:number")
class NumberBlock(var value: Double = 0.0) : CodeBlock(), ExpressionBlock {
    @Transient
    override val expressionType = typeOf<Number>()

    override suspend fun BlockContext.execute() = value

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
    @Transient
    override val expressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute() = value

    override fun BlockEditor.InputSlotScope.composeContent() {
        Checkbox(value) {
            modifier.onToggle { this@BoolBlock.value = it; notifyChanged() }
        }
    }
}