package ru.hollowhorizon.hollowengine.common.scripting

@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
annotation class Import(vararg val files: String)