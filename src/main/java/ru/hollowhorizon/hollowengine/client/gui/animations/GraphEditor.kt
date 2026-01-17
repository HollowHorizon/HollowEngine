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
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs.GridBackground
import kotlin.math.atan2

class GraphEditor {
    val scrollState = ScrollState()
    val scaleState = mutableStateOf(1.0f)
    var scale: Float = 1f

    val nodes = mutableStateListOf<GraphNode>()
    val connections = mutableStateListOf<GraphConnection>()

    val selectedNode = mutableStateOf<GraphNode?>(null)

    private var dragNode: GraphNode? = null
    private val dragStartPos = MutableVec2f(0f, 0f)
    private val nodeStartPos = MutableVec2f(0f, 0f)

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

                modifier.onWheelY {
                    if (KeyboardInput.isCtrlDown) {
                        val newScale =
                            (scaleState.value * (if (it.pointer.scroll.y > 0) 1.1f else 0.9f)).coerceIn(0.2f, 2.0f)
                        scaleState.set(newScale)
                        surface.triggerUpdate()
                    } else {
                        scrollState.scrollDpX(it.pointer.scroll.x * -20f)
                        scrollState.scrollDpY(it.pointer.scroll.y * -20f)
                        surface.triggerUpdate()
                    }
                }.onDragStart {
                    if (it.pointer.isLeftButtonDown) {
                        selectedNode.set(null)
                        surface.triggerUpdate()
                    }
                }.onDrag {
                    if (it.pointer.isRightButtonDown || (it.pointer.isLeftButtonDown && dragNode == null)) {
                        scrollState.scrollDpX(-it.pointer.delta.x / UiScale.measuredScale)
                        scrollState.scrollDpY(-it.pointer.delta.y / UiScale.measuredScale)
                        surface.triggerUpdate()
                    }
                }

                ScrollPane(scrollState) {
                    modifier.layout(CellLayout).onClick {
                        selectedNode.set(null)
                        surface.triggerUpdate()
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

                connections.forEach { conn ->
                    val from = nodes.find { it.id == conn.fromNodeId }
                    val to = nodes.find { it.id == conn.toNodeId }

                    if (from != null && to != null) {
                        val start =
                            Vec2f(Dp.fromPx((from.x + from.width / 2) * scale).px, Dp.fromPx((from.y + 5f) * scale).px)
                        val end = Vec2f(Dp.fromPx((to.x + to.width / 2) * scale).px, Dp.fromPx(to.y * scale).px)

                        val color = if (selectedNode.value == from) Color.WHITE else conn.color

                        with(uiNode) {
                            drawDashedArrow(start, end, color, scale, conn.label, textProps)
                        }
                    }
                }
            })
        }
    }

    private fun UiScope.renderNode(node: GraphNode) {
        val isSelected = selectedNode.use() == node

        Column {
            modifier.width(Dp(node.width * scale)).height(FitContent)
                .margin(start = Dp.fromPx(node.x * scale), top = Dp.fromPx(node.y * scale))
                .zLayer(if (isSelected) 200 else 100)

            modifier.onDragStart {
                if (it.pointer.isLeftButtonDown) {
                    dragNode = node
                    dragStartPos.set(it.screenPosition)
                    nodeStartPos.set(node.x, node.y)
                    selectedNode.set(node)
                    surface.triggerUpdate()
                }
            }.onDrag {
                if (dragNode == node) {
                    val dx = (it.screenPosition.x - dragStartPos.x) / scale
                    val dy = (it.screenPosition.y - dragStartPos.y) / scale
                    node.x = nodeStartPos.x + dx
                    node.y = nodeStartPos.y + dy
                    surface.triggerUpdate()
                }
            }.onDragEnd { dragNode = null }.onClick {
                selectedNode.set(node)
                surface.triggerUpdate()
            }

            val nodeColor by animateColorAsState(if (isSelected) node.color.mix(Color.WHITE, 0.3f) else node.color)
            val borderColor by animateColorAsState(if (isSelected) ColorTheme.Accents.Main else node.color)
            modifier
                .background(
                    RoundRectGradientBackground(
                        Dimensions.PaddingMedium,
                        nodeColor.mix(ColorTheme.UI.BackgroundSecondary, 0.5f), ColorTheme.UI.BackgroundSecondary,
                        0.dp, 0.dp,
                        Dp(node.width * 1.5f * scale), Dp(75f * scale)
                    )
                )
                .border(RoundRectBorder(borderColor, Dimensions.PaddingMedium, Dimensions.PaddingSmall))
                .padding(Dimensions.PaddingMedium.scaled())

            Text(node.title) {
                modifier.font(FontProps(isBold = true)).textColor(Color.WHITE).alignX(AlignmentX.Center)
            }

            Row {
                modifier.alignX(AlignmentX.Center)
                Badge("0.0s")
                Badge("Base")
            }
        }
    }

    private fun UiScope.Badge(text: String) {
        Box {
            modifier.margin(Dimensions.PaddingNormal.scaled()).margin(bottom=0.dp)
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
                modifier.font(FontProps(size = 18f, isBold = true)).textColor(ColorTheme.UI.WhiteReplacement)
                    .margin(bottom = Dimensions.PaddingLarge)
            }

            val node = selectedNode.use()
            if (node != null) {
                Text(node.title) { modifier.textColor(Color.WHITE) }
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
                    .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.5f)).font(FontProps(size = 10f))
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

                        nodes.forEach { n ->
                            val mx = n.x * mapScale + mapOffsetX
                            val my = n.y * mapScale + mapOffsetY
                            val mw = n.width * mapScale
                            val mh = 40f * mapScale

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
    val normDir = Vec2f(dir.x / length, dir.y / length)

    val dashLen = 10f * scale
    val gapLen = 5f * scale
    var currentPos = 0f

    val draw = node.getPlainBuilder()
    val text = node.getTextBuilder(textProps.font)

    while (currentPos < length - 15f * scale) {
        val p1 = Vec2f(from.x + normDir.x * currentPos, from.y + normDir.y * currentPos)
        val endSegment = (currentPos + dashLen).coerceAtMost(length - 15f * scale)
        val p2 = Vec2f(from.x + normDir.x * endSegment, from.y + normDir.y * endSegment)

        draw.configure(color) {
            line(p1.x, p1.y, p2.x, p2.y, 2f * scale)
        }
        currentPos += dashLen + gapLen
    }

    val arrowTip = to - (normDir * (5f * scale))
    val angleRad = atan2(normDir.y, normDir.x)
    val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()

    draw.configure(color) {
        circle {
            center.set(arrowTip.x, arrowTip.y, 0f)
            radius = 10f * scale
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