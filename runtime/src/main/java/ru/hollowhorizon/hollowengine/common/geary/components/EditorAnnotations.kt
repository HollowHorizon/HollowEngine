@file:OptIn(ExperimentalSerializationApi::class)

package ru.hollowhorizon.hollowengine.common.geary.components

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class EditorName(val name: String)

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class EditorRange(val min: Float, val max: Float)

@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class EditorIcon(val icon: String)

@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class EditorHidden
