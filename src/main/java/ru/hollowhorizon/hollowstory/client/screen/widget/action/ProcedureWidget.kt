package ru.hollowhorizon.hollowstory.client.screen.widget.action

import com.mojang.blaze3d.matrix.MatrixStack
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hc.client.utils.mc
import ru.hollowhorizon.hc.client.utils.toRGBA
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowstory.HollowStory.MODID
import ru.hollowhorizon.hollowstory.client.screen.VisualEditorScreen

abstract class ProcedureWidget(x: Int, y: Int, width: Int, height: Int, color: Int = 0x18CDED) :
    HollowWidget(x, y, width, height, "".toSTC()), IProcedureLoader {
    private var lineDriver: LineDrawer? = null
    var isLeftClicked: Boolean = false
    var canMove = false
    var isStartConnecting: Boolean = false
    var resizeStatus: ResizeStatus = ResizeStatus.NONE
    var parent: ProcedureWidget? = null
    var children: MutableList<ProcedureWidget> = mutableListOf()
    private var color = color.toRGBA()

    @Suppress("DEPRECATION")
    override fun renderButton(stack: MatrixStack, mouseX: Int, mouseY: Int, ticks: Float) {
        if(lineDriver != null) {
            lineDriver!!.render(stack, mouseX, mouseY)
        }
        drawConnections(stack)
        drawNodes(stack)

        bind(MODID, "gui/widgets/procedure_widget.png")
        RenderSystem.color4f(color.r, color.g, color.b, 1.0F)
        drawWidget(stack)
        RenderSystem.color4f(1f, 1f, 1f, 1f)
    }

    private fun drawConnections(stack: MatrixStack) {
        val drawer = LineDrawer(this.x + width, this.y + height / 2)

        for(child in children) {
            drawer.render(stack, child.x, child.y + child.height / 2)
        }
    }

    private fun drawNodes(stack: MatrixStack) {
        bind(MODID, "gui/widgets/orb.png")

        blit(stack, x - 5, y + height / 2 - 5, 0F, (if (parent != null) 0 else 1) * 10F, 10, 10, 10, 30)

        blit(
            stack, x + width - 5, y + height / 2 - 5, 0F, (
                    when (children.size) {
                        0 -> 1
                        1 -> 0
                        else -> 2
                    }
                    ) * 10F, 10, 10, 10, 30
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isHovered) {
            if(mouseX > this.x + width - 5 && mouseX < this.x + width + 5 && mouseY > this.y + height / 2 - 5 && mouseY < this.y + height / 2 + 5) {
                isStartConnecting = true
                this.lineDriver = LineDrawer(this.x + width, this.y + height / 2)
                return true
            }

            isLeftClicked = true
            calculateResizeStatus(mouseX, mouseY)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    fun calculateResizeStatus(mouseX: Double, mouseY: Double) {
        val x = mouseX - this.x
        val y = mouseY - this.y
        val size = 10
        resizeStatus = if (x < size && y < size) {
            ResizeStatus.TOP_LEFT
        } else if (x > width - size && y < size) {
            ResizeStatus.TOP_RIGHT
        } else if (x < size && y > height - size) {
            ResizeStatus.BOTTOM_LEFT
        } else if (x > width - size && y > height - size) {
            ResizeStatus.BOTTOM_RIGHT
        } else if (x < size) {
            ResizeStatus.LEFT
        } else if (x > width - size) {
            ResizeStatus.RIGHT
        } else if (y < size) {
            ResizeStatus.TOP
        } else if (y > height - size) {
            ResizeStatus.BOTTOM
        } else {
            ResizeStatus.NONE
        }
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        if (isLeftClicked) {
            val sizeX = width - mouseX.toInt() - this.x
            val sizeY = height - mouseY.toInt() - this.y
            when (resizeStatus) {
                ResizeStatus.TOP_LEFT -> {
                    if (sizeX < 20 || sizeY < 20 || sizeX > 200 || sizeY > 100) return
                    width -= mouseX.toInt() - this.x
                    height -= mouseY.toInt() - this.y
                    this.x = mouseX.toInt()
                    this.y = mouseY.toInt()
                    width = width.lerp(20, 200)
                    height = height.lerp(20, 100)
                    return
                }

                ResizeStatus.TOP_RIGHT -> {
                    if (sizeX < 20 || sizeY < 20 || sizeX > 200 || sizeY > 100) return
                    width += mouseX.toInt() - this.x - width
                    height -= mouseY.toInt() - this.y
                    this.y = mouseY.toInt()
                    width = width.lerp(20, 200)
                    height = height.lerp(20, 100)
                    return
                }

                ResizeStatus.BOTTOM_LEFT -> {
                    if (sizeX < 20 || sizeY < 20 || sizeX > 200 || sizeY > 100) return
                    width -= mouseX.toInt() - this.x
                    height += mouseY.toInt() - this.y - height
                    this.x = mouseX.toInt()
                    width = width.lerp(20, 200)
                    height = height.lerp(20, 100)
                    return
                }

                ResizeStatus.BOTTOM_RIGHT -> {
                    width += mouseX.toInt() - this.x - width
                    height += mouseY.toInt() - this.y - height
                    width = width.lerp(20, 200)
                    height = height.lerp(20, 100)
                    return
                }

                ResizeStatus.LEFT -> {
                    if (sizeX < 20 || sizeY < 20 || sizeX > 200 || sizeY > 100) return
                    width -= mouseX.toInt() - this.x
                    this.x = mouseX.toInt()
                    width = width.lerp(20, 200)
                    height = height.lerp(20, 100)
                    return
                }

                ResizeStatus.RIGHT -> {
                    width += mouseX.toInt() - this.x - width
                    width = width.lerp(20, 200)
                    height = height.lerp(20, 100)
                    return
                }

                ResizeStatus.TOP -> {
                    if (sizeX < 20 || sizeY < 20 || sizeX > 200 || sizeY > 100) return
                    height -= mouseY.toInt() - this.y
                    this.y = mouseY.toInt()
                    width = width.lerp(20, 200)
                    height = height.lerp(20, 100)
                    return
                }

                ResizeStatus.BOTTOM -> {
                    height += mouseY.toInt() - this.y - height
                    width = width.lerp(20, 200)
                    height = height.lerp(20, 100)
                    return
                }

                else -> {}
            }


            this.setX(mouseX.toInt()-width/2)
            this.setY(mouseY.toInt()-height/2)
        }
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if(isStartConnecting) {
                isStartConnecting = false
                this.lineDriver = null

                mc.screen?.let {
                    if(it is VisualEditorScreen) {
                        val widget = it.getWidgetAt(mouseX, mouseY)
                        if(widget != null && widget != this) {
                            this.children.add(widget)
                            widget.parent = this
                        }
                    }
                }

                return true
            }
            isLeftClicked = false
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    fun drawWidget(stack: MatrixStack) {
        if (width > height) {
            val size = height / 3
            blit(stack, x, y, 0F, 0F, size, size, size * 3, size * 3)
            blit(stack, x + size, y, width - size * 2F, 0F, width - size * 2, size, (width - size * 2) * 3, size * 3)
            blit(stack, x + width - size, y, size * 2F, 0F, size, size, size * 3, size * 3)

            blit(stack, x, y + size, 0F, size * 1F, size, height - size * 2, size * 3, (height - size * 2) * 3)
            blit(stack, x + size, y + size, width - size * 2F, size * 1F, width - size * 2, height - size * 2, (width - size * 2) * 3, (height - size * 2) * 3)
            blit(stack,x + width - size, y + size, size * 2F, size * 1F, size, height - size * 2, size * 3, (height - size * 2) * 3)

            blit(stack, x, y + height - size, 0F, size * 2F, size, size, size * 3, size * 3)
            blit(stack, x + size, y + height - size, width - size * 2F, size * 2F, width - size * 2, size, (width - size * 2) * 3, size * 3)
            blit(stack, x + width - size, y + height - size, size * 2F, size * 2F, size, size, size * 3, size * 3)
        } else {
            val size = width / 3
            blit(stack, x, y, 0F, 0F, size, size, size * 3, size * 3)
            blit(stack, x + size, y, width - size * 2F, 0F, width - size * 2, size, (width - size * 2) * 3, size * 3)
            blit(stack, x + width - size, y, size * 2F, 0F, size, size, size * 3, size * 3)

            blit(stack, x, y + size, 0F, height - size * 2F, size, height - size * 2, size * 3, (height - size * 2) * 3)
            blit(stack, x + size, y + size, width - size * 2F, height - size * 2F, width - size * 2, height - size * 2, (width - size * 2) * 3, (height - size * 2) * 3)
            blit(stack, x + width - size, y + size, size * 2F, height - size * 2F, size, height - size * 2, size * 3, (height - size * 2) * 3)

            blit(stack, x, y + height - size, 0F, size * 2F, size, size, size * 3, size * 3)
            blit(stack, x + size, y + height - size, width - size * 2F, size * 2F, width - size * 2, size, (width - size * 2) * 3, size * 3)
            blit(stack, x + width - size, y + height - size, size * 2F, size * 2F, size, size, size * 3, size * 3)
        }
    }

    enum class ResizeStatus {
        NONE, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    fun Int.lerp(min: Int, max: Int): Int {
        return if (this < min) min
        else if (this > max) max
        else this
    }

    fun canConnect(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= x - 5 && mouseX <= x + 5 && mouseY >= y + height / 2 - 5 && mouseY <= y + height / 2 + 5
    }
}