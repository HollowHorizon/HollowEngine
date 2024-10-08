package ru.hollowhorizon.hollowengine.compiler.identifiers

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendLauncher

val Suspendable = FqName("ru.hollowhorizon.hollowengine.scripting.Suspendable")
val United = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting"), Name.identifier("United"))
val DelegateProperty = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting"), Name.identifier("DelegateProperty"))

val IntIterator = ClassId(FqName("kotlin.collections"), Name.identifier("IntIterator"))

val SequenceNode = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting.nodes"), Name.identifier("SequenceNode"))

val SuspendContext = ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("SuspendContext"))
val SuspendLauncher = ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("SuspendLauncher"))

val SuspendState = ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("SuspendState"))
val ResumeState = ClassId(FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"), Name.identifier("ResumeState"))

val LoopNode = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting.nodes"), Name.identifier("LoopNode"))
val WhenNode = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting.nodes"), Name.identifier("WhenNode"))
val BranchNode = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting.nodes"), Name.identifier("BranchNode"))

val ArrayList = ClassId(FqName("java.util"), Name.identifier("ArrayList"))
val HashMap = ClassId(FqName("java.util"), Name.identifier("HashMap"))

val ToNode = CallableId(FqName("ru.hollowhorizon.hollowengine.scripting.nodes"), Name.identifier("toNode"))