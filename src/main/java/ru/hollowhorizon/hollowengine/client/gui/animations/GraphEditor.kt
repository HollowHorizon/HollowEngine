package ru.hollowhorizon.hollowengine.client.gui.animations

import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.geometry.MeshBuilder
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.configure
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs.GridBackground

class GraphEditor {
    // Используем структуру контроллера похожую на твою, но упрощенную для примера
    val scrollState = ScrollState()
    val scaleState = mutableStateOf(1.0f)
    var scale: Float = 1f

    // Данные графа
    val nodes = mutableStateListOf<GraphNode>()
    val connections = mutableStateListOf<GraphConnection>()

    // Выделение
    val selectedNode = mutableStateOf<GraphNode?>(null)

    // Dragging state
    private var dragNode: GraphNode? = null
    private val dragStartPos = MutableVec2f(0f, 0f)
    private val nodeStartPos = MutableVec2f(0f, 0f)

    init {
        // Инициализация тестовых данных как на скрине
        nodes.add(GraphNode(title = "Entry", x = 100f, y = 100f, color = Color("6BC872"))) // Green
        nodes.add(GraphNode(title = "Idle", x = 100f, y = 250f, color = Color("EB903F")))  // Orange
        nodes.add(GraphNode(title = "Run", x = 100f, y = 450f, color = Color("5F6677")))   // Grey
        nodes.add(GraphNode(title = "Frontflip", x = 400f, y = 100f, color = Color("5F6677")))
        nodes.add(GraphNode(title = "Any State", x = 600f, y = 150f, color = Color("548AF7"))) // Blue

        connections.add(GraphConnection(nodes[0].id, nodes[1].id, "auto"))
        connections.add(GraphConnection(nodes[1].id, nodes[2].id, "speed > 0"))
        connections.add(GraphConnection(nodes[1].id, nodes[3].id, "jump"))
    }

    context(scope: UiScope)
    fun EditorLayout() = with(scope) {
        // Обновление зума (как в твоем коде)
        scale = scaleState.use()

        Row(Grow.Std, Grow.Std) {
            // --- MAIN CANVAS ---
            Box(Grow.Std, Grow.Std) {
                // Фон-сетка (темная, как на скрине)
                val bgColor = Color("1E1F22")
                val gridColor = Color("2A2E35")
                modifier
                    .background(UiRenderer { node ->
                        node.apply {
                            getUiPrimitives().localRect(0f, 0f, widthPx, heightPx, bgColor)
                        }
                    })
                    .background(
                        GridBackground(
                            sectionSize = Dp(40f), // Крупная сетка
                            currentZoom = scale,
                            scrollX = scrollState.xScrollDp.value * UiScale.measuredScale,
                            scrollY = scrollState.yScrollDp.value * UiScale.measuredScale,
                            lineWidth = Dp(1f),
                            lineColor = gridColor
                        )
                    )

                // Обработка зума и пана
                modifier
                    .onWheelY {
                        if (KeyboardInput.isCtrlDown) {
                            val newScale = (scaleState.value * (if (it.pointer.scroll.y > 0) 1.1f else 0.9f))
                                .coerceIn(0.2f, 2.0f)
                            scaleState.set(newScale)
                        } else {
                            scrollState.scrollDpX(it.pointer.scroll.x * -20f)
                            scrollState.scrollDpY(it.pointer.scroll.y * -20f)
                        }
                    }
                    .onDragStart {
                        if (it.pointer.isLeftButtonDown) {
                            selectedNode.set(null) // Клик в пустоту снимает выделение
                        }
                    }
                    .onDrag {
                        if (it.pointer.isRightButtonDown || (it.pointer.isLeftButtonDown && dragNode == null)) {
                            scrollState.scrollDpX(-it.pointer.delta.x / UiScale.measuredScale)
                            scrollState.scrollDpY(-it.pointer.delta.y / UiScale.measuredScale)
                        }
                    }

                // ScrollPane для контента
                ScrollPane(scrollState) {
                    modifier.layout(CellLayout).onClick { selectedNode.set(null) }

                    // 1. Рендеринг связей (СЛОЙ НИЖЕ УЗЛОВ)
                    renderConnections()

                    // 2. Рендеринг узлов
                    nodes.use().forEach { node ->
                        renderNode(node)
                    }
                }

                // --- OVERLAYS ---

                // Мини-карта
                MiniMap()
            }

            // --- RIGHT PROPERTY PANEL ---
            PropertyPanel()
        }
    }

    private fun UiScope.renderConnections() {
        // Рисуем линии. В Kool UI2 для рисования линий поверх ScrollPane удобно использовать Canvas внутри Box
        // Но здесь мы используем UiRenderer внутри ScrollPane, чтобы линии двигались вместе с контентом
        Box(Grow.Std, Grow.Std) {
            modifier.layout(CellLayout).zLayer(-10) // Уводим назад

            modifier.background(UiRenderer { uiNode ->
                val draw = uiNode.getPlainBuilder()
                connections.forEach { conn ->
                    val from = nodes.find { it.id == conn.fromNodeId }
                    val to = nodes.find { it.id == conn.toNodeId }

                    if (from != null && to != null) {
                        val start = Vec2f(from.x + from.width / 2, from.y + 30f) * scale
                        val end = Vec2f(to.x + to.width / 2, to.y + 30f) * scale

                        // Цвет линии
                        val color = if (selectedNode.value == from) Color.WHITE else conn.color

                        // Рисуем пунктирную линию
                        with(uiNode) {
                            drawDashedArrow(draw, start, end, color, scale, conn.label)
                        }
                    }
                }
            })
        }
    }

    private fun UiScope.renderNode(node: GraphNode) {
        val isSelected = selectedNode.use() == node

        Column {
            modifier
                .width(Dp(node.width * scale))
                .height(FitContent)
                .margin(start = Dp(node.x * scale), top = Dp(node.y * scale))
                .zLayer(if (isSelected) 100 else 10) // Выделенный поверх

            // Drag Logic
            modifier
                .onDragStart {
                    if (it.pointer.isLeftButtonDown) {
                        dragNode = node
                        dragStartPos.set(it.screenPosition)
                        nodeStartPos.set(node.x, node.y)
                        selectedNode.set(node)
                    }
                }
                .onDrag {
                    if (dragNode == node) {
                        val dx = (it.screenPosition.x - dragStartPos.x) / scale
                        val dy = (it.screenPosition.y - dragStartPos.y) / scale
                        node.x = nodeStartPos.x + dx
                        node.y = nodeStartPos.y + dy
                    }
                }
                .onDragEnd { dragNode = null }
                .onClick { selectedNode.set(node) }

            // --- HEADER ---
            Box(Grow.Std, FitContent) {
                // Скругление только сверху
                modifier.background(RoundRectBackground(node.color, Dimensions.PaddingMedium))

                Text(node.title) {
                    modifier
                        .margin(Dimensions.PaddingMedium.scaled())
                        .font(FontProps(isBold = true))
                        .textColor(Color.WHITE)
                        .alignY(AlignmentY.Center)
                }

                // Индикаторы времени или состояния (как на скрине "0.0s Base")
                Row {
                    modifier.align(AlignmentX.End, AlignmentY.Center).margin(end = Dimensions.PaddingSmall.scaled())

                    Badge("0.0s")
                    Badge("Base")
                }
            }

            // --- BODY ---
            Column(Grow.Std, FitContent) {
                modifier.background(
                    RoundRectBackground(
                        ColorTheme.UI.BackgroundElements,
                        Dimensions.PaddingMedium
                    )
                )
                // Хак, чтобы закрыть скругление хедера снизу (прямоугольник поверх стыка)
                // В реальном проекте лучше написать кастомный шейдер фона

                // Входы/Выходы портов (визуально)
                if (isSelected) {
                    Box(Grow.Std, Dp(2f * scale)) { modifier.backgroundColor(ColorTheme.Accents.Main) }
                }
            }
        }
    }

    private fun UiScope.Badge(text: String) {
        Box {
            modifier
                .margin(horizontal = Dp(2f))
                .padding(horizontal = Dp(4f), vertical = Dp(2f))
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.3f), Dp(4f)))

            Text(text) {
                modifier.textColor(Color.WHITE.withAlpha(0.8f)).font(FontProps(size = 10f)).textColor(ColorTheme.UI.WhiteReplacement)
            }
        }
    }

    // --- RIGHT SIDEBAR ---
    private fun UiScope.PropertyPanel() {
        Column {
            modifier
                .width(Dp(300f))
                .height(Grow.Std)
                .background(RectBackground(ColorTheme.UI.BackgroundSecondary))
                .padding(Dimensions.PaddingLarge)
                .zLayer(1000) // Поверх всего

            Text("СВОЙСТВА") {
                modifier.font(FontProps(size = 18f, isBold = true)).textColor(ColorTheme.UI.WhiteReplacement).margin(bottom = Dimensions.PaddingLarge)
            }

            val selection = selectedNode.use()
            if (selection != null) {
                PropertyField("Состояние", selection.title) { selection.title = it }
                PropertyField("Скорость", "1.0")

                Row(Grow.Std) {
                    modifier.margin(vertical = Dimensions.PaddingMedium)
                    Text("Зацикливание") { modifier.width(Grow.Std).alignY(AlignmentY.Center) }
                    Checkbox(true) {}
                }

                Text("Переходы") { modifier.margin(top = Dimensions.PaddingLarge, bottom = Dimensions.PaddingMedium) }

                // Список переходов
                Column(Grow.Std) {
                    connections.filter { it.fromNodeId == selection.id }.forEach { conn ->
                        val targetName = nodes.find { it.id == conn.toNodeId }?.title ?: "Unknown"
                        Row(Grow.Std) {
                            modifier
                                .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dp(4f)))
                                .padding(Dimensions.PaddingMedium)
                                .margin(bottom = Dp(4f))

                            Text("→ $targetName") { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
                            Box(Grow.Std) {}
                            Text(if (conn.label.isNotEmpty()) conn.label else "0.0s") {
                                modifier.textColor(Color.GRAY).font(FontProps(size = 10f)).textColor(ColorTheme.UI.WhiteReplacement)
                            }
                        }
                    }
                }
            } else {
                Text("Нет выделения") { modifier.textColor(Color.GRAY) }
            }
        }
    }

    private fun UiScope.PropertyField(label: String, value: String, onChange: ((String) -> Unit)? = null) {
        Column(Grow.Std) {
            modifier.margin(bottom = Dimensions.PaddingMedium)
            Text(label) { modifier.textColor(Color.GRAY).font(FontProps(size = 12f))
                .textColor(ColorTheme.UI.WhiteReplacement)
                .margin(bottom = Dp(4f)) }
            TextField(value) {
                modifier
                    .width(Grow.Std)
                    .colors(lineColor = Color.WHITE.withAlpha(0f))
                    .onChange { onChange?.invoke(it) }
            }
        }
    }

    // --- MINIMAP ---
    private fun UiScope.MiniMap() {
        Box {
            modifier
                .width(Dp(200f)).height(Dp(150f))
                .align(AlignmentX.End, AlignmentY.Top)
                .margin(Dimensions.PaddingLarge)
                .background(
                    RoundRectBackground(
                        ColorTheme.UI.BackgroundSecondary.withAlpha(0.8f),
                        Dimensions.PaddingMedium
                    )
                )
                .border(RoundRectBorder(Color.GRAY, Dimensions.PaddingMedium, Dp(1f)))
                .zLayer(1000)

            modifier.background(UiRenderer { node ->
                val draw = node.getUiPrimitives()
                // Рисуем мини-версию узлов
                val mapScale = 0.1f
                val mapOffsetX = 50f
                val mapOffsetY = 50f

                nodes.forEach { n ->
                    val mx = n.x * mapScale + mapOffsetX
                    val my = n.y * mapScale + mapOffsetY
                    val mw = n.width * mapScale
                    val mh = 50f * mapScale // примерная высота

                    draw.rect(node.leftPx + mx, node.topPx + my, mw, mh, node.clipBoundsPx, n.color)
                }
            })

            Text("Мини-карта") {
                modifier.margin(Dimensions.PaddingMedium).textColor(Color.GRAY)
            }
        }
    }

    // Хелпер для зума размеров
    private fun Dp.scaled() = Dp(this.value * scale)

    // Хелпер для шрифтов
    private fun FontProps(size: Float = 14f, isBold: Boolean = false) =
        MsdfFont(
            ColorTheme.Fonts.MONOCRAFT,
            size * scale,
            weight = if (isBold) MsdfFont.WEIGHT_BOLD else MsdfFont.WEIGHT_REGULAR
        )
}

// --- UTILS FOR DRAWING ---

context(node: UiNode)
fun drawDashedArrow(draw: MeshBuilder<UiVertexLayout>, from: Vec2f, to: Vec2f, color: Color, scale: Float, label: String) {
    val dir = Vec2f(to.x - from.x, to.y - from.y)
    val length = dir.length()
    val normDir = Vec2f(dir.x / length, dir.y / length)

    // Параметры пунктира
    val dashLen = 10f * scale
    val gapLen = 5f * scale
    var currentPos = 0f

    // Рисуем линию сегментами
    while (currentPos < length - 15f * scale) { // -15 для стрелки
        val p1 = Vec2f(from.x + normDir.x * currentPos, from.y + normDir.y * currentPos)
        val endSegment = (currentPos + dashLen).coerceAtMost(length - 15f * scale)
        val p2 = Vec2f(from.x + normDir.x * endSegment, from.y + normDir.y * endSegment)

        draw.configure(color) {
            line(p1.x, p1.y, p2.x, p2.y, 2f * scale)
        }
        currentPos += dashLen + gapLen
    }

    // Рисуем стрелку в конце
    val arrowTip = to - (normDir * (5f * scale)) // Чуть отступаем от самого центра узла
    val arrowSize = 8f * scale
    val perp = Vec2f(-normDir.y, normDir.x)

    val corner1 = arrowTip - (normDir * arrowSize) + (perp * (arrowSize * 0.5f))
    val corner2 = arrowTip - (normDir * arrowSize) - (perp * (arrowSize * 0.5f))

    draw.configure(color) {
        //draw.triangle(arrowTip.x, arrowTip.y, corner1.x, corner1.y, corner2.x, corner2.y, color)
    }

    // Лейбл на линии
    if (label.isNotEmpty()) {
        // Здесь нужна логика рендеринга текста в примитивах, но в Kool UI2 проще нарисовать прямоугольник цвета фона
        // Для простоты примера пропускаем сложный рендеринг текста внутри UiRenderer,
        // обычно текст рисуется отдельными Text() нодами, позиционируемыми абсолютно.
    }
}