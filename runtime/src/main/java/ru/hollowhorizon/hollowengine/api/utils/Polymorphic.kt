package ru.hollowhorizon.hollowengine.api.utils

import kotlin.reflect.KClass

/**
 * Required for child serialization
 *
 * You can read more [here](https://0mods.team/docs/hollowcore/serialization/#serialization-with-inheritance)
 */
annotation class Polymorphic(val baseClass: KClass<*>)