package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.UiStyleProperty

/**
 * Transitions and keyframe animations.
 *
 * Both are lists, and both are named in the plural: a declaration that accepts several
 * comma-separated entries says so in its name, while the singular CSS spellings stay
 * available as aliases.
 */
internal fun motionHssProperties(): List<HssProperty> = hssProperties {
    property(
        "transitions", "transition",
        summary = "Properties that animate towards their new value, with duration and easing.",
        syntax = listSyntax(
            HssSlot("property", HssValueKind.STYLE_PROPERTY),
            slot("duration", HssValueKind.DURATION),
            slot("easing", HssValueKind.EASING, optional = true),
        ),
        examples = listOf("background 100ms ease-out", "all 200ms ease-in-out", "none"),
    ) {
        style {
            val parsed = parseTransitions(value)
            it.transitions = if (UiStyleProperty.TRANSITIONS in it.explicitProperties.orEmpty()) {
                it.transitions.mergeUiTransitions(parsed)
            } else {
                parsed
            }
            it.explicitProperties = it.explicitProperties.orEmpty() + UiStyleProperty.TRANSITIONS
        }
    }

    property(
        "animations", "animation",
        summary = "Keyframe animations played on the node; tokens may come in any order.",
        syntax = listSyntax(*AnimationSlots.toTypedArray(), classifier = ::animationSlotAt),
        examples = listOf("fade 200ms ease-out forwards", "pulse 1s linear infinite", "none"),
    ) { style { it.animations = parseAnimations(value) } }

    property(
        "animation-name",
        summary = "Names of the `@keyframes` blocks to play.",
        syntax = listSyntax(slot("name", HssValueKind.KEYFRAMES)),
    ) { style { patch -> patch.animations = splitTopLevel(value, ',').map { UiAnimation(unquote(it)) } } }

    animationPart(
        "animation-duration",
        "How long one iteration of each animation lasts.",
        slot("duration", HssValueKind.DURATION),
        ::parseDuration,
    ) { animation, duration -> animation.copy(durationMillis = duration) }

    animationPart(
        "animation-timing-function",
        "Easing of each animation.",
        slot("easing", HssValueKind.EASING),
        ::parseEasing,
    ) { animation, easing -> animation.copy(easing = easing) }

    animationPart(
        "animation-delay",
        "Delay before each animation starts.",
        slot("delay", HssValueKind.DURATION),
        ::parseDuration,
    ) { animation, delay -> animation.copy(delayMillis = delay) }

    animationPart(
        "animation-iteration-count",
        "How many times each animation repeats.",
        AnimationIterationSlot,
        ::parseIterationCount,
    ) { animation, count -> animation.copy(iterationCount = count) }

    animationPart(
        "animation-direction",
        "Direction each iteration plays in.",
        HssSlot("direction", HssValueKind.KEYWORD, keywords = enumKeywords<UiAnimationDirection>()),
        ::parseAnimationDirection,
    ) { animation, direction -> animation.copy(direction = direction) }

    animationPart(
        "animation-fill-mode",
        "Which keyframe values stick before and after the animation runs.",
        HssSlot("fill-mode", HssValueKind.KEYWORD, keywords = enumKeywords<UiAnimationFillMode>()),
        ::parseAnimationFillMode,
    ) { animation, fillMode -> animation.copy(fillMode = fillMode) }

    animationPart(
        "animation-play-state",
        "Whether the animations run or hold their current frame.",
        HssSlot("play-state", HssValueKind.KEYWORD, keywords = enumKeywords<UiAnimationPlayState>()),
        ::parseAnimationPlayState,
    ) { animation, playState -> animation.copy(playState = playState) }
}

/**
 * Declares one `animation-*` long-hand: a comma-separated list whose values are spread
 * over the animations declared by `animations`, cycling when there are fewer values.
 */
private fun <T> HssPropertyBuilder.animationPart(
    name: String,
    summary: String,
    valueSlot: HssSlot,
    parse: (String) -> T,
    patch: (UiAnimation, T) -> UiAnimation,
) {
    property(
        name,
        summary = summary,
        syntax = listSyntax(valueSlot),
        examples = valueSlot.candidates(),
    ) {
        style { style ->
            style.animations = style.animations.patchAnimationValues(splitTopLevel(value, ',').map(parse), patch)
        }
    }
}
