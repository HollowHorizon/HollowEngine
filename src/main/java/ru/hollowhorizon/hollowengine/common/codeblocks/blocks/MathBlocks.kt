package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.scripting.EditorTheme
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.expressionTypeOrNull
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import kotlin.random.Random

enum class MathOp(val symbol: String) {
    ADD("+"), SUB("-"), MUL("*"), DIV("/");
}

enum class LogicOp(val symbol: String) {
    AND("&&"), OR("||");
}

enum class CompareOp(val symbol: String) {
    EQUALS("=="), NOT_EQUALS("!="), GREATER(">"), LESS("<"), GREATER_EQUALS(">="), LESS_EQUALS("<=");
}

@Serializable
@SerialName("hollowengine:math/operation")
class MathBlock(var op: MathOp = MathOp.ADD) : ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<Number>()

    val a by input<Number>("a")
    val b by input<Number>("b")

    override suspend fun execute(): Any? {
        val a = a().toDouble()
        val b = b().toDouble()
        return when (op) {
            MathOp.ADD -> a + b
            MathOp.SUB -> a - b
            MathOp.MUL -> a * b
            MathOp.DIV -> a / b
        }
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(a)

        // Кликабельный текст для смены операции
        Box {
            modifier
                .size(sizes.largeGap, sizes.largeGap)
                .alignY(AlignmentY.Center)
                .margin(horizontal = 4.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.3f), sizes.largeGap))
                .onClick {
                    if (it.pointer.isLeftButtonClicked) {
                        val values = MathOp.entries
                        op = values[(op.ordinal + 1) % values.size]
                    }
                    surface.triggerUpdate()
                    notifyChanged()
                }
                .zLayer(modifier.zLayer + 10)

            Text(op.symbol) { modifier.textColor(Color.WHITE).align(AlignmentX.Center).alignY(AlignmentY.Center).bold() }
        }

        InputSlot(b)
    }
}

@Serializable
@SerialName("hollowengine:math/random")
class RandomNumberBlock : ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<Number>()

    val min by input<Number>("min")
    val max by input<Number>("max")

    override suspend fun execute(): Any? {
        val minVal = min().toDouble()
        val maxVal = max().toDouble()
        return Random.nextDouble(minVal, maxVal)
    }

    override fun InputSlotScope.composeContent() {
        Row(Grow.Std) {
            Text("Случайное от") {
                modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
            }
            InputSlot(min)
            Text("до") {
                modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
            }
            InputSlot(max)
        }
    }
}

@Serializable
@SerialName("hollowengine:math/compare")
class CompareBlock(var op: CompareOp = CompareOp.EQUALS) : ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<Boolean>()

    val a by input<Number>("a")
    val b by input<Number>("b")

    override suspend fun execute(): Any? {
        val resA = a().toDouble()
        val resB = b().toDouble()

        return when (op) {
            CompareOp.EQUALS -> resA == resB
            CompareOp.NOT_EQUALS -> resA != resB
            CompareOp.GREATER -> resA > resB
            CompareOp.LESS -> resA < resB
            CompareOp.GREATER_EQUALS -> resA >= resB
            CompareOp.LESS_EQUALS -> resA <= resB
        }
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(a)

        // Кликабельный текст для смены операции
        Box {
            modifier
                .size(sizes.largeGap, sizes.largeGap).alignY(AlignmentY.Center)
                .margin(horizontal = 4.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.3f), sizes.largeGap))
                .onClick {
                    if (it.pointer.isLeftButtonClicked) {
                        val values = CompareOp.entries
                        op = values[(op.ordinal + 1) % values.size]
                    }
                    surface.triggerUpdate()
                    notifyChanged()
                }
                .alignY(AlignmentY.Center)
                .zLayer(modifier.zLayer + 10)

            Text(op.symbol) { modifier.textColor(Color.WHITE).align(AlignmentX.Center).alignY(AlignmentY.Center).bold() }
        }

        InputSlot(b)
    }
}

@Serializable
@SerialName("hollowengine:math/logic")
class LogicBlock(var op: LogicOp = LogicOp.AND) : ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<Boolean>()

    val a by input<Boolean>("a")
    val b by input<Boolean>("b")

    override suspend fun execute(): Any? {
        val resA = a()
        val resB = b()

        return when (op) {
            LogicOp.AND -> resA && resB
            LogicOp.OR -> resA || resB
        }
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(a)
        Box {
            modifier
                .size(FitContent, sizes.largeGap)
                .alignY(AlignmentY.Center)
                .padding(horizontal = sizes.smallGap)
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.3f), sizes.largeGap))
                .onClick {
                    if (it.pointer.isLeftButtonClicked) {
                        val values = LogicOp.entries
                        op = values[(op.ordinal + 1) % values.size]
                    }
                    surface.triggerUpdate()
                    notifyChanged()
                }
                .zLayer(modifier.zLayer + 10)

            Text(op.symbol) {
                modifier.textColor(Color.WHITE).alignX(AlignmentX.Center).alignY(AlignmentY.Center).bold()
            }
        }

        InputSlot(b)
    }
}

@Serializable
@SerialName("hollowengine:math/not")
class NotBlock : ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<Boolean>()

    val value by input<Boolean>("value")

    override suspend fun execute(): Any? {
        return !(value())
    }

    override fun InputSlotScope.composeContent() {
        Text("Не") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(value)
    }
}

@Serializable
@SerialName("hollowengine:math/test")
class TestBlock : ExpressionBlock() {
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

    override suspend fun execute(): Any? {
        return if (test()) thenBranch()
        else elseBranch()
    }

    override fun InputSlotScope.composeContent() {
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
class NumberBlock(var value: Double = 0.0) : ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<Number>()

    override suspend fun execute() = value

    override fun InputSlotScope.composeContent() {
        TextField(value.toString()) {
            modifier
                .onChange { value = it.toDoubleOrNull() ?: 0.0; notifyChanged() }
                .colors(
                    lineColor = Color.WHITE,
                    textColor = Color.WHITE,
                    selectionColor = EditorTheme.selection,
                    cursorColor = EditorTheme.caret
                )
        }
    }
}

@Serializable
@SerialName("hollowengine:boolen")
class BoolBlock(var value: Boolean = true) : ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<Boolean>()

    override suspend fun execute() = value

    override fun InputSlotScope.composeContent() {
        Checkbox(value) {
            modifier.onToggle { this@BoolBlock.value = it; surface.triggerUpdate(); notifyChanged() }
        }
    }
}