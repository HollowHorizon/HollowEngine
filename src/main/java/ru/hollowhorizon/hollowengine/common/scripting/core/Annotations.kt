package ru.hollowhorizon.hollowengine.common.scripting.core

@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
annotation class Import(vararg val files: String)