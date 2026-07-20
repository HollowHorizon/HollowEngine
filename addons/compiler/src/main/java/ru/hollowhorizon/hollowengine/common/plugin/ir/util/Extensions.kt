package ru.hollowhorizon.hollowengine.common.plugin.ir.util

import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import ru.hollowhorizon.hollowengine.common.plugin.ir.StateIrGenerationExtension
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <T : IrDeclaration> T.builder(body: DeclarationIrBuilder.(T) -> Unit = {}): DeclarationIrBuilder {
    contract {
        callsInPlace(body, InvocationKind.EXACTLY_ONCE)
    }

    return StateIrGenerationExtension.pluginContext.irBuiltIns.createIrBuilder(symbol, startOffset, endOffset)
        .apply { body(this@builder) }
}