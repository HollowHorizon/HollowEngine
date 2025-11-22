package ru.hollowhorizon.hollowengine.common.compiler

@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
annotation class Import(vararg val files: String)