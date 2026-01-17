package ru.hollowhorizon.hollowengine.client.gui.animations

import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.geometry.TextProps
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.animateSpringFloatAsState
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.backgrounds
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.configure
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.findParentOfType
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs.GridBackground
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.Math.abs
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.Math.max
import kotlin.math.atan2

class GraphEditor {
    val scrollState = ScrollState()
    val scaleState = mutableStateOf(1.0f)
    var scale: Float = 1f

    val nodes = mutableStateListOf<GraphNode>()
    val connections = mutableStateListOf<GraphConnection>()

    val selectedNode = mutableStateOf<GraphNode?>(null)

    // Drag state
    private var dragNode: GraphNode? = null
    private val dragOffset = MutableVec2f()
    private val tempVec = MutableVec2f()

    private val connectionFont by lazy {
        MsdfFont(ColorTheme.Fonts.MONOCRAFT, 14f)
    }

    init {
        nodes.add(GraphNode(title = "Entry", x = 100f, y = 100f, color = Color("6BC872")))
        nodes.add(GraphNode(title = "Idle", x = 100f, y = 250f, color = Color("EB903F")))
        nodes.add(GraphNode(title = "Run", x = 100f, y = 450f, color = Color("5F6677")))
        nodes.add(GraphNode(title = "Frontflip", x = 400f, y = 100f, color = Color("5F6677")))
        nodes.add(GraphNode(title = "Any State", x = 600f, y = 150f, color = Color("548AF7")))

        connections.add(GraphConnection(nodes[0].id, nodes[1].id, "auto"))
        connections.add(GraphConnection(nodes[1].id, nodes[2].id, "speed > 0"))
        connections.add(GraphConnection(nodes[2].id, nodes[1].id, "stop")) // Двусторонняя связь
        connections.add(GraphConnection(nodes[1].id, nodes[3].id, "jump"))
    }

    context(scope: UiScope) fun EditorLayout() = with(scope) {
        scale = animateSpringFloatAsState(scaleState.use()).use()

        Row(Grow.Std, Grow.Std) {
            Box(Grow.Std, Grow.Std) {
                val bgColor = Color("1E1F22")
                val gridColor = Color("2A2E35")
                modifier.background(UiRenderer { node ->
                    node.apply {
                        getUiPrimitives().localRect(0f, 0f, widthPx, heightPx, bgColor)
                    }
                }).background(
                    GridBackground(
                        sectionSize = Dp(40f),
                        currentZoom = scale,
                        scrollState = scrollState,
                        lineWidth = Dp(1f),
                        lineColor = gridColor
                    )
                )

                // Zoom control
                modifier.onWheelY {
                    if (KeyboardInput.isCtrlDown) {
                        val newScale = (scaleState.value * (if (it.pointer.scroll.y > 0) 1.1f else 0.9f))
                            .coerceIn(0.2f, 2.0f)
                        scaleState.set(newScale)
                    } else {
                        if(KeyboardInput.isShiftDown) {
                            scrollState.scrollDpX(it.pointer.scroll.x * -20f)
                        } else {
                            scrollState.scrollDpY(it.pointer.scroll.y * -20f)
                        }
                    }
                }.onWheelX {
                    scrollState.scrollDpX(it.pointer.scroll.x * -20f)
                }

                modifier.onClick {
                    if (it.pointer.isLeftButtonDown) {
                        selectedNode.set(null)
                    }
                }

                // Pan control
                modifier.onDrag {
                    if (it.pointer.isRightButtonDown || (it.pointer.isLeftButtonDown && dragNode == null)) {
                        scrollState.scrollDpX(-it.pointer.delta.x / UiScale.measuredScale)
                        scrollState.scrollDpY(-it.pointer.delta.y / UiScale.measuredScale)
                    }
                }

                ScrollPane(scrollState) {
                    modifier.layout(CellLayout).onClick {
                        selectedNode.set(null)
                    }

                    renderConnections(connectionFont)

                    nodes.use().forEach { node ->
                        renderNode(node)
                    }
                }

                MiniMap()
            }

            PropertyPanel()
        }
    }

    private fun UiScope.renderConnections(font: MsdfFont) {
        Box(Grow.Std, Grow.Std) {
            modifier.layout(CellLayout).zLayer(-10)

            modifier.background(UiRenderer { uiNode ->
                val textProps = TextProps(font).apply {
                    scale = this@GraphEditor.scale
                    isYAxisUp = false
                }

                val connList = connections.use()

                connList.forEach { conn ->
                    val from = nodes.find { it.id == conn.fromNodeId }
                    val to = nodes.find { it.id == conn.toNodeId }

                    if (from != null && to != null) {
                        // Получаем данные координат и размеров
                        val fromX = from.xState.use()
                        val fromY = from.yState.use()
                        val fromW = from.widthState.use()
                        val fromH = from.heightState.use()

                        val toX = to.xState.use()
                        val toY = to.yState.use()
                        val toW = to.widthState.use()
                        val toH = to.heightState.use()

                        // Вычисляем центры узлов в пикселях (с учетом скролла ScrollPane не нужно, т.к. Box внутри него, но нужен масштаб)
                        // Координаты внутри ScrollPane локальны, поэтому просто scale
                        val centerA = Vec2f((fromX + fromW / 2) * scale, (fromY + fromH / 2) * scale)
                        val centerB = Vec2f((toX + toW / 2) * scale, (toY + toH / 2) * scale)

                        // Проверка на двунаправленную связь
                        val isBiDirectional = connList.any {
                            it.fromNodeId == conn.toNodeId && it.toNodeId == conn.fromNodeId
                        }

                        // Смещаем "виртуальные центры", если связь двойная
                        var targetCenterA = centerA
                        var targetCenterB = centerB

                        if (isBiDirectional) {
                            val dir = Vec2f(centerB.x - centerA.x, centerB.y - centerA.y)
                            val len = dir.length()
                            if (len > 0.001f) {
                                // Перпендикуляр
                                val perp = Vec2f(-dir.y / len, dir.x / len)
                                val offsetAmount = 15f * scale
                                val offset = Vec2f(perp.x * offsetAmount, perp.y * offsetAmount)

                                targetCenterA = Vec2f(centerA.x + offset.x, centerA.y + offset.y)
                                targetCenterB = Vec2f(centerB.x + offset.x, centerB.y + offset.y)
                            }
                        }

                        // Находим точки пересечения линии (TargetA -> TargetB) с границами прямоугольников
                        val startPos = getEdgePoint(targetCenterA, fromW * scale, fromH * scale, targetCenterB)
                        val endPos = getEdgePoint(targetCenterB, toW * scale, toH * scale, targetCenterA)

                        val color = if (selectedNode.value == from) Color.WHITE else conn.color

                        with(uiNode) {
                            // Передаем false для clip, чтобы стрелки не обрезались, если вылезут за пределы (хотя внутри Box(Grow) это редкость)
                            drawDashedArrow(startPos, endPos, color, scale, conn.label, textProps)
                        }
                    }
                }
            })
        }
    }

    /**
     * Вычисляет точку пересечения луча от center к target с границами прямоугольника (шириной w, высотой h),
     * расположенного в center.
     */
    private fun getEdgePoint(center: Vec2f, w: Float, h: Float, target: Vec2f): Vec2f {
        val dirX = target.x - center.x
        val dirY = target.y - center.y

        // Если центры совпадают (или очень близко), возвращаем центр
        if (abs(dirX) < 0.1f && abs(dirY) < 0.1f) return center

        val halfW = w / 2f
        val halfH = h / 2f

        // Определяем, какую границу пересекает луч: вертикальную или горизонтальную.
        // Для этого сравниваем тангенс угла луча с тангенсом угла диагонали прямоугольника.

        // Масштабируем вектор так, чтобы проверить пересечение с единичным квадратом,
        // это позволяет избавиться от проблем с разными пропорциями w и h
        val nx = dirX / halfW
        val ny = dirY / halfH

        // Выбираем t, чтобы выйти на границу 1.0
        val t = 1.0f / max(abs(nx), abs(ny))

        return Vec2f(center.x + nx * t * halfW, center.y + ny * t * halfH)
    }

    private fun UiScope.renderNode(node: GraphNode) {
        val isSelected = selectedNode.use() == node
        val x = node.xState.use()
        val y = node.yState.use()

        Column {
            modifier
                .width(FitContent)
                .height(FitContent)
                .margin(start = Dp.fromPx(x * scale), top = Dp.fromPx(y * scale))
                .zLayer(if (isSelected) 200 else 100)

            val isHovered by modifier.hoverable()

            // Сохраняем реальные размеры узла в логических единицах
            modifier.onMeasured {
                val logicalW = it.widthPx / scale
                val logicalH = it.heightPx / scale

                if (abs(node.widthState.value - logicalW) > 0.1f) {
                    node.widthState.set(logicalW)
                }
                if (abs(node.heightState.value - logicalH) > 0.1f) {
                    node.heightState.set(logicalH)
                }
            }

            modifier.onDragStart {
                if (it.pointer.isLeftButtonDown) {
                    dragNode = node
                    selectedNode.set(node)
                    val parent = uiNode.findParentOfType<ScrollPaneNode>()!!
                    parent.toLocal(it.screenPosition, tempVec)
                    val mouseLogicX = tempVec.x / scale
                    val mouseLogicY = tempVec.y / scale
                    dragOffset.set(mouseLogicX - node.xState.value, mouseLogicY - node.yState.value)
                }
            }.onDrag {
                if (dragNode == node) {
                    val parent = uiNode.findParentOfType<ScrollPaneNode>()!!
                    parent.toLocal(it.screenPosition, tempVec)

                    val mouseLogicX = tempVec.x / scale
                    val mouseLogicY = tempVec.y / scale

                    node.xState.set(mouseLogicX - dragOffset.x)
                    node.yState.set(mouseLogicY - dragOffset.y)
                }
            }.onDragEnd {
                dragNode = null
            }.onClick {
                selectedNode.set(node)
            }

            val nodeColor by animateColorAsState(
                if (isSelected || isHovered) node.color.mix(
                    Color.WHITE,
                    0.3f
                ) else node.color
            )
            val borderColor by animateColorAsState(if (isSelected) Color.WHITE else if (isHovered) ColorTheme.UI.WhiteReplacement else node.color)

            modifier
                .background(
                    RoundRectGradientBackground(
                        Dimensions.PaddingMedium,
                        nodeColor.mix(ColorTheme.UI.BackgroundSecondary, 0.5f), ColorTheme.UI.BackgroundSecondary,
                        0.dp, 0.dp,
                        Dp(150f * scale), Dp(75f * scale)
                    )
                )
                .border(RoundRectBorder(borderColor, Dimensions.PaddingMedium, Dimensions.PaddingSmall))
                .padding(Dimensions.PaddingMedium.scaled())

            Text(node.title) {
                modifier.font(FontProps(isBold = true)).textColor(Color.WHITE).alignX(AlignmentX.Center)
            }

            Row {
                modifier.alignX(AlignmentX.Center).margin(top = Dimensions.PaddingSmall.scaled())
                Badge("0.0s")
                Badge("Base")
            }
        }
    }

    fun ScrollState.position() = Vec2f(xScrollDp.value * UiScale.measuredScale, yScrollDp.value * UiScale.measuredScale)

    private fun UiScope.Badge(text: String) {
        Box {
            modifier
                .margin(horizontal = Dimensions.PaddingNormal.scaled())
                .padding(Dimensions.PaddingNormal.scaled())
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.3f), Dimensions.PaddingNormal.scaled()))
                .border(
                    RoundRectBorder(
                        ColorTheme.UI.BackgroundAccent, Dimensions.PaddingNormal.scaled(),
                        (Dimensions.PaddingSmall * 0.5f).scaled()
                    )
                )

            Text(text) {
                modifier.textColor(ColorTheme.UI.WhiteReplacement).font(FontProps(size = 10f))
            }
        }
    }

    private fun UiScope.PropertyPanel() {
        Column {
            modifier.width(Dp(300f)).height(Grow.Std).background(RectBackground(ColorTheme.UI.BackgroundSecondary))
                .padding(Dimensions.PaddingLarge).zLayer(1000)

            Text("СВОЙСТВА") {
                modifier.textColor(ColorTheme.UI.WhiteReplacement)
                    .margin(bottom = Dimensions.PaddingLarge)
            }

            val node = selectedNode.use()
            if (node != null) {
                Text(node.title) { modifier.textColor(Color.WHITE) }
                Text("Pos: ${node.xState.use().toInt()}, ${node.yState.use().toInt()}") {
                    modifier.textColor(Color.GRAY).margin(top = Dimensions.PaddingSmall)
                }
            } else {
                Text("Нет выделения") { modifier.textColor(Color.GRAY) }
            }
        }
    }

    private fun UiScope.MiniMap() {
        Column {
            modifier.width(Dp(200f)).height(Dp(150f)).align(AlignmentX.End, AlignmentY.Top)
                .margin(Dimensions.PaddingLarge)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingMedium))
                .border(
                    RoundRectBorder(
                        ColorTheme.UI.BackgroundAccent,
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall
                    )
                )
                .padding(Dimensions.PaddingMedium)
                .zLayer(1000)

            Text("Мини-карта") {
                modifier.align(AlignmentX.Center, AlignmentY.Center).margin(bottom = Dp(6f))
                    .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.5f))
            }

            Box(Grow.Std, Grow.Std) {
                modifier.margin(Dimensions.PaddingMedium)
                    .margin(top = Dimensions.PaddingSmall)
                modifier.backgrounds(
                    RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dp(4f)),
                    UiRenderer { node ->
                        val draw = node.getUiPrimitives()
                        val mapScale = 0.1f
                        val mapOffsetX = 50f
                        val mapOffsetY = 50f

                        nodes.use().forEach { n ->
                            val mx = n.xState.use() * mapScale + mapOffsetX
                            val my = n.yState.use() * mapScale + mapOffsetY
                            val mw = n.widthState.use() * mapScale
                            val mh = n.heightState.use() * mapScale

                            draw.roundRect(
                                node.leftPx + mx, node.topPx + my, mw, mh, 2f,
                                node.clipBoundsPx, n.color
                            )
                        }
                    })
            }
        }
    }

    private fun Dp.scaled() = Dp(this.value * scale)

    private fun FontProps(size: Float = 14f, isBold: Boolean = false) = MsdfFont(
        ColorTheme.Fonts.MONOCRAFT, size * scale, weight = if (isBold) MsdfFont.WEIGHT_BOLD else MsdfFont.WEIGHT_REGULAR
    )
}

context(node: UiNode) fun drawDashedArrow(
    from: Vec2f,
    to: Vec2f,
    color: Color,
    scale: Float,
    label: String,
    textProps: TextProps,
) {
    val dir = Vec2f(to.x - from.x, to.y - from.y)
    val length = dir.length()
    if (length < 1f) return

    val normDir = Vec2f(dir.x / length, dir.y / length)

    val dashLen = 10f * scale
    val gapLen = 5f * scale
    var currentPos = 0f

    val draw = node.getPlainBuilder()
    val text = node.getTextBuilder(textProps.font)

    val arrowSize = 10f * scale
    // Рисуем не до самого конца, чтобы оставить место для острия стрелки
    val endLimit = length - arrowSize

    while (currentPos < endLimit) {
        val p1 = Vec2f(from.x + normDir.x * currentPos, from.y + normDir.y * currentPos)
        val endSegment = (currentPos + dashLen).coerceAtMost(endLimit)
        val p2 = Vec2f(from.x + normDir.x * endSegment, from.y + normDir.y * endSegment)

        draw.configure(color) {
            line(p1.x, p1.y, p2.x, p2.y, 2f * scale)
        }
        currentPos += dashLen + gapLen
    }

    // Рисуем кружок-стрелку в конце (смещенный на радиус, чтобы касаться точки to)
    val arrowCenter = to - (normDir * (arrowSize))
    val angleRad = atan2(normDir.y, normDir.x)
    val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()

    draw.configure(color) {
        circle {
            center.set(arrowCenter.x, arrowCenter.y, 0f)
            radius = arrowSize
            steps = 3
            startDeg = angleDeg
        }
    }

    if (label.isNotEmpty()) {
        val mid = (from + to) * 0.5f

        textProps.apply {
            this.text = label
            origin.set(
                mid.x - font.textDimensions(label).width / 2f,
                mid.y + font.textDimensions(label).height / 2f,
                0f
            )
        }

        text.configure(ColorTheme.UI.WhiteReplacement) {
            text(textProps)
        }
    }
}