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
import ru.hollowhorizon.hollowengine.client.gui.markdown.rememberTarget
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs.GridBackground
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.TextEditorConfig
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.CompiledFileProvider
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.ScriptTextArea
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar.ComboBox
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode
import ru.hollowhorizon.hollowengine.client.utils.math.Interpolation
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.Math.abs
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.Math.max
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

class GraphEditor {
    val scrollState = ScrollState()
    val scaleState = mutableStateOf(1.0f)
    var scale: Float = 1f

    val nodes = mutableStateListOf<GraphNode>()
    val connections = mutableStateListOf<GraphConnection>()

    val selectedNode = mutableStateOf<GraphNode?>(null)

    val selectedConnection = mutableStateOf<GraphConnection?>(null)
    val hoveredConnection = mutableStateOf<GraphConnection?>(null)

    // Drag state
    private var dragNode: GraphNode? = null
    private val dragOffset = MutableVec2f()
    private val tempVec = MutableVec2f()

    private val viewportWidth = mutableStateOf(0f)
    private val viewportHeight = mutableStateOf(0f)

    private val lastMousePos = MutableVec2f()

    private var scrollPaneNode: ScrollPaneNode? = null

    val modelPath = mutableStateOf("hollowengine:models/entity/player_model.gltf")
    val availableAnimations = mutableStateListOf<String>()
    
    var onModelPathChanged: ((String) -> Unit)? = null

    private val connectionFont by lazy {
        MsdfFont(ColorTheme.Fonts.MONOCRAFT, 14f)
    }

    private val connectionHitThreshold = 8f

    init {
        val entry = GraphNode(
            title = "Entry",
            x = 100f,
            y = 100f,
            color = Color("6BC872"),
            type = NodeType.ENTRY,
        )
        val idle = GraphNode(
            title = "Idle",
            x = 100f,
            y = 250f,
            color = Color("EB903F"),
        )
        val run = GraphNode(
            title = "Run",
            x = 100f,
            y = 450f,
            color = Color("5F6677"),
        )
        val frontflip = GraphNode(
            title = "Frontflip",
            x = 400f,
            y = 100f,
            color = Color("5F6677"),
        )
        val anyState = GraphNode(
            title = "Any State",
            x = 600f,
            y = 150f,
            color = Color("548AF7"),
            type = NodeType.ANY,
        )

        nodes.addAll(listOf(entry, idle, run, frontflip, anyState))

        connections.add(GraphConnection(entry.id, idle.id, "auto"))
    }

    context(scope: UiScope) fun EditorLayout() = with(scope) {
        scale = animateSpringFloatAsState(scaleState.use()).use()

        Row(Grow.Std, Grow.Std) {
            Box(Grow.Std, Grow.Std) {
                modifier.onMeasured {
                    viewportWidth.set(it.widthPx)
                    viewportHeight.set(it.heightPx)
                }

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
                        offsetX = -scrollState.xScrollDp.use() * UiScale.measuredScale,
                        offsetY = -scrollState.yScrollDp.use() * UiScale.measuredScale,
                        lineWidth = Dp(1f),
                        lineColor = gridColor
                    )
                )

                modifier.onWheelY {
                    if (KeyboardInput.isCtrlDown) {
                        val newScale = (scaleState.value * (if (it.pointer.scroll.y > 0) 1.1f else 0.9f))
                            .coerceIn(0.2f, 2.0f)
                        scaleState.set(newScale)
                    } else {
                        if (KeyboardInput.isShiftDown) {
                            scrollState.scrollDpX(it.pointer.scroll.x * -20f)
                        } else {
                            scrollState.scrollDpY(it.pointer.scroll.y * -20f)
                        }
                    }
                }.onWheelX {
                    scrollState.scrollDpX(it.pointer.scroll.x * -20f)
                }

                val contextMenu = remember { ItemPopupMenu<Vec2f>("graph-context-menu") }

                modifier.onClick {
                    if (it.pointer.isLeftButtonClicked) {
                        updateMousePos(it.screenPosition)
                        val clickedConn = findConnectionAtPoint(lastMousePos.x, lastMousePos.y)
                        if (clickedConn != null) {
                            selectedConnection.set(clickedConn)
                            selectedNode.set(null)
                        } else {
                            selectedNode.set(null)
                            selectedConnection.set(null)
                        }
                    } else if(it.pointer.isRightButtonClicked) {
                        updateMousePos(it.screenPosition)

                        val clickedConn = findConnectionAtPoint(lastMousePos.x, lastMousePos.y)
                        val clickedNode = nodes.find { node ->
                            val x = node.xState.value * scale
                            val y = node.yState.value * scale
                            val w = node.widthState.value * scale
                            val h = node.heightState.value * scale
                            lastMousePos.x in x..(x + w) && lastMousePos.y in y..(y + h)
                        }
                        if (clickedConn == null && clickedNode == null) {
                            contextMenu.show(it.screenPosition, buildContextMenu(contextMenu), it.screenPosition)
                        }
                    }
                }

                modifier.onDrag {
                    if (it.pointer.isRightButtonDown || (it.pointer.isLeftButtonDown && dragNode == null)) {
                        scrollState.scrollDpX(-it.pointer.delta.x / UiScale.measuredScale)
                        scrollState.scrollDpY(-it.pointer.delta.y / UiScale.measuredScale)
                    }
                }

                modifier.onPointer {
                    updateMousePos(it.screenPosition)

                    val hovered = findConnectionAtPoint(lastMousePos.x, lastMousePos.y)
                    if (hoveredConnection.value != hovered) {
                        hoveredConnection.set(hovered)
                    }
                }

                ScrollPane(scrollState) {
                    scrollPaneNode = uiNode.findParentOfType<ScrollPaneNode>() ?: (uiNode as? ScrollPaneNode)

                    modifier.layout(CellLayout).onClick {
                        updateMousePos(it.screenPosition)
                        val clickedConn = findConnectionAtPoint(lastMousePos.x, lastMousePos.y)
                        if (clickedConn != null) {
                            selectedConnection.set(clickedConn)
                            selectedNode.set(null)
                        } else {
                            selectedNode.set(null)
                            selectedConnection.set(null)
                        }
                    }

                    renderConnections(connectionFont)

                    nodes.use().forEach { node ->
                        renderNode(node)
                    }
                }

                MiniMap()
                
                contextMenu()
            }

            PropertyPanel()
        }
    }

    private fun buildContextMenu(menu: ItemPopupMenu<Vec2f>): SubMenuItem<Vec2f> = SubMenuItem("Создать состояние") {
        item("Начальное состояние", icons.ADD) {
            val pos = menu.item
            val spn = scrollPaneNode
            if (spn != null && pos != null) {
                spn.toLocal(pos, tempVec)
                val x = tempVec.x / scale
                val y = tempVec.y / scale
                val entry = GraphNode(
                    title = "Entry",
                    x = x,
                    y = y,
                    color = Color("6BC872"),
                    type = NodeType.ENTRY,
                )
                nodes.add(entry)
                selectedNode.set(entry)
                menu.hide()
            }
        }
        
        item("Любое состояние", icons.ADD) {
            val pos = menu.item
            val spn = scrollPaneNode
            if (spn != null && pos != null) {
                spn.toLocal(pos, tempVec)
                val x = tempVec.x / scale
                val y = tempVec.y / scale
                val anyState = GraphNode(
                    title = "Any State",
                    x = x,
                    y = y,
                    color = Color("548AF7"),
                    type = NodeType.ANY,
                )
                nodes.add(anyState)
                selectedNode.set(anyState)
                menu.hide()
            }
        }
        
        val anims = availableAnimations
        if (anims.isNotEmpty()) {
            subMenu("Из анимации") {
                anims.forEach { animName ->
                    item(animName) {
                        val pos = menu.item
                        val spn = scrollPaneNode
                        if (spn != null && pos != null) {
                            spn.toLocal(pos, tempVec)
                            val x = tempVec.x / scale
                            val y = tempVec.y / scale
                            val state = GraphNode(
                                title = animName,
                                x = x,
                                y = y,
                                color = Color("5F6677"),
                                type = NodeType.STATE,
                                animationName = animName,
                            )
                            nodes.add(state)
                            selectedNode.set(state)
                            menu.hide()
                        }
                    }
                }
            }
        }
        
        item("Пустое состояние", icons.ADD) {
            val pos = menu.item
            val spn = scrollPaneNode
            if (spn != null && pos != null) {
                spn.toLocal(pos, tempVec)
                val x = tempVec.x / scale
                val y = tempVec.y / scale
                val state = GraphNode(
                    title = "New State",
                    x = x,
                    y = y,
                    color = Color("5F6677"),
                    type = NodeType.STATE,
                )
                nodes.add(state)
                selectedNode.set(state)
                menu.hide()
            }
        }
    }

    private fun updateMousePos(screenPosition: Vec2f) {
        val spn = scrollPaneNode
        if (spn != null) {
            spn.toLocal(screenPosition, tempVec)
            lastMousePos.set(tempVec.x, tempVec.y)
        } else {
            lastMousePos.set(screenPosition.x, screenPosition.y)
        }
    }

    private fun findConnectionAtPoint(px: Float, py: Float): GraphConnection? {
        val connList = connections
        var bestConn: GraphConnection? = null
        var bestDist = Float.MAX_VALUE

        for (conn in connList) {
            val from = nodes.find { it.id == conn.fromNodeId } ?: continue
            val to = nodes.find { it.id == conn.toNodeId } ?: continue

            val fromX = from.xState.value
            val fromY = from.yState.value
            val fromW = from.widthState.value
            val fromH = from.heightState.value

            val toX = to.xState.value
            val toY = to.yState.value
            val toW = to.widthState.value
            val toH = to.heightState.value

            val centerA = Vec2f((fromX + fromW / 2) * scale, (fromY + fromH / 2) * scale)
            val centerB = Vec2f((toX + toW / 2) * scale, (toY + toH / 2) * scale)

            val isBiDirectional = connList.any {
                it.fromNodeId == conn.toNodeId && it.toNodeId == conn.fromNodeId
            }

            var targetCenterA = centerA
            var targetCenterB = centerB

            if (isBiDirectional) {
                val dir = Vec2f(centerB.x - centerA.x, centerB.y - centerA.y)
                val len = dir.length()
                if (len > 0.001f) {
                    val perp = Vec2f(-dir.y / len, dir.x / len)
                    val offsetAmount = 15f * scale
                    val offset = Vec2f(perp.x * offsetAmount, perp.y * offsetAmount)

                    targetCenterA = Vec2f(centerA.x + offset.x, centerA.y + offset.y)
                    targetCenterB = Vec2f(centerB.x + offset.x, centerB.y + offset.y)
                }
            }

            val startPos = getEdgePoint(targetCenterA, fromW * scale, fromH * scale, targetCenterB)
            val endPos = getEdgePoint(targetCenterB, toW * scale, toH * scale, targetCenterA)

            val dist = pointToSegmentDistance(px, py, startPos.x, startPos.y, endPos.x, endPos.y)
            if (dist < connectionHitThreshold * scale && dist < bestDist) {
                bestDist = dist
                bestConn = conn
            }
        }

        return bestConn
    }

    private fun pointToSegmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy

        if (lenSq < 0.0001f) {
            val ex = px - ax
            val ey = py - ay
            return sqrt(ex * ex + ey * ey)
        }

        val t = ((px - ax) * dx + (py - ay) * dy) / lenSq
        val clampedT = t.coerceIn(0f, 1f)

        val projX = ax + clampedT * dx
        val projY = ay + clampedT * dy

        val ex = px - projX
        val ey = py - projY
        return sqrt(ex * ex + ey * ey)
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
                val currentSelectedConn = selectedConnection.use()
                val currentHoveredConn = hoveredConnection.use()
                val currentSelectedNode = selectedNode.use()

                connList.forEach { conn ->
                    val from = nodes.find { it.id == conn.fromNodeId }
                    val to = nodes.find { it.id == conn.toNodeId }

                    if (from != null && to != null) {
                        val fromX = from.xState.use()
                        val fromY = from.yState.use()
                        val fromW = from.widthState.use()
                        val fromH = from.heightState.use()

                        val toX = to.xState.use()
                        val toY = to.yState.use()
                        val toW = to.widthState.use()
                        val toH = to.heightState.use()

                        val centerA = Vec2f((fromX + fromW / 2) * scale, (fromY + fromH / 2) * scale)
                        val centerB = Vec2f((toX + toW / 2) * scale, (toY + toH / 2) * scale)

                        val isBiDirectional = connList.any {
                            it.fromNodeId == conn.toNodeId && it.toNodeId == conn.fromNodeId
                        }

                        var targetCenterA = centerA
                        var targetCenterB = centerB

                        if (isBiDirectional) {
                            val dir = Vec2f(centerB.x - centerA.x, centerB.y - centerA.y)
                            val len = dir.length()
                            if (len > 0.001f) {
                                val perp = Vec2f(-dir.y / len, dir.x / len)
                                val offsetAmount = 15f * scale
                                val offset = Vec2f(perp.x * offsetAmount, perp.y * offsetAmount)

                                targetCenterA = Vec2f(centerA.x + offset.x, centerA.y + offset.y)
                                targetCenterB = Vec2f(centerB.x + offset.x, centerB.y + offset.y)
                            }
                        }

                        val startPos = getEdgePoint(targetCenterA, fromW * scale, fromH * scale, targetCenterB)
                        val endPos = getEdgePoint(targetCenterB, toW * scale, toH * scale, targetCenterA)

                        // Determine connection color based on selection/hover state
                        val isSelected = currentSelectedConn?.id == conn.id
                        val isHovered = currentHoveredConn?.id == conn.id && !isSelected
                        val isFromNodeSelected = currentSelectedNode == from

                        val baseColor = when {
                            conn.properties.mute -> ColorTheme.GraphColors.ConnectionMuted
                            isSelected -> ColorTheme.GraphColors.ConnectionSelected
                            isHovered -> ColorTheme.GraphColors.ConnectionHovered
                            isFromNodeSelected -> Color.WHITE
                            else -> conn.color
                        }

                        val lineWidth = when {
                            isSelected -> 3f
                            isHovered -> 2.5f
                            else -> 2f
                        }

                        val color by animateColorAsState(baseColor)

                        with(uiNode) {
                            drawDashedArrow(startPos, endPos, color, scale, conn.label, textProps, lineWidth)
                        }
                    }
                }
            })
        }
    }

    private fun getEdgePoint(center: Vec2f, w: Float, h: Float, target: Vec2f): Vec2f {
        val dirX = target.x - center.x
        val dirY = target.y - center.y

        if (abs(dirX) < 0.1f && abs(dirY) < 0.1f) return center

        val halfW = w / 2f
        val halfH = h / 2f

        val nx = dirX / halfW
        val ny = dirY / halfH

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
                    selectedConnection.set(null)
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

                    node.xState.set((mouseLogicX - dragOffset.x).coerceAtLeast(0f))
                    node.yState.set((mouseLogicY - dragOffset.y).coerceAtLeast(0f))
                }
            }.onDragEnd {
                dragNode = null
            }.onClick {
                selectedNode.set(node)
                selectedConnection.set(null)
            }

            val nodeColor by animateColorAsState(
                if (isSelected || isHovered) {
                    node.color.mix(Color.WHITE, 0.3f)
                } else node.color
            )
            val borderColor by animateColorAsState(if (isSelected) Color.WHITE else if (isHovered) ColorTheme.UI.WhiteReplacement else node.color)

            modifier
                .background(
                    RoundRectGradientBackground(
                        Dimensions.PaddingMedium.scaled(),
                        nodeColor.mix(ColorTheme.UI.BackgroundSecondary, 0.3f), ColorTheme.UI.BackgroundSecondary,
                        0.dp, 0.dp,
                        Dp(150f * scale), Dp(75f * scale)
                    )
                )
                .border(
                    RoundRectBorder(
                        borderColor,
                        Dimensions.PaddingMedium.scaled(),
                        Dimensions.PaddingSmall.scaled()
                    )
                )
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
        val node = selectedNode.use()
        val conn = selectedConnection.use()

        Column(width = Grow.Std, scopeName = when {
            node != null -> "Node-Editor"
            conn != null -> "Connection-Editor"
            else -> "No-Selection"
        }) {
            modifier.width(Dp(350f)).height(Grow.Std).background(RectBackground(ColorTheme.UI.BackgroundSecondary))
                .padding(Dimensions.PaddingLarge).zLayer(1000)

            Text("ПАРАМЕТРЫ") {
                modifier.textColor(ColorTheme.UI.WhiteReplacement)
                    .font(FontProps(size = 16f, isBold = true))
                    .margin(bottom = Dimensions.PaddingLarge)
            }

            when {
                node != null -> {
                    Text("СОСТОЯНИЕ") {
                        modifier.textColor(ColorTheme.UI.WhiteReplacement)
                            .font(FontProps(size = 14f, isBold = true))
                            .margin(bottom = Dimensions.PaddingSmall)
                    }

                    PropertyTextField("Название", node.title) { node.title = it }
                    PropertyReadOnlyField("Координаты", "${node.xState.use().toInt()}, ${node.yState.use().toInt()}")

                    Divider()

                    Text("ОСНОВНОЕ") {
                        modifier.textColor(ColorTheme.UI.WhiteReplacement)
                            .font(FontProps(size = 14f))
                            .margin(top = Dimensions.PaddingMedium, bottom = Dimensions.PaddingSmall)
                    }

                    // Node type: Entry / Any / State
                    val typeIndex = when (node.type) {
                        NodeType.ENTRY -> 0
                        NodeType.ANY -> 1
                        NodeType.STATE -> 2
                    }
                    val typeState = remember(typeIndex)
                    val typePreview = when (typeState.use()) {
                        0 -> "Начало"
                        1 -> "Любое состояние"
                        else -> "Состояние"
                    }
                    PropertyComboBox(
                        "Тип",
                        typePreview,
                        listOf(
                            Composable { Text("Начало") { modifier.textColor(ColorTheme.UI.WhiteReplacement) } },
                            Composable { Text("Любое состояние") { modifier.textColor(ColorTheme.UI.WhiteReplacement) } },
                            Composable { Text("Состояние") { modifier.textColor(ColorTheme.UI.WhiteReplacement) } },
                        ),
                        typeState,
                    )
                    when (typeState.use()) {
                        0 -> node.type = NodeType.ENTRY
                        1 -> node.type = NodeType.ANY
                        else -> node.type = NodeType.STATE
                    }

                    // Animation selection
                    val anims = availableAnimations.use()
                    if (anims.isNotEmpty()) {
                        val currentIndex = anims.indexOfFirst { it == node.animationName }.coerceAtLeast(-1)
                        val indexState = remember(currentIndex)
                        val animPreview = if (currentIndex >= 0) anims[currentIndex] else "Не выбрано"

                        PropertyComboBox(
                            "Анимация",
                            animPreview,
                            anims.map { name ->
                                Composable {
                                    Text(name) { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
                                }
                            },
                            indexState,
                        )

                        val idx = indexState.use()
                        if (idx in anims.indices) {
                            node.animationName = anims[idx]
                        }
                    }

                    Divider()

                    Text("ВОСПРОИЗВЕДЕНИЕ") {
                        modifier.textColor(ColorTheme.UI.WhiteReplacement)
                            .font(FontProps(size = 14f))
                            .margin(top = Dimensions.PaddingMedium, bottom = Dimensions.PaddingSmall)
                    }

                    // Wrap mode
                    val wrapItems = WrapMode.entries.toList()
                    val wrapIndex = wrapItems.indexOf(node.wrapMode).coerceAtLeast(0)
                    val wrapState = remember(wrapIndex)
                    PropertyComboBox(
                        "Режим",
                        node.wrapMode.name,
                        wrapItems.map { mode ->
                            Composable {
                                Text(mode.name) { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
                            }
                        },
                        wrapState,
                    )
                    val wi = wrapState.use()
                    if (wi in wrapItems.indices) node.wrapMode = wrapItems[wi]

                    PropertyFloatField("Скорость", node.speed) { node.speed = it }
                    PropertyFloatField("Вес", node.weight) { node.weight = it }
                    PropertyIntField("Приоритет", node.priority) { node.priority = it }

                    Divider()

                    Text("СМЕШИВАНИЕ") {
                        modifier.textColor(ColorTheme.UI.WhiteReplacement)
                            .font(FontProps(size = 14f))
                            .margin(top = Dimensions.PaddingMedium, bottom = Dimensions.PaddingSmall)
                    }

                    PropertyFloatField("Появление", node.fadeIn) { node.fadeIn = it }
                    PropertyFloatField("Затухание", node.fadeOut) { node.fadeOut = it }

                    // Blend curve
                    val curves = Interpolation.entries.toList()
                    val curveIndex = curves.indexOf(node.blendCurve).coerceAtLeast(0)
                    val curveState = remember(curveIndex)

                    PropertyComboBox(
                        "Кривая",
                        node.blendCurve.name,
                        curves.map { c ->
                            Composable {
                                Text(c.name.lowercase()) { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
                            }
                        },
                        curveState,
                    )
                    val ci = curveState.use()
                    if (ci in curves.indices) node.blendCurve = curves[ci]

                    Divider()

                    Text("ПЕРЕЗАПИСАТЬ") {
                        modifier.textColor(ColorTheme.UI.WhiteReplacement)
                            .font(FontProps(size = 14f))
                            .margin(top = Dimensions.PaddingMedium, bottom = Dimensions.PaddingSmall)
                    }

                    ToggleRow("Смещение", node.overrideTranslation) { node.overrideTranslation = it }
                    ToggleRow("Поворот", node.overrideRotation) { node.overrideRotation = it }
                    ToggleRow("Масштаб", node.overrideScale) { node.overrideScale = it }
                }
                conn != null -> {
                    val fromNode = nodes.find { it.id == conn.fromNodeId }
                    val toNode = nodes.find { it.id == conn.toNodeId }
                    val fromName = fromNode?.title ?: "?"
                    val toName = toNode?.title ?: "?"

                    Text("СВЯЗЬ") {
                        modifier.textColor(Color.WHITE)
                            .font(FontProps(size = 14f, isBold = true))
                            .margin(bottom = Dimensions.PaddingSmall)
                    }
                    PropertyReadOnlyField("Переход", "$fromName → $toName")

                    PropertyTextField("Название", conn.label) { new ->
                        val idx = connections.indexOfFirst { it.id == conn.id }
                        if (idx != -1) {
                            connections[idx] = connections[idx].copy(label = new)
                            selectedConnection.set(connections[idx])
                        }
                    }

                    Divider()

                    Text("ПЕРЕХОД") {
                        modifier.textColor(ColorTheme.UI.WhiteReplacement)
                            .font(FontProps(size = 14f))
                            .margin(top = Dimensions.PaddingMedium, bottom = Dimensions.PaddingSmall)
                    }

                    var props = conn.properties

                    PropertyFloatField("Вес", props.weight) {
                        props = props.copy(weight = it)
                        updateConnectionProperties(conn.id, props)
                    }
                    PropertyFloatField("Время", props.duration) {
                        props = props.copy(duration = it)
                        updateConnectionProperties(conn.id, props)
                    }
                    PropertyFloatField("Появление", props.fadeIn) {
                        props = props.copy(fadeIn = it)
                        updateConnectionProperties(conn.id, props)
                    }
                    PropertyFloatField("Затухание", props.fadeOut) {
                        props = props.copy(fadeOut = it)
                        updateConnectionProperties(conn.id, props)
                    }

                    PropertyFloatField("Время завер.", props.exitTime ?: 0f) {
                        val value = it
                        props = props.copy(exitTime = value)
                        updateConnectionProperties(conn.id, props)
                    }

                    Divider()

                    Text("УСЛОВИЕ (Kotlin)") {
                        modifier.textColor(ColorTheme.UI.WhiteReplacement)
                            .font(FontProps(size = 14f))
                            .margin(top = Dimensions.PaddingMedium, bottom = Dimensions.PaddingSmall)
                    }
                    val condProvider = rememberTarget(conn.id) {
                        CompiledFileProvider(
                            name = "transition_${conn.id}.kts",
                            analyzer = ScriptingEnvironment.INSTANCE.analyzer,
                            initialText = props.condition,
                        ).also { provider ->
                            provider.onTextChanged = { raw ->
                                // single-line: strip newlines just in case (paste etc.)
                                val sanitized = raw.replace("\r", " ").replace("\n", " ")
                                if (sanitized != raw) provider.setText(sanitized)
                                val next = props.copy(condition = sanitized)
                                props = next
                                updateConnectionProperties(conn.id, next)
                            }
                        }
                    }
                    // keep provider in sync when switching between connections
                    if (condProvider.currentText != props.condition) {
                        condProvider.setText(props.condition)
                    }

                    Row {
                        modifier.margin(top = Dimensions.PaddingSmall).width(Grow.Std)
                        Text("Условие:") {
                            modifier.textColor(Color.GRAY).width(Dp(100f)).font(FontProps(size = 12f))
                                .alignY(AlignmentY.Center)
                                .margin(top = Dimensions.PaddingSmall)
                        }

                        ScriptTextArea(
                            lineProvider = condProvider,
                            width = Grow.Std,
                            height = Dp(34f),
                            withVerticalScrollbar = false,
                            withHorizontalScrollbar = true,
                        ) {
                            // minimal embedded editor config
                            modifier.editorConfig = TextEditorConfig(
                                showLineNumbers = false,
                                showBackground = false,
                                showVerticalScrollbar = false,
                                showHorizontalScrollbar = false,
                                showSelectionAndCaret = true,
                                singleLine = true,
                                enableKeyMap = true,
                                enableAutoBrackets = true,
                            )
                            modifier.editorHandler = (condProvider)

                            // take completions/diagnostics from provider
                            modifier.completions.clear()
                            modifier.completions.addAll(condProvider.analysisState.completions)
                            modifier.errors.clear()
                            modifier.errors.addAll(condProvider.analysisState.diagnostics)

                            installDefaultSelectionHandler()
                        }
                    }

                    Divider()

                    ToggleRow("Отключено", props.mute) {
                        props = props.copy(mute = it)
                        updateConnectionProperties(conn.id, props)
                    }
                }
                else -> {
                    Text("КОНТРОЛЛЕР") {
                        modifier.textColor(Color.WHITE)
                            .font(FontProps(size = 14f, isBold = true))
                            .margin(bottom = Dimensions.PaddingSmall)
                    }

                    PropertyTextField("Модель", modelPath.value) { newPath ->
                        modelPath.set(newPath)
                        onModelPathChanged?.invoke(newPath)
                    }

                    val anims = availableAnimations.use()
                    if (anims.isNotEmpty()) {
                        PropertyReadOnlyField("Анимаций доступно", anims.size.toString())
                    } else {
                        PropertyReadOnlyField("Анимаций доступно", "0")
                    }
                }
            }
        }
    }

    private fun UiScope.PropertyComboBox(
        label: String,
        preview: String,
        items: List<Composable>,
        itemIndex: MutableStateValue<Int>,
    ) {
        Row(Grow.Std) {
            modifier.margin(Dimensions.PaddingNormal).width(Grow.Std)
            Text("$label:") {
                modifier.textColor(Color.GRAY).width(Grow.Std).font(FontProps(size = 12f))
                    .alignY(AlignmentY.Center)
            }
            Box {
                modifier.align(AlignmentX.End, AlignmentY.Center)

                ComboBox(preview, items, itemIndex)
            }
        }
    }

    private fun UiScope.PropertyTextField(label: String, value: String, onChange: (String) -> Unit) {
        Row {
            modifier.margin(Dimensions.PaddingNormal).width(Grow.Std)
            Text("$label:") {
                modifier.textColor(Color.GRAY).width(Dp(100f)).font(FontProps(size = 12f))
                    .alignY(AlignmentY.Center)

            }
            val textState = remember(value)
            TextField(textState.use()) {
                modifier.width(Grow.Std)
                    .colors(lineColor = ColorTheme.UI.BackgroundAccent, lineColorFocused = ColorTheme.UI.WhiteReplacement)

                modifier.onChange { new ->
                    textState.set(new)
                    onChange(new)
                }.alignY(AlignmentY.Center)
            }
        }
    }

    private fun UiScope.PropertyReadOnlyField(label: String, value: String) {
        Row {
            modifier.margin(Dimensions.PaddingNormal).width(Grow.Std)
            Text("$label:") {
                modifier.textColor(Color.GRAY).width(Dp(100f)).font(FontProps(size = 12f))
                    .alignY(AlignmentY.Center)
            }
            Text(value) {
                modifier.textColor(ColorTheme.UI.WhiteReplacement)
                    .font(FontProps(size = 12f))
                    .alignY(AlignmentY.Center)
            }
        }
    }

    private fun UiScope.PropertyFloatField(label: String, value: Float, onChange: (Float) -> Unit) {
        Row {
            modifier.margin(Dimensions.PaddingNormal).width(Grow.Std)
            Text("$label:") {
                modifier.textColor(Color.GRAY).width(Dp(100f)).font(FontProps(size = 12f))
                    .alignY(AlignmentY.Center)
            }
            val textState = remember(value.toString())
            TextField(textState.use()) {
                modifier.width(Grow.Std)
                    .colors(lineColor = ColorTheme.UI.BackgroundAccent, lineColorFocused = ColorTheme.UI.WhiteReplacement)
                modifier.onChange { new ->
                    textState.set(new)
                    new.toFloatOrNull()?.let(onChange)
                }.alignY(AlignmentY.Center)
            }
        }
    }

    private fun UiScope.PropertyIntField(label: String, value: Int, onChange: (Int) -> Unit) {
        Row {
            modifier.margin(Dimensions.PaddingNormal).width(Grow.Std)
            Text("$label:") {
                modifier.textColor(Color.GRAY).width(Dp(100f)).font(FontProps(size = 12f))
                    .alignY(AlignmentY.Center)
            }
            val textState = remember(value.toString())
            TextField(textState.use()) {
                modifier.width(Grow.Std)
                    .colors(lineColor = ColorTheme.UI.BackgroundAccent, lineColorFocused = ColorTheme.UI.WhiteReplacement)

                modifier.onChange { new ->
                    textState.set(new)
                    new.toIntOrNull()?.let(onChange)
                }.alignY(AlignmentY.Center)
            }
        }
    }

    private fun UiScope.ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
        Row {
            modifier.margin(Dimensions.PaddingNormal).width(Grow.Std)
            Text("$label:") {
                modifier.textColor(Color.GRAY).width(Dp(100f)).font(FontProps(size = 12f))
                    .alignY(AlignmentY.Center)
            }
            val state = remember(value)
            Checkbox(state.use()) {
                modifier.onToggle {
                    state.set(it)
                    onChange(it)
                }.alignY(AlignmentY.Center)
                    .colors(borderColor = ColorTheme.UI.BackgroundAccent, backgroundColor = ColorTheme.UI.BackgroundSecondary)
            }
        }
    }

    private fun UiScope.Divider() {
        Box {
            modifier.width(Grow.Std).height(Dimensions.PaddingSmall)
                .margin(vertical = Dimensions.PaddingMedium)
                .background(RectBackground(ColorTheme.UI.WhiteReplacement))
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

            Text("Mini-map") {
                modifier.align(AlignmentX.Center, AlignmentY.Center).margin(bottom = Dp(6f))
                    .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.5f))
            }

            Box(Grow.Std, Grow.Std) {
                modifier.margin(Dimensions.PaddingMedium)
                    .margin(top = Dimensions.PaddingSmall)

                val currentNodes = nodes.use()
                val currentScrollX = scrollState.xScrollDp.use()
                val currentScrollY = scrollState.yScrollDp.use()
                val currentScale = scale
                val currentViewW = viewportWidth.use()
                val currentViewH = viewportHeight.use()

                modifier.backgrounds(
                    RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dp(4f)),
                    UiRenderer { node ->
                        val draw = node.getUiPrimitives()

                        if (currentNodes.isEmpty()) return@UiRenderer

                        val minX = currentNodes.minOf { it.xState.value }
                        val minY = currentNodes.minOf { it.yState.value }
                        val maxX = currentNodes.maxOf { it.xState.value + it.widthState.value }
                        val maxY = currentNodes.maxOf { it.yState.value + it.heightState.value }

                        val padding = 20f
                        val worldL = minX - padding
                        val worldT = minY - padding
                        val worldR = maxX + padding
                        val worldB = maxY + padding

                        val worldW = worldR - worldL
                        val worldH = worldB - worldT

                        val mapW = node.widthPx
                        val mapH = node.heightPx

                        if (worldW <= 0.1f || worldH <= 0.1f) return@UiRenderer

                        val scaleX = mapW / worldW
                        val scaleY = mapH / worldH
                        val mapScale = min(scaleX, scaleY)

                        val drawOffsetX = (mapW - worldW * mapScale) / 2f
                        val drawOffsetY = (mapH - worldH * mapScale) / 2f

                        fun toMapX(x: Float) = node.leftPx + drawOffsetX + (x - worldL) * mapScale
                        fun toMapY(y: Float) = node.topPx + drawOffsetY + (y - worldT) * mapScale

                        currentNodes.forEach { n ->
                            val mx = toMapX(n.xState.value)
                            val my = toMapY(n.yState.value)
                            val mw = n.widthState.value * mapScale
                            val mh = n.heightState.value * mapScale

                            draw.roundRect(
                                mx, my, mw, mh, 2f,
                                node.clipBoundsPx, n.color
                            )
                        }

                        val density = UiScale.measuredScale

                        val camX = (currentScrollX * density) / currentScale
                        val camY = (currentScrollY * density) / currentScale

                        val camW = currentViewW / currentScale
                        val camH = currentViewH / currentScale

                        val viewX = toMapX(camX)
                        val viewY = toMapY(camY)
                        val viewW = camW * mapScale
                        val viewH = camH * mapScale
                        if (mapW <= viewW || mapH <= viewH) return@UiRenderer

                        draw.rect(
                            viewX, viewY, viewW, viewH,
                            node.clipBoundsPx, Color.WHITE.withAlpha(0.3f)
                        )
                    })
            }
        }
    }

    fun selectConnection(connectionId: String): Boolean {
        val conn = connections.find { it.id == connectionId }
        if (conn != null) {
            selectedConnection.set(conn)
            selectedNode.set(null)
            return true
        }
        return false
    }

    fun clearConnectionSelection() {
        selectedConnection.set(null)
    }

    fun updateConnectionProperties(connectionId: String, newProperties: ConnectionProperties): GraphConnection? {
        val index = connections.indexOfFirst { it.id == connectionId }
        if (index == -1) return null

        val old = connections[index]
        val updated = old.copy(properties = newProperties)
        connections[index] = updated

        // Update selection reference if this was the selected connection
        if (selectedConnection.value?.id == connectionId) {
            selectedConnection.set(updated)
        }

        return updated
    }

    fun getOutgoingConnections(nodeId: String): List<GraphConnection> {
        return connections.filter { it.fromNodeId == nodeId }
    }

    fun getIncomingConnections(nodeId: String): List<GraphConnection> {
        return connections.filter { it.toNodeId == nodeId }
    }

    fun toggleConnectionMute(connectionId: String): GraphConnection? {
        val conn = connections.find { it.id == connectionId } ?: return null
        return updateConnectionProperties(connectionId, conn.properties.copy(mute = !conn.properties.mute))
    }

    private fun Dp.scaled() = Dp(this.value * scale)

    private fun FontProps(size: Float = 14f, isBold: Boolean = false) = MsdfFont(
        ColorTheme.Fonts.MONOCRAFT, size * scale, weight = if (isBold) MsdfFont.WEIGHT_BOLD else MsdfFont.WEIGHT_REGULAR
    )

    fun toGraph(): AnimationControllerGraph {
        val nodeData = nodes.map { node ->
            GraphNodeData(
                id = node.id,
                title = node.title,
                x = node.xState.value,
                y = node.yState.value,
                type = node.type,
                animationName = node.animationName,
                wrapMode = node.wrapMode,
                speed = node.speed,
                weight = node.weight,
                priority = node.priority,
                fadeIn = node.fadeIn,
                fadeOut = node.fadeOut,
                blendCurve = node.blendCurve,
                overrideTranslation = node.overrideTranslation,
                overrideRotation = node.overrideRotation,
                overrideScale = node.overrideScale,
                extras = node.extras.toMap(),
            )
        }
        val connectionData = connections.map { conn ->
            GraphConnectionData(
                id = conn.id,
                fromNodeId = conn.fromNodeId,
                toNodeId = conn.toNodeId,
                label = conn.label,
                properties = conn.properties
            )
        }
        return AnimationControllerGraph(modelPath.value, nodeData, connectionData)
    }

    fun loadGraph(graph: AnimationControllerGraph) {
        nodes.clear()
        connections.clear()

        modelPath.set(graph.modelPath)

        graph.nodes.forEach { data ->
            val color = when (data.title) {
                "Entry" -> Color("6BC872")
                "Any State" -> Color("548AF7")
                else -> Color("5F6677")
            }
            nodes.add(
                GraphNode(
                    id = data.id,
                    title = data.title,
                    x = data.x,
                    y = data.y,
                    color = color,
                    type = data.type,
                    animationName = data.animationName,
                    wrapMode = data.wrapMode,
                    speed = data.speed,
                    weight = data.weight,
                    priority = data.priority,
                    fadeIn = data.fadeIn,
                    fadeOut = data.fadeOut,
                    blendCurve = data.blendCurve,
                    overrideTranslation = data.overrideTranslation,
                    overrideRotation = data.overrideRotation,
                    overrideScale = data.overrideScale,
                    extras = data.extras.toMutableMap(),
                )
            )
        }

        graph.connections.forEach { data ->
            connections.add(
                GraphConnection(
                    fromNodeId = data.fromNodeId,
                    toNodeId = data.toNodeId,
                    label = data.label,
                    id = data.id,
                    properties = data.properties,
                )
            )
        }
    }
}

context(node: UiNode) fun drawDashedArrow(
    from: Vec2f,
    to: Vec2f,
    color: Color,
    scale: Float,
    label: String,
    textProps: TextProps,
    lineWidth: Float = 2f,
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
    val endLimit = length - arrowSize

    while (currentPos < endLimit) {
        val p1 = Vec2f(from.x + normDir.x * currentPos, from.y + normDir.y * currentPos)
        val endSegment = (currentPos + dashLen).coerceAtMost(endLimit)
        val p2 = Vec2f(from.x + normDir.x * endSegment, from.y + normDir.y * endSegment)

        draw.configure(color) {
            line(p1.x, p1.y, p2.x, p2.y, lineWidth * scale)
        }
        currentPos += dashLen + gapLen
    }

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
                mid.x - font.textDimensions(label).width / 2f * scale,
                mid.y + font.textDimensions(label).height / 2f * scale,
                0f
            )
        }

        text.configure(ColorTheme.UI.WhiteReplacement) {
            text(textProps)
        }
    }
}
