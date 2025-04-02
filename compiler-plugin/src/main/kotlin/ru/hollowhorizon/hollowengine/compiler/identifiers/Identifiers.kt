@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler.identifiers

import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.pluginContext

val Serializable = ClassId(FqName("kotlinx.serialization"), Name.identifier("Serializable"))
val Suspendable = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting"), Name.identifier("Suspendable"))
val Ignore = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting"), Name.identifier("Ignore"))
val Restorable = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting"), Name.identifier("Restorable"))
val SuspendState = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting"), Name.identifier("SuspendState"))
val ResumeState = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting"), Name.identifier("ResumeState"))


fun ClassId.constructor() = pluginContext.referenceClass(this)!!.constructors.first()