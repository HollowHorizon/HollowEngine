package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.text.*

/** Named inline text effects, in the order the completion popup offers them. */
internal val UiTextEffectNames = listOf(
    "bold",
    "italic",
    "underline",
    "strikethrough",
    "code",
    "link",
    "color",
    "font-size",
    "font-family",
    "shadow",
    "outline",
    "glow",
    "gradient",
    "typewriter",
    "rainbow",
    "pulse",
    "wave",
    "shake",
    "wiggle",
    "swing",
    "scroll",
    "glitch",
)

internal fun parseTextEffects(value: String): List<UiTextEffect> {
    if (value.equals("none", ignoreCase = true)) return emptyList()
    return splitTopLevel(value, ',').map { parseTextEffect(it.trim()) }
}

internal fun parseTextEffect(entry: String): UiTextEffect {
    val nameEnd = entry.indexOf('(')
    val name = if (nameEnd < 0) entry.trim().lowercase() else entry.substring(0, nameEnd).trim().lowercase()
    val args = if (nameEnd < 0) emptyList() else functionArgs(entry, name)

    return when (name) {
        "bold" -> Bold(args.getOrNull(0)?.let(::parseScalar) ?: DefaultBoldWeight)
        "italic" -> Italic(args.getOrNull(0)?.let(::parseScalar) ?: DefaultItalicSkew)
        "underline" -> parseHssRule(args, ::Underline)
        "strikethrough" -> parseHssRule(args, ::Strikethrough)
        "code" -> Code
        "link" -> Link(args.firstOrNull() ?: "")
        "color" -> TextColor(if (args.isNotEmpty()) parseColor(args.first()) else UiColor.White)
        "font-size", "size" -> TextSize(args.firstOrNull()?.let(::parseScalar) ?: DefaultUiFontSize)
        "font-family", "font" -> TextFont(args.firstOrNull() ?: "")
        "shadow" -> parseHssShadow(args)
        "outline" -> parseHssOutline(args)
        "glow" -> parseHssGlow(args)
        "gradient" -> parseHssGradient(args)
        "typewriter" -> Typewriter
        "rainbow" -> Rainbow()
        "pulse" -> Pulse()
        "wave" -> parseHssWave(args)
        "shake" -> parseHssShake(args)
        "wiggle" -> parseHssWiggle(args)
        "swing" -> Swing()
        "scroll" -> Scroll()
        "glitch" -> Glitch()
        else -> throw IllegalArgumentException("Unknown text effect '$name'")
    }
}

/** `underline(thickness, offset, color)` / `strikethrough(...)`; every argument is optional. */
private fun <T : UiTextEffect> parseHssRule(
    args: List<String>,
    create: (thickness: Float, offset: Float, color: UiColor?) -> T,
): T = create(
    args.getOrNull(0)?.let(::parseScalar) ?: 0f,
    args.getOrNull(1)?.let(::parseScalar) ?: 0f,
    args.getOrNull(2)?.let(::parseColor),
)

private fun parseHssShadow(args: List<String>): Shadow {
    return Shadow(
        offsetX = args.getOrNull(0)?.let(::parseScalar) ?: 1.5f,
        offsetY = args.getOrNull(1)?.let(::parseScalar) ?: 1.5f,
        blur = args.getOrNull(2)?.let(::parseScalar) ?: 0f,
        color = args.getOrNull(3)?.let(::parseColor) ?: UiColor(0f, 0f, 0f, 0.7f),
    )
}

private fun parseHssOutline(args: List<String>): Outline {
    return Outline(
        width = args.getOrNull(0)?.let(::parseScalar) ?: 1.5f,
        color = args.getOrNull(1)?.let(::parseColor) ?: UiColor(0f, 0f, 0f, 1f),
    )
}

private fun parseHssGlow(args: List<String>): Glow {
    return Glow(
        radius = args.getOrNull(0)?.let(::parseScalar) ?: 3f,
        color = args.getOrNull(1)?.let(::parseColor) ?: UiColor(1f, 1f, 1f, 0.8f),
        quality = args.getOrNull(2)?.toIntOrNull() ?: 4,
    )
}

private fun parseHssGradient(args: List<String>): Gradient {
    val colorArgs = if (args.size >= 2) args.drop(1) else args
    val colors = colorArgs.map { parseColor(it) }
    return Gradient(
        colors = if (colors.size >= 2) colors else Gradient().colors,
        mode = when (args.firstOrNull()?.lowercase()) {
            "vertical", "v" -> GradientMode.VERTICAL
            else -> GradientMode.HORIZONTAL
        },
    )
}

private fun parseHssWave(args: List<String>): Wave {
    return Wave(
        amplitude = args.getOrNull(0)?.let(::parseScalar) ?: 3f,
        frequency = args.getOrNull(1)?.let(::parseScalar) ?: 1.5f,
        speed = args.getOrNull(2)?.let(::parseScalar) ?: 2f,
    )
}

private fun parseHssShake(args: List<String>): Shake {
    return Shake(
        amplitude = args.getOrNull(0)?.let(::parseScalar) ?: 2f,
        frequency = args.getOrNull(1)?.let(::parseScalar) ?: 10f,
    )
}

private fun parseHssWiggle(args: List<String>): Wiggle {
    return Wiggle(
        amplitude = args.getOrNull(0)?.let(::parseScalar) ?: 2f,
        frequency = args.getOrNull(1)?.let(::parseScalar) ?: 2f,
        speed = args.getOrNull(2)?.let(::parseScalar) ?: 1.5f,
        angleDegrees = args.getOrNull(3)?.let(::parseScalar) ?: 0f,
    )
}
