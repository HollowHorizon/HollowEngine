package ru.hollowhorizon.hollowengine.client.gui.timeline.ui

import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.timeline.*

fun UiScope.PropertiesPanel(controller: TimelineController, width: Dimension? = null) {
    Column(width = width ?: Dp(controller.propertiesPanelWidth.use()), height = Grow.Std) {
        modifier
            .backgroundColor(ColorTheme.UI.BackgroundDarker)
            .zLayer(20)
            .onClick { it.pointer.consume() }

        ScrollArea(
            width = Grow.Std,
            height = Grow.Std,
            withVerticalScrollbar = true,
            state = rememberScrollState(),
            containerModifier = {
                it.backgroundColor(ColorTheme.UI.BackgroundSecondary).margin(end = Dimensions.PaddingMedium)
            },
            vScrollbarModifier = {
                it.colors(
                    trackColor = ColorTheme.UI.BackgroundSecondary,
                    trackHoverColor = ColorTheme.UI.BackgroundElements,
                    color = ColorTheme.UI.BackgroundAccent,
                    hoverColor = ColorTheme.UI.WhiteReplacement
                )
            }
        ) {
            modifier.width(Grow.Std)
                .margin(end = Dimensions.PaddingMedium)

            Column(width = Grow.Std) {
                modifier.padding(Dimensions.PaddingSmall)

                if (controller.selectedKeyframes.isNotEmpty()) {
                    val selectedKey = controller.selectedKeyframes.first()

                    val track = controller.getAllTracks()
                        .filterIsInstance<AnimTrack<*>>()
                        .find { it.keyframes.contains(selectedKey) }

                    if (track != null) {
                        KeyframeProperties(selectedKey, track, controller)
                    } else {
                        Text("Ошибка: Трек не найден") { modifier.textColor(ColorTheme.Console.Error) }
                    }

                } else if (controller.isWorkAreaSelected.use()) {
                    Text("Конец рабочей зоны") {
                        modifier.textColor(ColorTheme.Accents.Main).margin(bottom = sizes.smallGap)
                    }

                    TimePropertyField("Time:", controller.workAreaEnd.value) { inputTime ->
                        val clamped = inputTime.coerceAtLeast(0.1f)
                        controller.workAreaEnd.set(clamped)
                        if (controller.currentTime.value > clamped) {
                            controller.currentTime.set(0f)
                        }
                        surface.triggerUpdate()
                    }
                } else {
                    Text("Ничего не выбрано") {
                        modifier.textColor(ColorTheme.Accents.Main).margin(Dimensions.PaddingMedium)
                            .textAlignX(AlignmentX.Center).width(Grow.Std)
                    }
                }
            }
        }
    }
}


private fun UiScope.KeyframeProperties(
    key: Keyframe<*>,
    track: AnimTrack<*>,
    controller: TimelineController,
) {
    Column(width = Grow.Std) {
        modifier
            .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, sizes.smallGap))
            .padding(sizes.smallGap)

        TimePropertyField("Time:", key.time) { inputTime ->
            val limitMax = controller.workAreaEnd.value
            val clamped = inputTime.coerceIn(0f, limitMax)
            if (key.time != clamped) {
                controller.moveKeyframe(track, key, clamped)
                surface.triggerUpdate()
            }
        }
    }

    Box(height = sizes.gap) {}

    Column(width = Grow.Std) {
        modifier
            .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, sizes.smallGap))
            .padding(sizes.smallGap)

        drawKeyframeValueEditor(key, track, controller)
    }

    Box(height = sizes.gap) {}

    Text("График интерполяции") {
        modifier.textColor(ColorTheme.Accents.Main).margin(bottom = sizes.smallGap)
    }

    val curveWidth = remember(100f)

    Box(width = Grow.Std, height = Dp.fromPx(curveWidth.use() * 0.5625f)) {
        modifier
            .margin(bottom = sizes.gap)
            .background(RoundRectBackground(Color("111111"), sizes.smallGap))
            .onMeasured {
                curveWidth.set(it.widthPx)
            }

        val graphColor = ColorTheme.Accents.Main
        val axisColor = Color.WHITE.withAlpha(0.2f)

        modifier.background(UiRenderer { node ->
            renderEasingGraph(node, key.easing, graphColor, axisColor)
        })
    }

    EasingSelector(key, controller, sizes, surface)

    Box(height = sizes.largeGap) {}

    Button("Удалить ключ") {
        modifier
            .width(Grow.Std)
            .colors(buttonColor = ColorTheme.Console.Error, textHoverColor = Color.WHITE)
            .onClick {
                controller.deleteSelectedKeyframes()
                surface.triggerUpdate()
            }
    }
}

private fun renderEasingGraph(node: UiNode, easing: Easing.Easing, curveColor: Color, axisColor: Color) {
    node.apply {
        val builder = getPlainBuilder()
        val pad = sizes.smallGap.px * 2f
        val w = widthPx - pad * 2f
        val h = heightPx - pad * 2f

        val x0 = pad
        val y0 = heightPx - pad

        builder.configured(axisColor) {
            line(x0, y0, x0 + w, y0, 1.dp.px)
            line(x0, y0 - h, x0 + w, y0 - h, 1.dp.px)
        }

        builder.configured(curveColor) {
            val steps = 64
            var prevX = x0
            var prevY = y0 - (easing.eased(0f) * h)

            for (i in 1..steps) {
                val t = i / steps.toFloat()
                val v = easing.eased(t)

                val px = x0 + t * w
                val py = y0 - v * h

                line(prevX, prevY, px, py, 2.dp.px)

                prevX = px
                prevY = py
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun UiScope.drawKeyframeValueEditor(key: Keyframe<*>, track: AnimTrack<*>, controller: TimelineController) {
    val driver = track.driver as PropertyDriver<Any?>
    val typedKey = key as Keyframe<Any?>

    with(driver) {
        drawEditor(typedKey.value) { newValue ->
            controller.updateSelectedValues("Edit keyframe value") {
                typedKey.value = newValue
            }
        }
    }
}

private fun UiScope.TimePropertyField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val formattedValue = "%.3f".format(value).replace(',', '.')

    val textState = remember(formattedValue)

    Row(width = Grow.Std, height = 26.dp) {
        modifier.margin(bottom = 2.dp)

        Text(label) {
            modifier
                .width(Grow(1f))
                .alignY(AlignmentY.Center)
                .textColor(ColorTheme.Accents.Main)
        }

        TextField(textState.use()) {
            modifier
                .width(80.dp)
                .alignY(AlignmentY.Center)
                .textAlignX(AlignmentX.End)
                .colors(
                    lineColorFocused = ColorTheme.Accents.Main,
                    selectionColor = ColorTheme.Accents.Main.withAlpha(0.5f),
                    textColor = ColorTheme.UI.WhiteReplacement
                )
                .onChange { input ->
                    textState.set(input)
                }
                .onEnterPressed { input ->
                    val safeInput = input.replace(',', '.')
                    val parsedValue = safeInput.toFloatOrNull()

                    if (parsedValue != null) {
                        onValueChange(parsedValue)
                        surface.requestFocus(null)
                    } else {
                        textState.set(formattedValue)
                    }
                }
        }
    }
}

private fun UiScope.EasingSelector(
    keyframe: Keyframe<*>,
    controller: TimelineController,
    sizes: Sizes,
    surface: UiSurface,
) {
    Row(width = Grow.Std) {
        modifier
            .margin(bottom = sizes.smallGap)
            .onClick { controller.isEasingListExpanded.toggle() }

        val isExpanded = controller.isEasingListExpanded.use()
        val targetAngle = if (isExpanded) 0f else -180f
        val arrowAngle = animateFloatAsState(targetAngle).use()

        Box(width = 10.dp, height = 10.dp) {
            modifier
                .alignY(AlignmentY.Center)
                .margin(end = sizes.smallGap)
                .background(UiRenderer { node ->
                    node.apply {
                        val imgMesh = surface.getMeshLayer(modifier.zLayer).addImage(controller.arrow)
                        imgMesh.builder.clear()
                        imgMesh.builder.configured(ColorTheme.Accents.Main.withAlpha(0.7f)) {
                            val cx = widthPx * 0.5f
                            val cy = heightPx * 0.5f
                            translate(cx, cy, 0f)
                            rotate(arrowAngle.deg, Vec3f.Z_AXIS)
                            translate(-cx, -cy, 0f)
                            rect {
                                isCenteredOrigin = false
                                origin.set(0f, 0f, 0f)
                                size.set(widthPx, heightPx)
                            }
                        }
                        imgMesh.applyShader(controller.arrow, null)
                    }
                })
        }
        Text("Список функций") {
            modifier.font(sizes.normalText).textColor(ColorTheme.Accents.Main.withAlpha(0.8f))
        }
    }

    if (controller.isEasingListExpanded.use()) {
        Column(width = Grow.Std) {
            val currentEasingFunc = keyframe.easing
            val activeCategory = easingTypes.find { cat -> cat.variants.any { it.function == currentEasingFunc } }

            easingTypes.chunked(2).forEach { rowItems ->
                Row(width = Grow.Std) {
                    modifier.margin(bottom = sizes.smallGap)
                    rowItems.forEachIndexed { index, category ->
                        val isSelected = activeCategory == category
                        EasingButton(category.name, isSelected, sizes) {
                            val defaultVariant =
                                if (category.variants.size > 2) category.variants[2] else category.variants[0]
                            keyframe.easing = defaultVariant.function
                            controller.onChanged?.invoke()
                            surface.triggerUpdate()
                        }
                        if (index == 0 && rowItems.size > 1) Box(width = sizes.smallGap) {}
                    }
                }
            }

            if (activeCategory != null && activeCategory.variants.size > 1) {
                Box(width = Grow.Std, height = 1.dp) {
                    modifier.margin(vertical = sizes.smallGap).backgroundColor(Color("31343D"))
                }
                activeCategory.variants.chunked(2).forEach { rowVariants ->
                    Row(width = Grow.Std) {
                        modifier.margin(bottom = sizes.smallGap)
                        rowVariants.forEachIndexed { vIndex, variant ->
                            val isVariantSelected = keyframe.easing == variant.function
                            EasingButton(variant.name, isVariantSelected, sizes) {
                                keyframe.easing = variant.function
                                controller.onChanged?.invoke()
                                surface.triggerUpdate()
                            }
                            if (vIndex == 0 && rowVariants.size > 1) Box(width = sizes.smallGap) {}
                        }
                    }
                }
            }
        }
    }
}

private fun UiScope.EasingButton(
    text: String,
    isSelected: Boolean,
    sizes: Sizes,
    onClick: () -> Unit,
) {
    val isHovered = remember(false)
    val colorNormal = Color.fromHex("31343D")
    val colorHover = Color.fromHex("454850")

    val targetColor = when {
        isSelected -> ColorTheme.Accents.Main
        isHovered.use() -> colorHover
        else -> colorNormal
    }
    val bg = animateColorAsState(targetColor, tween(duration = 0.1f)).use()
    val textColor = if (isSelected) ColorTheme.UI.BackgroundSecondary else Color("CCCCCC")

    Box(height = 24.dp) {
        modifier
            .width(Grow.Std)
            .background(RoundRectBackground(bg, 4.dp))
            .onEnter { isHovered.set(true) }
            .onExit { isHovered.set(false) }
            .onClick { onClick() }

        Text(text) {
            modifier
                .align(AlignmentX.Center, AlignmentY.Center)
                .font(sizes.normalText)
                .textColor(textColor)
        }
    }
}
