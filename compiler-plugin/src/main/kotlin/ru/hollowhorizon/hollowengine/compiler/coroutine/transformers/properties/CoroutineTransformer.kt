package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.properties

import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.CoroutineGenerator

abstract class CoroutineTransformer: IrElementTransformerVoid() {
    lateinit var coroutine: CoroutineGenerator
}