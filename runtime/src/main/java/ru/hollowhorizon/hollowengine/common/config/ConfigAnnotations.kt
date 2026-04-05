package ru.hollowhorizon.hollowengine.common.config

annotation class ConfigName(val name: String)

annotation class PropertyName(val name: String)
annotation class PropertyComment(val value: String)
annotation class PropertyRange(val min: Float, val max: Float)
annotation class PropertyValidValues(vararg val values: String)