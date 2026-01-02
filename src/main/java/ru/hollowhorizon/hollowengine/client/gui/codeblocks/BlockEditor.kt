package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.Clipboard
import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.set
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.codeblocks.BlockGridBackground
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.common.codeblocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.model.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class BlockEditor(val provider: BlockProvider, val notifyChanged: () -> Unit) : BlocksScope {
    val controller = BlockController()
    override val rootBlocks: MutableStateList<BlockModel> = MutableStateList(ObservableBlockList(this))

    val scaleState = mutableStateOf(1.0f)

    var scale: Float = 1f
        private set

    private val snapAnimations = mutableListOf<SnapAnimation>()
    private val creationPopup = ItemPopupMenu<Vec2f>("BlockCreationMenu")
    private val blockPopup = ItemPopupMenu<Vec2f>("BlockPopupMenu")

    fun Dp.scaled(): Dp = Dp(this.value * scale)

    fun Float.scaled(): Float = this * scale

    fun getFont(baseSize: Float, isBold: Boolean = false): MsdfFont {
        val size = (baseSize * scale).coerceAtLeast(1f)
        return if (isBold) MsdfFont(ColorTheme.Fonts.MONOCRAFT, size, weight = MsdfFont.WEIGHT_EXTRA_BOLD)
        else MsdfFont(ColorTheme.Fonts.MONOCRAFT, size)
    }

    companion object {
        val C_BLOCK_SPINE_WIDTH = Dimensions.PaddingMedium
        val DROP_SENSOR_HEIGHT = Dimensions.PaddingMedium
    }

    fun UiScope.EditorLayout(body: ScrollPaneScope.() -> Unit) {
        controller.update()

        val smoothScale = animateSpringFloatAsState(
            scaleState.use(),
            stiffness = 600f,
            damping = 0.8f
        )
        scale = smoothScale.use()

        Box {
            modifier
                .width(Grow.Std)
                .height(Grow.Std)
                .onWheelX {
                    controller.scrollState.scrollDpX(it.pointer.scroll.x * -20f)
                }
                .onWheelY {
                    if (KeyboardInput.isCtrlDown) {

                        val oldScale = scaleState.value
                        val factor = if (it.pointer.scroll.y > 0) 1.1f else 0.9f
                        val newScale = (oldScale * factor).coerceIn(0.25f, 3.0f)

                        if (oldScale != newScale) {
                            scaleState.set(newScale)
                        }
                    } else if (KeyboardInput.isShiftDown) {
                        controller.scrollState.scrollDpX(it.pointer.scroll.x * -20f)
                    } else {
                        controller.scrollState.scrollDpY(it.pointer.scroll.y * -50f)
                    }
                }
                .onClick { createBlocksMenu(it) }
            modifier.background(
                BlockGridBackground(
                    this@BlockEditor,
                    3.dp,
                    Dimensions.PaddingLarge + Dimensions.PaddingNormal
                )
            )

            modifier.onDrag {
                val delta = it.pointer.delta
                if (delta.x != 0f) controller.scrollState.scrollDpX(Dp.fromPx(-delta.x).value)
                if (delta.y != 0f) controller.scrollState.scrollDpY(Dp.fromPx(-delta.y).value)
            }

            ScrollPane(controller.scrollState) {
                modifier.layout(CellLayout).padding(Dimensions.PaddingLarge.scaled())

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
                    .width(Dimensions.PaddingMedium).margin(Dimensions.PaddingMedium)
                    .colors(
                        trackColor = ColorTheme.UI.BackgroundSecondary,
                        trackHoverColor = ColorTheme.UI.BackgroundElements,
                        color = ColorTheme.UI.BackgroundAccent,
                        hoverColor = ColorTheme.UI.WhiteReplacement
                    )
                    .relativeBarPos(controller.scrollState.relativeBarPosY)
                    .relativeBarLen(controller.scrollState.relativeBarLenY)
                    .onChange { controller.scrollState.scrollRelativeY(it) }
                    .zLayer(100_000_000)
            }
            HorizontalScrollbar {
                modifier
                    .height(Dimensions.PaddingMedium).margin(Dimensions.PaddingMedium)
                    .colors(
                        trackColor = ColorTheme.UI.BackgroundSecondary,
                        trackHoverColor = ColorTheme.UI.BackgroundElements,
                        color = ColorTheme.UI.BackgroundAccent,
                        hoverColor = ColorTheme.UI.WhiteReplacement
                    )
                    .relativeBarPos(controller.scrollState.relativeBarPosX)
                    .relativeBarLen(controller.scrollState.relativeBarLenX)
                    .onChange { controller.scrollState.scrollRelativeX(it) }
                    .zLayer(100_000_000)
            }

            ScaleOverlay()

            blockPopup()
            creationPopup()
        }
    }

    private fun UiScope.ScaleOverlay() {
        Row {
            modifier.align(AlignmentX.End, AlignmentY.Bottom)
                .margin(Dimensions.PaddingHuge + Dimensions.PaddingMedium)
                .padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingNormal))
                .zLayer(100_000_000)
            
            Text("-") {
                val isHovered by modifier.hoverable()
                val textColor by animateColorAsState(if(isHovered) ColorTheme.UI.WhiteReplacement else ColorTheme.UI.BackgroundAccent)
                val color by animateColorAsState(if(isHovered) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundElements)

                modifier.alignY(AlignmentY.Center)
                    .textColor(textColor)
                    .margin(Dimensions.PaddingNormal)
                    .padding(Dimensions.PaddingNormal)
                    .background(RoundRectBackground(color, Dimensions.PaddingNormal))

                modifier.onClick {
                    scaleState.set((floor((scale - 0.01f) / 0.05f) * 0.05f).coerceAtLeast(0.25f))
                }
            }

            Text("${(scale * 100f).roundToInt()}%") {
                modifier.alignY(AlignmentY.Center)
                    .textColor(ColorTheme.UI.WhiteReplacement)
            }

            Text("+") {
                val isHovered by modifier.hoverable()
                val textColor by animateColorAsState(if(isHovered) ColorTheme.UI.WhiteReplacement else ColorTheme.UI.BackgroundAccent)
                val color by animateColorAsState(if(isHovered) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundElements)

                modifier.alignY(AlignmentY.Center)
                    .textColor(textColor)
                    .margin(Dimensions.PaddingNormal)
                    .padding(Dimensions.PaddingNormal)
                    .background(RoundRectBackground(color, Dimensions.PaddingNormal))

                modifier.onClick {
                    scaleState.set((ceil((scale + 0.01f) / 0.05f) * 0.05f).coerceAtMost(3.0f))
                }
            }
        }
    }

    private fun UiScope.createBlocksMenu(event: PointerEvent) {
        if (event.isRightClick) {
            val rootMenu = buildMenuFromProvider(provider, uiNode)
            creationPopup.show(Vec2f(event.screenPosition), rootMenu, Vec2f(event.screenPosition))
        }
    }

    context(scope: UiScope)
    internal fun renderBlockRecursively(
        block: BlockModel,
        isGhost: Boolean = false,
    ): Unit = with(scope) {
        val currentZoom = scale

        Column {
            val isRoot = rootBlocks.use().contains(block)
            modifier.width(if (isRoot) FitContent else Grow.Std)

            val isDragging = controller.isDragging(block)
            var baseLayer = if (isDragging) 1_000_000 else UiSurface.LAYER_DEFAULT
            rootBlocks.indexOf(block.root).takeUnless { it == -1 }?.let { baseLayer += it * 1000 }
            if (block.bodyRoot.parentBlock != null) baseLayer += 100
            if (block.isExpression()) modifier.zLayer(baseLayer + 100)
            else modifier.zLayer(baseLayer + 100 - block.parentCount)

            if (isRoot) {
                modifier.margin(
                    start = Dp.fromPx(block.positionX.use() * currentZoom),
                    top = Dp.fromPx(block.positionY.use() * currentZoom)
                )
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
                    modifier.width(FitContent)

                    Column {
                        modifier.width(FitContent)
                        val isHovered = remember { mutableStateOf(false) }

                        BlockHeaderVisual(isHovered, block, isGhost) {
                            modifier
                                .onDragStart { ev -> controller.handleDragStart(block, ev) }
                                .onDrag { ev -> controller.handleDrag(block, ev) }
                                .onDragEnd { controller.handleDragEnd(block) }
                                .onClick { onBlockRightClick(block, it, uiNode) }
                                .onEnter { isHovered.set(true) }
                                .onHover { PointerInput.cursorShape = CursorShape.HAND }
                                .onExit { isHovered.set(false) }
                        }

                        block.let { it as? ContainerBlock }?.let {
                            with(InputSlotScope(this@BlockEditor, this, block, isHovered.use(), isGhost)) {
                                with(it) { composeBody() }
                            }
                        }

                        if (block is ContainerBlock) {
                            Box {
                                modifier.height(Dimensions.PaddingHuge.scaled()).width(Grow.Std)
                                val isUnused =
                                    block.parentsWithSelf.none { it is StartBlock } && block.root in rootBlocks
                                val bgColor = if (isGhost) block.color.withAlpha(0.5f)
                                else if (isUnused) block.color.mix(Color.LIGHT_GRAY, 0.5f).withAlpha(0.35f)
                                else block.color
                                val color by animateColorAsState(
                                    if (isHovered.value) bgColor else bgColor.mulRgb(0.9f),
                                    tween(0.2f, Easing.easeOutQuart)
                                )
                                modifier.background(ContainerFooterBackground(color, currentZoom, block !is EndBlock))

                                if (!isDragging) {
                                    Box {
                                        modifier.width(Grow.Std).alignY(AlignmentY.Bottom)
                                            .height(DROP_SENSOR_HEIGHT.scaled())
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
                                .width(Grow.Std).height(DROP_SENSOR_HEIGHT.scaled())
                                .alignY(AlignmentY.Top)
                            controller.addDropTarget(DropAction.InsertBefore(block), uiNode)
                        }

                        if (block !is ContainerBlock) {
                            Box {
                                modifier
                                    .width(Grow.Std).height(DROP_SENSOR_HEIGHT.scaled())
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

    private fun onBlockRightClick(block: BlockModel, event: PointerEvent, uiNode: UiNode) {
        if (event.isRightClick) {
            blockPopup.show(Vec2f(event.screenPosition), SubMenuItem("Блок", null) {
                item("Дублировать") { controller.duplicateBlock(block, it) }
                item("Копировать UUID") { Clipboard.copyToClipboard(block.uuid.toString()) }
                item("Удалить") { controller.removeBlock(block) }
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
            modifier.apply(blockModifier)

            val isUnused = block.parentsWithSelf.none { it is StartBlock } && block.root in rootBlocks
            val bgColor = if (isGhost) block.color.withAlpha(0.5f)
            else if (isUnused) block.color.mix(Color.LIGHT_GRAY, 0.5f).withAlpha(0.35f)
            else block.color

            val color by animateColorAsState(
                if (isHovered.use()) bgColor else bgColor.mulRgb(0.9f),
                tween(0.2f, Easing.easeOutQuart)
            )
            val isContainer = block is ContainerBlock
            val currentZoom = scale

            modifier.background(
                ScratchBlockBackground(
                    color = color,
                    zoom = currentZoom,
                    isExpression = block.isExpression(),
                    hasNext = !block.isExpression(),
                    hasPrev = block !is StartBlock,
                    isContainerHeader = isContainer,
                    drawInnerShadow = block.isExpression() && block.parentBlock != null,
                )
            )

            with(block) {
                Row(Grow.Std) {
                    modifier.apply(blockModifier)
                    modifier
                        .padding(
                            horizontal = Dimensions.PaddingMedium.scaled(),
                            vertical = Dimensions.PaddingNormal.scaled()
                        )
                        .alignY(AlignmentY.Center)

                    InputSlotScope(this@BlockEditor, this, block, isHovered.use(), isGhost).composeContent()
                }
            }
        }
    }

    internal fun UiScope.GhostPlaceholder(isExpression: Boolean) {
        Box(Grow.Std) {
            if (isExpression) modifier.size(40.dp.scaled(), 30.dp.scaled())
            else modifier.height(40.dp.scaled()).width(100.dp.scaled())

            modifier.background(
                ScratchBlockBackground(
                    Color.WHITE.withAlpha(0.2f),
                    scale,
                    isExpression,
                    !isExpression
                )
            )
            if (!isExpression) modifier.margin(vertical = Dimensions.PaddingSmall.scaled())
        }
    }

    fun triggerSnapEffect(action: SnapAnimation) {
        snapAnimations.add(action)
    }

    private fun UiScope.renderSnapAnimations() {
        snapAnimations.removeIf { it.isFinished }
        val currentZoom = scale

        Box {
            modifier.width(Grow.Std).height(Grow.Std)
            modifier.background(UiRenderer { node ->
                node.apply {
                    val drawList = getPlainBuilder(UiSurface.LAYER_FLOATING)
                    snapAnimations.forEach { anim ->
                        val p = anim.animator.updateUsing()
                        val baseScale = 10f + p * 30f
                        val actualScale = baseScale * currentZoom
                        val alpha = Easing.quad(1f - p).coerceIn(0f, 1f)

                        drawList.configured(Color.WHITE.withAlpha(alpha)) {
                            translate(anim.x * currentZoom, anim.y * currentZoom, 0f)
                            scale(actualScale, actualScale, 1f)

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

inline fun <reified T> UiNode.findParentOfType(filter: (T) -> Boolean = { true }): T? {
    var current: UiNode? = this

    while (current != null && !(current is T && filter(current))) current = current.parent
    return current as? T
}