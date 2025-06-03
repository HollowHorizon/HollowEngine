package ru.hollowhorizon.hollowengine.ksp

@Target(AnnotationTarget.FILE)
annotation class DocPage(
    val location: String,
    val title: String,
    val description: String = "",
)
