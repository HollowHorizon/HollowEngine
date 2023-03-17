package ru.hollowhorizon.hollowstory.story

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class StartAfter(val id: String)