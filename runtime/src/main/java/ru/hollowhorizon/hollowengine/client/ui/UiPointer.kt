package ru.hollowhorizon.hollowengine.client.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Live pointer position provided by [HollowUiSurface]; read it to place cursor-following UI. */
data class UiPointer(val x: Float, val y: Float) {
    val isKnown: Boolean get() = x.isFinite() && y.isFinite()

    companion object {
        val Unknown = UiPointer(Float.NaN, Float.NaN)
    }
}

val LocalPointer = staticCompositionLocalOf { UiPointer.Unknown }
