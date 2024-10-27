package ru.hollowhorizon.hollowengine.compiler.identifiers

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val Suspendable = FqName("ru.hollowhorizon.hollowengine.scripting.Suspendable")
val United = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting"), Name.identifier("United"))
val Metadata = ClassId(FqName("kotlin"), Name.identifier("Metadata"))
val SuspendContext =
    ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("SuspendContext"))
val SuspendLauncher =
    ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("SuspendLauncher"))
val SuspendState =
    ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("SuspendState"))
val ResumeState = ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("ResumeState"))
val ArrayList = ClassId(FqName("java.util"), Name.identifier("ArrayList"))