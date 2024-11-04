package ru.hollowhorizon.hollowengine.compiler.identifiers

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val Suspendable = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting"), Name.identifier("Suspendable"))
val United = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting"), Name.identifier("United"))
val Ignore = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting"), Name.identifier("Ignore"))
val Metadata = ClassId(FqName("kotlin"), Name.identifier("Metadata"))
val SuspendContext =
    ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("SuspendContext"))
val AsyncContext =
    ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("AsyncContext"))
val AsyncController =
    ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("AsyncController"))
val SuspendLauncher =
    ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("SuspendLauncher"))
val SuspendState =
    ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("SuspendState"))
val ResumeState = ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("ResumeState"))
val ArrayList = ClassId(FqName("java.util"), Name.identifier("ArrayList"))