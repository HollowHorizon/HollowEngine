package ru.hollowhorizon.hollowengine.common.geary.di

import kotlin.reflect.KClass

class ScopedDIContext(
    val simpleName: String,
    val byClass: KClass<*>? = null,
) : DIContext()