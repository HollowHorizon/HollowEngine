package ru.hollowhorizon.hollowengine.common.scripting.compiler.story

import net.minecraft.nbt.EndTag
import net.minecraft.nbt.Tag
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.deserialize
import ru.hollowhorizon.hc.client.utils.nbt.deserializeNoInline
import ru.hollowhorizon.hc.client.utils.nbt.serializeNoInline
import ru.hollowhorizon.hollowengine.common.scripting.compiler.story.FunctionTransformer.ctx
import kotlin.collections.set
import kotlin.reflect.KProperty


class LocalPropertiesTransformer : IrElementTransformerVoid() {
    private val delegateType = ctx.referenceClass(
        ClassId(
            FqName("ru.hollowhorizon.hollowengine.common.scripting.compiler.story"), Name.identifier("PropertyDelegate")
        )
    )!!
    private val delegateConstructor = delegateType.constructors.first()
    val properties = hashMapOf<IrValueSymbol, IrValueDeclaration>()
    private val ignoredExpressions = hashSetOf<IrGetValue>()

    override fun visitVariable(declaration: IrVariable): IrStatement {
        if((declaration.type as? IrSimpleTypeImpl)?.classifier == delegateType) return super.visitVariable(declaration)
        val func = declaration.parent as? IrFunction ?: return super.visitVariable(declaration)
        val builder = ctx.irBuiltIns.createIrBuilder(func.symbol, func.startOffset, func.endOffset)

        val delegateInitializer = builder.irCall(delegateConstructor).apply {
            putClassTypeArgument(0, declaration.type)
            val initializer = declaration.initializer
            if (initializer != null) {
                putValueArgument(0, builder.irLambda(initializer.type) {
                    +irReturn(initializer)
                })
            }
        }
        val delegate = IrVariableImpl(
            builder.startOffset,
            builder.endOffset,
            IrDeclarationOrigin.DEFINED,
            IrVariableSymbolImpl(),
            Name.identifier(declaration.name.identifier),
            delegateType.typeWith(declaration.type),
            true,
            isConst = false,
            isLateinit = false
        ).apply {
            parent = builder.scope.getLocalDeclarationParent()
            this.initializer = delegateInitializer
        }
        properties[declaration.symbol] = delegate

        return super.visitVariable(delegate)
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        properties[expression.symbol]?.let { delegate ->
            val func = delegate.parent as? IrFunction ?: return super.visitSetValue(expression)

            val builder = ctx.irBuiltIns.createIrBuilder(func.symbol, func.startOffset, func.endOffset)

            expression.value.transform(this, null)

            return builder.irCall(delegateType.functionByName("set")).apply {
                dispatchReceiver = builder.irGet(delegate)
                putValueArgument(0, expression.value)
            }
        }

        return super.visitSetValue(expression)
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        properties[expression.symbol]?.let { delegate ->
            val func = delegate.parent as? IrFunction ?: return super.visitGetValue(expression)

            val builder = ctx.irBuiltIns.createIrBuilder(func.symbol, func.startOffset, func.endOffset)

            return builder.irCall(delegateType.functionByName("get"), expression.type).apply {
                dispatchReceiver = builder.irGet(delegate)
            }

        }

        return super.visitGetValue(expression)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        if (expression.symbol.owner.hasAnnotation(FqName("ru.hollowhorizon.hollowengine.common.scripting.story.StoryFunction"))) {
            ignoredExpressions.addAll(expression.valueArguments.filterIsInstance<IrGetValue>())
        }
        return super.visitCall(expression)
    }

    override fun visitBlock(expression: IrBlock): IrExpression {
        expression.transformChildren(this, null)
        return super.visitBlock(expression)
    }

    override fun visitWhen(expression: IrWhen): IrExpression {
        expression.transformChildren(this, null)
        return super.visitWhen(expression)
    }
}

val map = HashMap<KProperty<*>, Any?>()

// Используется для переменных в скриптах
open class PropertyDelegate<T>(val initializer: () -> T) {
    var value: T? = null

    open fun get(): T {
        if (value == null) value = initializer()
        return value ?: throw IllegalStateException("Value is null")
    }

    open fun set(value: T) {
        this.value = value
    }

    fun serialize(): Tag {
        val v = value ?: return EndTag.INSTANCE

        val type = v::class.java as Class<T & Any>

        return NBTFormat.serializeNoInline(v, type)
    }

    fun deserialize(tag: Tag) {
        if (tag is EndTag) return

        //TODO Пусть плагин для компилятора сам определяет тип переменной, генерики использовать запретим, ибо нафиг надо
        value = NBTFormat.deserializeNoInline(tag, value!!::class.java)
    }
}
