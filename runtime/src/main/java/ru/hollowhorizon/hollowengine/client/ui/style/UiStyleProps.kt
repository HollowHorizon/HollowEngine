package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextEffect
import ru.hollowhorizon.hollowengine.client.ui.shape.Shape
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiCheckboxStyle
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiSliderStyle
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextFieldStyle
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTyping

const val DefaultUiFontSize = 10f

/**
 * Declarations of every style property. A property is fully described by its single
 * declaration here — defaults/inheritance, merge, transition interpolation, invalidation
 * phase and layout-fingerprint participation. The typed accessors used across the
 * engine are declared right below, next to their property.
 */
object UiProps {
    private val LayoutPhase = setOf(UiInvalidationPhase.Layout)
    private val DrawPhase = setOf(UiInvalidationPhase.Draw)
    private val InputPhase = setOf(UiInvalidationPhase.Input)

    private fun <T> prop(
        name: String,
        default: T,
        aliases: Set<String> = emptySet(),
        phases: Set<UiInvalidationPhase> = LayoutPhase,
        fingerprint: Boolean = false,
        group: String? = null,
        merge: ((current: T, next: T) -> T)? = null,
        combine: ((current: T, next: T) -> T)? = null,
        interpolate: ((from: T, to: T, progress: Float) -> T)? = null,
        sanitize: ((T) -> T)? = null,
    ) = UiStyleProp(
        name = name,
        aliases = aliases,
        phases = phases,
        layoutFingerprint = fingerprint,
        transitionGroup = group,
        defaultValue = { default },
        mergeValues = merge,
        combineValues = combine,
        interpolation = interpolate,
        sanitize = sanitize,
    )

    private fun <T> inheritedProp(
        name: String,
        fallback: T,
        aliases: Set<String> = emptySet(),
        phases: Set<UiInvalidationPhase> = LayoutPhase,
        fingerprint: Boolean = false,
        interpolate: ((from: T, to: T, progress: Float) -> T)? = null,
    ) = UiStyleProp(
        name = name,
        aliases = aliases,
        phases = phases,
        layoutFingerprint = fingerprint,
        transitionGroup = null,
        defaultValue = { parent -> if (parent != null) parent[this] else fallback },
        mergeValues = null,
        interpolation = interpolate,
        sanitize = null,
    )

    // Sizing / layout
    val Width = prop<UiLength>("width", UiLength.Auto, fingerprint = true)
    val Height = prop<UiLength>("height", UiLength.Auto, fingerprint = true)
    val MinWidth = prop<UiLength>("min-width", UiLength.Auto, fingerprint = true)
    val MinHeight = prop<UiLength>("min-height", UiLength.Auto, fingerprint = true)
    val MaxWidth = prop<UiLength>("max-width", UiLength.Auto, fingerprint = true)
    val MaxHeight = prop<UiLength>("max-height", UiLength.Auto, fingerprint = true)
    val AspectRatio = prop<Float?>("aspect-ratio", null, fingerprint = true)
    val Padding = prop("padding", UiInsets.Zero, fingerprint = true)
    val Margin = prop("margin", UiInsets.Zero, fingerprint = true)
    val Gap = prop<UiLength>("gap", 0.px, fingerprint = true)
    val AlignHorizontal = prop("align-x", UiAlign.AUTO, fingerprint = true)
    val AlignVertical = prop("align-y", UiAlign.AUTO, fingerprint = true)
    val AlignItemsHorizontal = prop("align-items-x", UiAlign.AUTO, fingerprint = true)
    val AlignItemsVertical = prop("align-items-y", UiAlign.AUTO, fingerprint = true)
    val AlignItems = prop("align-items", UiAlign.AUTO, fingerprint = true)
    val AlignSelf = prop("align-self", UiAlign.AUTO, fingerprint = true)
    val JustifySelf = prop("justify-self", UiAlign.AUTO, fingerprint = true)
    val JustifyContent = prop("justify-content", UiAlign.AUTO, fingerprint = true)
    val Grow = prop("grow", 0f, fingerprint = true)
    val Position = prop("position", UiPosition(), fingerprint = true)

    // Border is split so a rule setting `border: 2px color` (width+colour) never wipes a
    // radius set elsewhere. Only width participates in layout; colour/radius are visual.
    val BorderWidth = prop("border-width", UiInsets.Zero, fingerprint = true)
    val BorderColor = prop("border-color", UiColor.Transparent, phases = DrawPhase)
    val BorderRadius = prop("border-radius", 0f, phases = DrawPhase)

    // Visuals
    val Background = prop<UiPaint>(
        "background", UiPaint.None,
        phases = DrawPhase, interpolate = ::interpolatePaint,
    )
    val Foreground = inheritedProp(
        "foreground", UiColor.White,
        phases = DrawPhase, interpolate = UiColor::interpolate,
    )
    val Image = prop<UiBoundString?>("image", null, phases = DrawPhase)
    val Shader = prop<UiBoundString?>("shader", null, phases = DrawPhase)
    val Shadows = prop<List<UiShadow>>(
        "shadow", emptyList(), aliases = setOf("box-shadow"),
        interpolate = ::interpolateShadows,
    )
    val Opacity = prop(
        "opacity", 1f,
        phases = DrawPhase, interpolate = ::lerp, sanitize = { it.coerceIn(0f, 1f) },
    )
    val Tint = prop("tint", UiColor.White, phases = DrawPhase, combine = ::mulColor, interpolate = UiColor::interpolate)
    val Filter = prop("filter", UiFilterChain.Empty, interpolate = UiFilterChain::interpolate)
    val BackdropFilter = prop("backdrop-filter", UiFilterChain.Empty, interpolate = UiFilterChain::interpolate)
    val BackfaceVisibility = prop("backface-visibility", UiBackfaceVisibility.VISIBLE)
    val Clip = prop("clip", false)
    val ClipShape = prop<Shape?>("clip-shape", null)
    val Layer = prop("layer", 0)
    val ImageFit = prop("image-fit", UiImageFit.STRETCH)
    val ImageSlice = prop("image-slice", UiInsets.all(4.px))
    val NodeShape = prop<Shape?>("shape", null)
    val ShapeFill = prop<UiPaint?>("shape-fill", null)
    val ShapeStroke = prop<UiPaint?>("shape-stroke", null)
    val ShapeStrokeWidth = prop<UiLength?>("shape-stroke-width", null)

    // Transform (split into independent properties; `transform` groups them for transitions).
    // Only *active states* stack — translate/rotate add, scale multiplies (combine); base and
    // a single state overlay last-wins (no merge).
    val Translate = prop("translate", UiVec3(), group = "transform", combine = ::addVec3, interpolate = ::interpolateVec3)
    val Rotate = prop("rotate", UiVec3(), group = "transform", combine = ::addVec3, interpolate = ::interpolateVec3)
    val Scale = prop("scale", UiVec3(1f, 1f, 1f), group = "transform", combine = ::mulVec3, interpolate = ::interpolateVec3)
    val Pivot = prop(
        "pivot", UiTransformPivot.Center,
        aliases = setOf("transform-origin"), group = "transform",
    )
    val Perspective = prop("perspective", 0f, group = "transform", interpolate = ::lerp)

    // Input — one independent property per capability. Only Scrollable affects layout
    // (it changes overflow/measurement); the rest are pure input concerns, so a hover or
    // focusability change no longer forces a relayout.
    val Hoverable = prop("hoverable", false, phases = InputPhase)
    val Clickable = prop("clickable", false, phases = InputPhase)
    val Focusable = prop("focusable", false, phases = InputPhase)
    val Draggable = prop("draggable", false, phases = InputPhase)
    val Scroll = prop<ScrollAxes?>("scroll", null, fingerprint = true)
    val InputTransparent = prop("input-transparent", false, phases = InputPhase)
    val Cursor = inheritedProp("cursor", UiCursorShape.DEFAULT, phases = InputPhase)

    // Widgets
    val Scrollbar = prop("scrollbar", UiScrollbarStyle(), fingerprint = true, merge = UiScrollbarStyle::merge)
    val Slider = prop("slider", UiSliderStyle(), merge = UiSliderStyle::merge)
    val Checkbox = prop("checkbox", UiCheckboxStyle(), merge = UiCheckboxStyle::merge)
    val TextField = prop("text-field", UiTextFieldStyle(), fingerprint = true, merge = UiTextFieldStyle::merge)

    // Text
    val TextWrap = prop("text-wrap", true, fingerprint = true)
    val TextOverflow = prop("text-overflow", UiTextOverflow.SHOW)
    val BoxDecorationBreak = prop("box-decoration-break", UiBoxDecorationBreak.SLICE, fingerprint = true)
    val TextAlign = inheritedProp("text-align", UiTextAlign.LEFT, fingerprint = true)
    val LineSpacing = inheritedProp("line-spacing", 0f, fingerprint = true)
    val SpaceWidth = inheritedProp<Float?>("space-width", null, fingerprint = true)
    val FontSize = inheritedProp("font-size", DefaultUiFontSize, fingerprint = true)
    val FontFamily = prop<String?>("font-family", null, fingerprint = true)
    val TextEffects = prop<List<UiTextEffect>>("text-effects", emptyList(), merge = { a, b -> a + b })
    val Typing = prop<UiTyping?>("typing", null)

    // Motion
    val Transitions = prop<List<UiTransition>>(
        "transition", emptyList(),
        merge = { current, next -> current.mergeUiTransitions(next) },
    )
    val Animations = prop<List<UiAnimation>>("animation", emptyList())
}

// Note: the declaration helpers live inside [UiProps] on purpose — its initialization
// must not touch this file's top-level class, whose static init reads UiProps back.

// -- Patch accessors -------------------------------------------------------------------

var UiStylePatch.width by UiProps.Width
var UiStylePatch.height by UiProps.Height
var UiStylePatch.minWidth by UiProps.MinWidth
var UiStylePatch.minHeight by UiProps.MinHeight
var UiStylePatch.maxWidth by UiProps.MaxWidth
var UiStylePatch.maxHeight by UiProps.MaxHeight
var UiStylePatch.aspectRatio by UiProps.AspectRatio
var UiStylePatch.padding by UiProps.Padding
var UiStylePatch.margin by UiProps.Margin
var UiStylePatch.gap by UiProps.Gap
var UiStylePatch.alignHorizontal by UiProps.AlignHorizontal
var UiStylePatch.alignVertical by UiProps.AlignVertical
var UiStylePatch.alignItemsHorizontal by UiProps.AlignItemsHorizontal
var UiStylePatch.alignItemsVertical by UiProps.AlignItemsVertical
var UiStylePatch.alignItems by UiProps.AlignItems
var UiStylePatch.alignSelf by UiProps.AlignSelf
var UiStylePatch.justifySelf by UiProps.JustifySelf
var UiStylePatch.justifyContent by UiProps.JustifyContent
var UiStylePatch.grow by UiProps.Grow
var UiStylePatch.position by UiProps.Position
var UiStylePatch.borderWidth by UiProps.BorderWidth
var UiStylePatch.borderColor by UiProps.BorderColor
var UiStylePatch.borderRadius by UiProps.BorderRadius

/** Composite view over the three independent border props. */
var UiStylePatch.border: UiBorder?
    get() {
        val width = this[UiProps.BorderWidth]
        val color = this[UiProps.BorderColor]
        val radius = this[UiProps.BorderRadius]
        if (width == null && color == null && radius == null) return null
        return UiBorder(width ?: UiInsets.Zero, color ?: UiColor.Transparent, radius ?: 0f)
    }
    set(value) {
        this[UiProps.BorderWidth] = value?.width
        this[UiProps.BorderColor] = value?.color
        this[UiProps.BorderRadius] = value?.radius
    }
var UiStylePatch.background by UiProps.Background
var UiStylePatch.foreground by UiProps.Foreground
var UiStylePatch.image by UiProps.Image
var UiStylePatch.shader by UiProps.Shader
var UiStylePatch.shadows by UiProps.Shadows
var UiStylePatch.opacity by UiProps.Opacity
var UiStylePatch.tint by UiProps.Tint
var UiStylePatch.filter by UiProps.Filter
var UiStylePatch.backdropFilter by UiProps.BackdropFilter
var UiStylePatch.backfaceVisibility by UiProps.BackfaceVisibility
var UiStylePatch.clip by UiProps.Clip
var UiStylePatch.clipShape by UiProps.ClipShape
var UiStylePatch.layer by UiProps.Layer
var UiStylePatch.imageFit by UiProps.ImageFit
var UiStylePatch.imageSlice by UiProps.ImageSlice
var UiStylePatch.shape by UiProps.NodeShape
var UiStylePatch.shapeFill by UiProps.ShapeFill
var UiStylePatch.shapeStroke by UiProps.ShapeStroke
var UiStylePatch.shapeStrokeWidth by UiProps.ShapeStrokeWidth
var UiStylePatch.translate by UiProps.Translate
var UiStylePatch.rotate by UiProps.Rotate
var UiStylePatch.scale by UiProps.Scale
var UiStylePatch.pivot by UiProps.Pivot
var UiStylePatch.perspective by UiProps.Perspective
var UiStylePatch.hoverable by UiProps.Hoverable
var UiStylePatch.clickable by UiProps.Clickable
var UiStylePatch.focusable by UiProps.Focusable
var UiStylePatch.draggable by UiProps.Draggable
var UiStylePatch.scroll by UiProps.Scroll
var UiStylePatch.inputTransparent by UiProps.InputTransparent
var UiStylePatch.cursor by UiProps.Cursor

/**
 * Composite view over the four input capability props. Reading returns the merged flags;
 * writing turns *on* only the flags that are true, so multiple event modifiers accumulate
 * (OR) instead of clobbering each other. To force a flag off (e.g. a `:disabled` rule),
 * set the individual prop (`clickable = false`) rather than assigning `input`.
 */
var UiStylePatch.input: UiInputStyle
    get() = UiInputStyle(
        hoverable = this[UiProps.Hoverable] ?: false,
        clickable = this[UiProps.Clickable] ?: false,
        focusable = this[UiProps.Focusable] ?: false,
        draggable = this[UiProps.Draggable] ?: false,
    )
    set(value) {
        if (value.hoverable) this[UiProps.Hoverable] = true
        if (value.clickable) this[UiProps.Clickable] = true
        if (value.focusable) this[UiProps.Focusable] = true
        if (value.draggable) this[UiProps.Draggable] = true
    }
var UiStylePatch.scrollbar by UiProps.Scrollbar
var UiStylePatch.slider by UiProps.Slider
var UiStylePatch.checkbox by UiProps.Checkbox
var UiStylePatch.textField by UiProps.TextField
var UiStylePatch.textWrap by UiProps.TextWrap
var UiStylePatch.textOverflow by UiProps.TextOverflow
var UiStylePatch.boxDecorationBreak by UiProps.BoxDecorationBreak
var UiStylePatch.textAlign by UiProps.TextAlign
var UiStylePatch.lineSpacing by UiProps.LineSpacing
var UiStylePatch.spaceWidth by UiProps.SpaceWidth
var UiStylePatch.fontSize by UiProps.FontSize
var UiStylePatch.fontFamily by UiProps.FontFamily
var UiStylePatch.textEffects by UiProps.TextEffects
var UiStylePatch.typing by UiProps.Typing
var UiStylePatch.transitions by UiProps.Transitions
var UiStylePatch.animations by UiProps.Animations

/** Composite width+height view kept for ergonomic writes; explicitness stays per-axis. */
var UiStylePatch.size: UiSize?
    get() = compositeSize(this[UiProps.Width], this[UiProps.Height])
    set(value) {
        this[UiProps.Width] = value?.width
        this[UiProps.Height] = value?.height
    }

var UiStylePatch.minSize: UiSize?
    get() = compositeSize(this[UiProps.MinWidth], this[UiProps.MinHeight])
    set(value) {
        this[UiProps.MinWidth] = value?.width
        this[UiProps.MinHeight] = value?.height
    }

var UiStylePatch.maxSize: UiSize?
    get() = compositeSize(this[UiProps.MaxWidth], this[UiProps.MaxHeight])
    set(value) {
        this[UiProps.MaxWidth] = value?.width
        this[UiProps.MaxHeight] = value?.height
    }

/** Composite transform view over the five independent transform properties. */
var UiStylePatch.transform: UiTransform?
    get() {
        val translate = this[UiProps.Translate]
        val rotate = this[UiProps.Rotate]
        val scale = this[UiProps.Scale]
        val pivot = this[UiProps.Pivot]
        val perspective = this[UiProps.Perspective]
        if (translate == null && rotate == null && scale == null && pivot == null && perspective == null) return null
        return UiTransform(
            translate = translate ?: UiVec3(),
            rotate = rotate ?: UiVec3(),
            scale = scale ?: UiVec3(1f, 1f, 1f),
            pivot = pivot ?: UiTransformPivot.Center,
            perspective = perspective ?: 0f,
        )
    }
    set(value) {
        this[UiProps.Translate] = value?.translate
        this[UiProps.Rotate] = value?.rotate
        this[UiProps.Scale] = value?.scale
        this[UiProps.Pivot] = value?.pivot
        this[UiProps.Perspective] = value?.perspective
    }

private fun compositeSize(width: UiLength?, height: UiLength?): UiSize? {
    if (width == null && height == null) return null
    return UiSize(width ?: UiLength.Auto, height ?: UiLength.Auto)
}

// -- Computed style accessors ----------------------------------------------------------

val UiComputedStyle.width by UiProps.Width
val UiComputedStyle.height by UiProps.Height
val UiComputedStyle.aspectRatio by UiProps.AspectRatio
val UiComputedStyle.padding by UiProps.Padding
val UiComputedStyle.margin by UiProps.Margin
val UiComputedStyle.gap by UiProps.Gap
val UiComputedStyle.alignHorizontal by UiProps.AlignHorizontal
val UiComputedStyle.alignVertical by UiProps.AlignVertical
val UiComputedStyle.alignItemsHorizontal by UiProps.AlignItemsHorizontal
val UiComputedStyle.alignItemsVertical by UiProps.AlignItemsVertical
val UiComputedStyle.alignItems by UiProps.AlignItems
val UiComputedStyle.alignSelf by UiProps.AlignSelf
val UiComputedStyle.justifySelf by UiProps.JustifySelf
val UiComputedStyle.justifyContent by UiProps.JustifyContent
val UiComputedStyle.grow by UiProps.Grow
val UiComputedStyle.position by UiProps.Position

val UiComputedStyle.border: UiBorder
    get() = UiBorder(this[UiProps.BorderWidth], this[UiProps.BorderColor], this[UiProps.BorderRadius])
val UiComputedStyle.background by UiProps.Background
val UiComputedStyle.foreground by UiProps.Foreground
val UiComputedStyle.image by UiProps.Image
val UiComputedStyle.shader by UiProps.Shader
val UiComputedStyle.shadows by UiProps.Shadows
val UiComputedStyle.opacity by UiProps.Opacity
val UiComputedStyle.tint by UiProps.Tint
val UiComputedStyle.filter by UiProps.Filter
val UiComputedStyle.backdropFilter by UiProps.BackdropFilter
val UiComputedStyle.backfaceVisibility by UiProps.BackfaceVisibility
val UiComputedStyle.clip by UiProps.Clip
val UiComputedStyle.clipShape by UiProps.ClipShape
val UiComputedStyle.layer by UiProps.Layer
val UiComputedStyle.imageFit by UiProps.ImageFit
val UiComputedStyle.imageSlice by UiProps.ImageSlice
val UiComputedStyle.shape by UiProps.NodeShape
val UiComputedStyle.shapeFill by UiProps.ShapeFill
val UiComputedStyle.shapeStroke by UiProps.ShapeStroke
val UiComputedStyle.shapeStrokeWidth by UiProps.ShapeStrokeWidth
val UiComputedStyle.translate by UiProps.Translate
val UiComputedStyle.rotate by UiProps.Rotate
val UiComputedStyle.scale by UiProps.Scale
val UiComputedStyle.pivot by UiProps.Pivot
val UiComputedStyle.perspective by UiProps.Perspective
val UiComputedStyle.hoverable by UiProps.Hoverable
val UiComputedStyle.clickable by UiProps.Clickable
val UiComputedStyle.focusable by UiProps.Focusable
val UiComputedStyle.draggable by UiProps.Draggable

val UiComputedStyle.scrollAxes: ScrollAxes? get() = this[UiProps.Scroll]

val UiComputedStyle.scrollable: Boolean get() = this[UiProps.Scroll] != null

val UiComputedStyle.input: UiInputStyle
    get() = UiInputStyle(
        hoverable = this[UiProps.Hoverable],
        clickable = this[UiProps.Clickable],
        focusable = this[UiProps.Focusable],
        draggable = this[UiProps.Draggable],
    )
val UiComputedStyle.cursor by UiProps.Cursor
val UiComputedStyle.scrollbar by UiProps.Scrollbar
val UiComputedStyle.slider by UiProps.Slider
val UiComputedStyle.checkbox by UiProps.Checkbox
val UiComputedStyle.textField by UiProps.TextField
val UiComputedStyle.textWrap by UiProps.TextWrap
val UiComputedStyle.textOverflow by UiProps.TextOverflow
val UiComputedStyle.inputTransparent by UiProps.InputTransparent
val UiComputedStyle.boxDecorationBreak by UiProps.BoxDecorationBreak
val UiComputedStyle.textAlign by UiProps.TextAlign
val UiComputedStyle.lineSpacing by UiProps.LineSpacing
val UiComputedStyle.spaceWidth by UiProps.SpaceWidth
val UiComputedStyle.fontSize by UiProps.FontSize
val UiComputedStyle.fontFamily by UiProps.FontFamily
val UiComputedStyle.textEffects by UiProps.TextEffects
val UiComputedStyle.typing by UiProps.Typing
val UiComputedStyle.transitions by UiProps.Transitions
val UiComputedStyle.animations by UiProps.Animations

val UiComputedStyle.size: UiSize get() = UiSize(this[UiProps.Width], this[UiProps.Height])
val UiComputedStyle.minSize: UiSize get() = UiSize(this[UiProps.MinWidth], this[UiProps.MinHeight])
val UiComputedStyle.maxSize: UiSize get() = UiSize(this[UiProps.MaxWidth], this[UiProps.MaxHeight])

val UiComputedStyle.transform: UiTransform
    get() = UiTransform(
        translate = this[UiProps.Translate],
        rotate = this[UiProps.Rotate],
        scale = this[UiProps.Scale],
        pivot = this[UiProps.Pivot],
        perspective = this[UiProps.Perspective],
    )

private val DefaultStyle by lazy { UiStylePatch().resolve() }

fun defaultModifierSnapshot(): UiComputedStyle = DefaultStyle

