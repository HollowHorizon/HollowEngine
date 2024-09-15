package ru.hollowhorizon.hollowengine.compiler.identifiers

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val Suspendable = FqName("ru.hollowhorizon.hollowengine.scripting.Suspendable")
val DelegateProperty = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting"), Name.identifier("DelegateProperty"))

val SequenceNode = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting.nodes"), Name.identifier("SequenceNode"))
val LoopNode = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting.nodes"), Name.identifier("LoopNode"))
val WhenNode = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting.nodes"), Name.identifier("WhenNode"))
val BranchNode = ClassId(FqName("ru.hollowhorizon.hollowengine.scripting.nodes"), Name.identifier("BranchNode"))

val ArrayList = ClassId(FqName("java.util"), Name.identifier("ArrayList"))
val HashMap = ClassId(FqName("java.util"), Name.identifier("HashMap"))

val ToNode = CallableId(FqName("ru.hollowhorizon.hollowengine.scripting.nodes"), Name.identifier("toNode"))