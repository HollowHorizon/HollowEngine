package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.Clipboard
import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.set
import ru.hollowhorizon.hollowengine.client.gui.scripting.EditorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockProvider
import ru.hollowhorizon.hollowengine.common.codeblocks.BlocksScope
import ru.hollowhorizon.hollowengine.common.codeblocks.isExpression
import ru.hollowhorizon.hollowengine.common.codeblocks.model.*
import ru.hollowhorizon.hollowengine.common.codeblocks.parentCount

class BlockEditor(val provider: BlockProvider, val notifyChanged: () -> Unit) : BlocksScope {
    val controller = BlockController()
    override val rootBlocks = mutableStateListOf<BlockModel>()


    private val snapAnimations = mutableListOf<SnapAnimation>()

    private val creationPopup = ItemPopupMenu<Vec2f>("BlockCreationMenu")
    private val blockPopup = ItemPopupMenu<Vec2f>("BlockPopupMenu")

    companion object {
        const val C_BLOCK_SPINE_WIDTH = 20f
        val DROP_SENSOR_HEIGHT = Dp(20f)
    }

    fun UiScope.EditorLayout(body: ScrollPaneScope.() -> Unit) {
        controller.update()

        val state = rememberScrollState()

        Box {
            modifier
                .width(Grow.Std)
                .height(Grow.Std)
                .onWheelX {
                    state.scrollDpX(it.pointer.scroll.x * -20f)
                }
                .onWheelY {
                    state.scrollDpY(it.pointer.scroll.y * -50f)
                }
                .onClick { createBlocksMenu(it) }

            modifier.onDrag {
                val delta = it.pointer.delta
                if (delta.x != 0f) {
                    state.scrollDpX(Dp.fromPx(-delta.x).value)
                }
                if (delta.y != 0f) {
                    state.scrollDpY(Dp.fromPx(-delta.y).value)
                }
            }

            ScrollPane(state) {
                modifier.layout(CellLayout)
                    .padding(sizes.largeGap)
                modifier.onClick {
                    createBlocksMenu(it)
                    controller.resetAction()
                }

                rootBlocks.use().forEach { block -> renderBlockRecursively(block) }
                body()

                renderSnapAnimations()
            }

            VerticalScrollbar {
                modifier
                    .width(sizes.smallGap).margin(sizes.smallGap)
                    .colors(
                        trackColor = EditorTheme.Scrollbar.trackColor,
                        trackHoverColor = EditorTheme.Scrollbar.trackHover,
                        color = EditorTheme.Scrollbar.color,
                        hoverColor = EditorTheme.Scrollbar.hoverColor,
                    )
                    .relativeBarPos(state.relativeBarPosY)
                    .relativeBarLen(state.relativeBarLenY)
                    .onChange { state.scrollRelativeY(it) }
            }
            HorizontalScrollbar {
                modifier
                    .height(sizes.smallGap).margin(sizes.smallGap)
                    .colors(
                        trackColor = EditorTheme.Scrollbar.trackColor,
                        trackHoverColor = EditorTheme.Scrollbar.trackHover,
                        color = EditorTheme.Scrollbar.color,
                        hoverColor = EditorTheme.Scrollbar.hoverColor,
                    )
                    .relativeBarPos(state.relativeBarPosX)
                    .relativeBarLen(state.relativeBarLenX)
                    .onChange { state.scrollRelativeX(it) }
            }

            blockPopup()
            creationPopup()
        }
    }

    private fun UiScope.createBlocksMenu(event: PointerEvent) {
        if (event.isRightClick) {
            val rootMenu = buildMenuFromProvider(provider, uiNode)
            creationPopup.show(Vec2f(event.screenPosition), rootMenu, Vec2f(event.screenPosition))
        }
    }

    internal fun UiScope.renderBlockRecursively(
        block: BlockModel,
        isGhost: Boolean = false,
    ) {
        Column {
            val isRoot = rootBlocks.contains(block)
            modifier.width(if (isRoot) FitContent else Grow.Std)

            val isDragging = controller.isDragging(block)
            val baseLayer = if (isDragging) UiSurface.LAYER_FLOATING else UiSurface.LAYER_DEFAULT
            if (block.isExpression()) modifier.zLayer(baseLayer + 100)
            else modifier.zLayer(baseLayer + 100 - block.parentCount)

            if (isRoot) {
                modifier.margin(start = Dp.fromPx(block.positionX.use()), top = Dp.fromPx(block.positionY.use()))
            }

            Column {
                modifier.width(Grow.Std)

                if (!block.isExpression() && controller.canAttachBefore(block) && !isDragging) {
                    Column(Grow.Std) {
                        GhostPlaceholder(false)
                        controller.addDropTarget(DropAction.InsertBefore(block), uiNode)
                    }
                }

                Box {
                    modifier.width(Grow.Std)

                    Column {
                        modifier.width(Grow.Std)

                        val isHovered = remember { mutableStateOf(false) }

                        BlockHeaderVisual(isHovered, block, isGhost) {
                            modifier
                                .onDragStart { ev -> controller.handleDragStart(block, ev) }
                                .onDrag { ev -> controller.handleDrag(block, ev) }
                                .onDragEnd { controller.handleDragEnd(block) }
                                .onClick {
                                    onBlockRightClick(block, it, uiNode)
                                }
                                .onEnter {
                                    isHovered.set(true)
                                }
                                .onHover {
                                    PointerInput.cursorShape = CursorShape.HAND
                                }
                                .onExit {
                                    isHovered.set(false)
                                }
                        }

                        block.let { it as? ContainerBlock }?.let {
                            with(InputSlotScope(this@BlockEditor, this, block, isHovered.use(), isGhost)) {
                                with(it) { composeBody() }
                            }
                        }

                        if (block is ContainerBlock) {
                            Box {
                                modifier.height(20.dp).width(Grow.Std)
                                val bgColor = if (isGhost) block.color.withAlpha(0.5f) else block.color
                                val color by animateColorAsState(
                                    if (isHovered.value) bgColor else bgColor.mulRgb(0.9f), tween(
                                        0.2f,
                                        Easing.quadRev
                                    )
                                )
                                modifier.background(ContainerFooterBackground(color, block !is EndBlock))

                                if (!isDragging) {
                                    Box {
                                        modifier.width(Grow.Std).alignY(AlignmentY.Bottom)
                                            .height(DROP_SENSOR_HEIGHT)
                                        controller.addDropTarget(
                                            DropAction.AttachAfter(block as StatementBlock),
                                            uiNode
                                        )

                                    }
                                }
                            }
                        }
                    }

                    if (!isDragging && !block.isExpression()) {
                        Box {
                            modifier
                                .width(Grow.Std).height(DROP_SENSOR_HEIGHT)
                                .alignY(AlignmentY.Top)

                            controller.addDropTarget(DropAction.InsertBefore(block), uiNode)
                        }

                        if (block !is ContainerBlock) {
                            Box {
                                modifier
                                    .width(Grow.Std).height(DROP_SENSOR_HEIGHT)
                                    .alignY(AlignmentY.Bottom)

                                controller.addDropTarget(DropAction.AttachAfter(block as StatementBlock), uiNode)
                            }
                        }
                    }
                }

                if (block is StatementBlock) {
                    if (controller.canAttachAfter(block) && !isDragging) {
                        GhostPlaceholder(false)
                    }

                    block.next?.let { next -> renderBlockRecursively(next, isGhost) }
                }
            }
        }
    }

    private fun onBlockRightClick(
        block: BlockModel,
        event: PointerEvent,
        uiNode: UiNode,
    ) {

        if (event.isRightClick) {
            blockPopup.show(Vec2f(event.screenPosition), SubMenuItem("Блок", null) {
                item("Дублировать") {
                    controller.duplicateBlock(block, it)
                }
                item("Копировать UUID") {
                    Clipboard.copyToClipboard(block.uuid.toString())
                }
                item("Удалить") {
                    controller.removeBlock(block)
                }
            }, Vec2f((uiNode.findParentOfType<ScrollPaneNode>() ?: uiNode).toLocal(event.screenPosition)))
        }
    }

    private fun UiScope.BlockHeaderVisual(
        isHovered: MutableStateValue<Boolean>,
        block: BlockModel,
        isGhost: Boolean,
        blockModifier: UiModifier.() -> Unit,
    ) {
        Box {
            val marginLeft = if (block.isExpression()) Dp.fromPx(PuzzleShapes.TAB_WIDTH) else 0.dp
            modifier.width(Grow.Std).margin(start = marginLeft).apply(blockModifier)

            val bgColor = if (isGhost) block.color.withAlpha(0.5f) else block.color
            val color by animateColorAsState(
                if (isHovered.use()) bgColor else bgColor.mulRgb(0.9f),
                tween(0.2f, Easing.quadRev)
            )
            val isContainer = block is ContainerBlock

            modifier.background(
                ScratchBlockBackground(
                    color = color,
                    isExpression = block.isExpression(),
                    hasNext = !block.isExpression(),
                    hasPrev = block !is StartBlock,
                    isContainerHeader = isContainer,
                    drawInnerShadow = block.isExpression() && block.parentBlock != null,
                )
            )

            with(block) {
                Row(Grow.Std) {
                    modifier.margin(start = marginLeft).apply(blockModifier)
                    modifier.padding(horizontal = 10.dp, vertical = 6.dp).alignY(AlignmentY.Center)

                    InputSlotScope(this@BlockEditor, this, block, isHovered.use(), isGhost).composeContent()
                }
            }
        }
    }

    internal fun UiScope.GhostPlaceholder(isExpression: Boolean) {
        Box(Grow.Std) {
            if (isExpression) modifier.size(40.dp, 30.dp)
            else modifier.height(40.dp).width(100.dp)
            modifier.background(ScratchBlockBackground(Color.WHITE.withAlpha(0.2f), isExpression, !isExpression))
            if (!isExpression) modifier.margin(vertical = 2.dp)
        }
    }

    fun triggerSnapEffect(action: SnapAnimation) {
        snapAnimations.add(action)
    }

    private fun UiScope.renderSnapAnimations() {
        snapAnimations.removeIf { it.isFinished }

        Box {
            modifier.width(Grow.Std).height(Grow.Std)
            modifier.background(UiRenderer { node ->
                node.apply {
                    val drawList = getPlainBuilder(UiSurface.LAYER_FLOATING)

                    snapAnimations.forEach { anim ->
                        val p = anim.animator.progressAndUse()

                        val scale = 10f + p * 30f

                        val alpha = Easing.quad(1f - p).coerceIn(0f, 1f)

                        drawList.configured(Color.WHITE.withAlpha(alpha)) {
                            translate(anim.x, anim.y, 0f)
                            scale(scale, scale, 1f)

                            // Используем нашу "ручную" геометрию
                            val i0 = geometry.numVertices
                            for (v in RingGeometry.vertices) {
                                vertex { it.position.set(v) }
                            }
                            for (i in RingGeometry.indices) {
                                geometry.addIndex(i0 + i)
                            }
                        }
                    }
                }
            })
        }
    }
}

inline fun <reified T> UiNode.findParentOfType(): T? {
    var current: UiNode? = this
    while (current != null && current !is T) current = current.parent
    return current as? T
}