package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiRenderer
import de.fabmax.kool.modules.ui2.UiSurface
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.PolyUtil

// Обновленный фон для верхней части
class ScratchBlockBackground(
    val color: Color,
    val isExpression: Boolean,
    val hasNext: Boolean,
    val isContainerHeader: Boolean = false // Если true, рисуем внутренний зубчик внизу
) : UiRenderer<UiNode> {

    private val notchWidth = 30f
    private val notchHeight = 8f
    private val notchX = 20f
    private val r = PuzzleShapes.CORNER_RADIUS

    override fun renderUi(node: UiNode) = with(node) {
        val w = node.widthPx
        val h = node.heightPx
        val x = 0f
        val y = 0f
        val points = mutableListOf<Vec3f>()

        if (isExpression) {
            // Рисуем пазл (как было)
            PuzzleShapes.addBezier(points, x, y + r, x, y, x + r, y)
            points.add(Vec3f(x + w - r, y, 0f))
            PuzzleShapes.addBezier(points, x + w - r, y, x + w, y, x + w, y + r)
            PuzzleShapes.addBezier(points, x + w, y + h - r, x + w, y + h, x + w - r, y + h)
            points.add(Vec3f(x + r, y + h, 0f))
            PuzzleShapes.addBezier(points, x + r, y + h, x, y + h, x, y + h - r)
            val tyStart = (h - PuzzleShapes.TAB_HEIGHT) / 2f
            points.add(Vec3f(x, tyStart + PuzzleShapes.TAB_HEIGHT, 0f))
            points.add(Vec3f(x - PuzzleShapes.TAB_WIDTH, tyStart + PuzzleShapes.TAB_HEIGHT - 5f, 0f))
            points.add(Vec3f(x - PuzzleShapes.TAB_WIDTH, tyStart + 5f, 0f))
            points.add(Vec3f(x, tyStart, 0f))
        } else {
            // Верхняя грань с выемкой
            PuzzleShapes.addBezier(points, x, y + r, x, y, x + r, y)
            points.add(Vec3f(x + notchX, y, 0f))
            points.add(Vec3f(x + notchX + 5f, y + notchHeight, 0f))
            points.add(Vec3f(x + notchX + notchWidth - 5f, y + notchHeight, 0f))
            points.add(Vec3f(x + notchX + notchWidth, y, 0f))
            PuzzleShapes.addBezier(points, x + w - r, y, x + w, y, x + w, y + r)

            // Правая грань
            PuzzleShapes.addBezier(points, x + w, y + h - r, x + w, y + h, x + w - r, y + h)

            // Нижняя грань
            if (isContainerHeader) {
                // Если это начало C-блока, внизу рисуем "внутренний зубчик"
                // Это имитация выступа для внутренней секции
                val innerNotchX = BlockEditor.C_BLOCK_SPINE_WIDTH + notchX

                // Идем от правого края влево до выемки
                points.add(Vec3f(x + innerNotchX + notchWidth, y + h, 0f))
                // Рисуем зубчик ВНИЗ (для внутреннего блока это будет верхний паз)
                points.add(Vec3f(x + innerNotchX + notchWidth - 5f, y + h + notchHeight, 0f))
                points.add(Vec3f(x + innerNotchX + 5f, y + h + notchHeight, 0f))
                points.add(Vec3f(x + innerNotchX, y + h, 0f))

                // Идем к левому краю (к позвоночнику)
                points.add(Vec3f(x, y + h, 0f)) // Spine width logic handled by layout, visually connects here
            } else {
                // Обычный блок
                if (hasNext) {
                    points.add(Vec3f(x + notchX + notchWidth, y + h, 0f))
                    points.add(Vec3f(x + notchX + notchWidth - 5f, y + h + notchHeight, 0f))
                    points.add(Vec3f(x + notchX + 5f, y + h + notchHeight, 0f))
                    points.add(Vec3f(x + notchX, y + h, 0f))
                }
                PuzzleShapes.addBezier(points, x + r, y + h, x, y + h, x, y + h - r)
            }
        }

        drawPath(node, points, color)
    }

    private fun drawPath(node: UiNode, points: List<Vec3f>, color: Color) = with(node) {
        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configured(color, clipped = false) {
            fillPolygon(PolyUtil.fillPolygon(points))
        }
        val strokeColor = color.mix(Color.BLACK, 0.2f)
        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configured(strokeColor, clipped = false) {
            for (i in 0 until points.size) {
                val p1 = points[i]
                val p2 = points[(i + 1) % points.size]
                // Не рисуем линию замыкания слева, если это header контейнера, чтобы слилось с телом
                line(p1.xy, p2.xy, 2f)
            }
        }
    }
}

// Фон для "подвала" C-блока
class ContainerFooterBackground(val color: Color) : UiRenderer<UiNode> {
    private val notchWidth = 30f
    private val notchHeight = 8f
    private val notchX = 20f
    private val r = PuzzleShapes.CORNER_RADIUS

    override fun renderUi(node: UiNode) = with(node) {
        val w = node.widthPx
        val h = node.heightPx
        val x = 0f
        val y = 0f
        val points = mutableListOf<Vec3f>()

        // Внутренний X для зубчика (должен совпадать с отступом тела)
        val innerNotchX = BlockEditor.C_BLOCK_SPINE_WIDTH + notchX

        // Верхняя грань (внутренняя) с зубчиком
        points.add(Vec3f(x, y, 0f))
        points.add(Vec3f(x + innerNotchX, y, 0f))
        points.add(Vec3f(x + innerNotchX + 5f, y + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth - 5f, y + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth, y, 0f))

        // Правая грань
        PuzzleShapes.addBezier(points, x + w - r, y, x + w, y, x + w, y + r)
        PuzzleShapes.addBezier(points, x + w, y + h - r, x + w, y + h, x + w - r, y + h)

        // Нижняя грань с внешним зубчиком (для следующего блока)
        points.add(Vec3f(x + notchX + notchWidth, y + h, 0f))
        points.add(Vec3f(x + notchX + notchWidth - 5f, y + h + notchHeight, 0f))
        points.add(Vec3f(x + notchX + 5f, y + h + notchHeight, 0f))
        points.add(Vec3f(x + notchX, y + h, 0f))

        // Левая грань
        PuzzleShapes.addBezier(points, x + r, y + h, x, y + h, x, y + h - r)

        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configured(color, clipped = false) {
            fillPolygon(PolyUtil.fillPolygon(points))
        }
        val strokeColor = color.mix(Color.BLACK, 0.2f)
        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configured(strokeColor, clipped = false) {
            for (i in 0 until points.size) {
                val p1 = points[i]
                val p2 = points[(i + 1) % points.size]
                line(p1.xy, p2.xy, 2f)
            }
        }
    }
}

class ContainerMiddleBackground(val color: Color) : UiRenderer<UiNode> {
    private val notchWidth = 30f
    private val notchHeight = 8f
    private val notchX = 20f // Сдвиг от края позвоночника
    private val spineW = BlockEditor.C_BLOCK_SPINE_WIDTH

    override fun renderUi(node: UiNode) = with(node) {
        val w = node.widthPx
        val h = node.heightPx
        val x = 0f
        val y = 0f
        val points = mutableListOf<Vec3f>()

        val innerNotchX = spineW + notchX

        // 1. Верхняя грань (впадина для зуба сверху)
        points.add(Vec3f(x, y, 0f))
        points.add(Vec3f(x + innerNotchX, y, 0f))
        points.add(Vec3f(x + innerNotchX + 5f, y + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth - 5f, y + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth, y, 0f))

        // Правая сторона
        points.add(Vec3f(x + w, y, 0f))
        points.add(Vec3f(x + w, y + h, 0f))

        // 2. Нижняя грань (зуб вниз для следующего блока)
        points.add(Vec3f(x + innerNotchX + notchWidth, y + h, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth - 5f, y + h + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + 5f, y + h + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX, y + h, 0f))
        points.add(Vec3f(x, y + h, 0f))

        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configured(color, clipped = false) {
            fillPolygon(PolyUtil.fillPolygon(points))
        }
    }
}