package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.text.UiMsdfFont

/**
 * The font layout tests measure against, pinned rather than inherited from the engine default.
 *
 * Two reasons. MonoCraft is monospaced, so a hand-computed expectation stays readable, the default
 * is Minecraft's own bitmap font, where every letter has its own width. And pinning means a change
 * of default cannot quietly rewrite what these tests assert: they are about wrapping, alignment and
 * caret mapping, not about which face happens to ship as the default.
 */
const val TestFontFamily = UiMsdfFont.MonocraftFontFamily

const val TestFontSize = 10f

val TestFontStyle: Modifier get() = Modifier.fontFamily(TestFontFamily).fontSize(TestFontSize)
