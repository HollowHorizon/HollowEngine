package ru.hollowhorizon.hollowengine.common.scripting

@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
annotation class Import(val file: String)