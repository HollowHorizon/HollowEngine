@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler.identifiers

import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.coroutine.AsyncController
import ru.hollowhorizon.hollowengine.compiler.pluginContext
val SCRIPTING_PACKAGE = FqName("ru.hollowhorizon.hollowengine.scripting")

val AsyncController = ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.coroutine"), Name.identifier("AsyncController"))
val Suspendable = ClassId(SCRIPTING_PACKAGE, Name.identifier("Suspendable"))
val Ignore = ClassId(SCRIPTING_PACKAGE, Name.identifier("Ignore"))
val Restorable = ClassId(SCRIPTING_PACKAGE, Name.identifier("Restorable"))
val SuspendState = ClassId(SCRIPTING_PACKAGE, Name.identifier("SuspendState"))
val ResumeState = ClassId(SCRIPTING_PACKAGE, Name.identifier("ResumeState"))
val LambdaParameter = ClassId(SCRIPTING_PACKAGE, Name.identifier("LambdaParameter"))

val Serializable = ClassId(FqName("kotlinx.serialization"), Name.identifier("Serializable"))

fun ClassId.constructor() = pluginContext.referenceClass(this)!!.constructors.first()