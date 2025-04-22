package ru.hollowhorizon.hollowengine.compiler.coroutine.generators

import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder
import ru.hollowhorizon.hollowengine.compiler.identifiers.AsyncController
import ru.hollowhorizon.hollowengine.compiler.pluginContext

internal val asyncController = pluginContext.referenceClass(AsyncController)!!

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun CoroutineGenerator.createAsyncs() {
    asyncs.forEach { field ->
        (updateAsyncsFunction.body as IrBlockBody).statements.add(0, field.builder().run {
            irIfThen(irEquals(
                irCall(asyncController.getPropertyGetter("isActive")!!).apply {
                    dispatchReceiver = irGetField(irGet(receiver), field)
                },
                irTrue()
            ),
            irCall(asyncController.functionByName("update")).apply {
                dispatchReceiver = irGetField(irGet(receiver), field)
            })
        })
    }
}