package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.input.*
import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.MutableColor
import de.fabmax.kool.util.set
import ru.hollowhorizon.hollowengine.client.audio.UIAudio
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.colors.PaddingLargeSpacing
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.common.codeblocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.model.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

open class BlockEditor(val provider: BlockProvider, val notifyChanged: () -> Unit) : BlocksScope {
    val controller = BlockController(this)
    val dragState = DragState(this)
    val blocksPanel = BlocksPanel(this)
    override val rootBlocks: MutableStateList<BlockModel> = MutableStateList(ObservableBlockList(this))

    // State
    val scaleState = mutableStateOf(1.0f)
    var scale: Float = 1f
        internal set

    // UI Components
    private val snapAnimations = mutableListOf<SnapAnimation>()
    private val creationPopup = ItemPopupMenu<Vec2f>("BlockCreationMenu")

    // --- Public API & Helpers ---
    fun Dp.scaled(): Dp = Dp(this.value * scale)

    fun getFont(baseSize: Float, isBold: Boolean = false): MsdfFont {
        val size = (baseSize * scale).coerceAtLeast(1f)
        return if (isBold) MsdfFont(ColorTheme.Fonts.MONOCRAFT, size, weight = MsdfFont.WEIGHT_EXTRA_BOLD)
        else MsdfFont(ColorTheme.Fonts.MONOCRAFT, size)
    }

    fun triggerSnapEffect(action: SnapAnimation) {
        snapAnimations.add(action)
    }

    companion object {
        const val SCROLL_SPEED_X = -50f
        const val SCROLL_SPEED_Y = -50f
        const val Z_LAYER_DRAGGING = 1_000_000
        const val Z_LAYER_SCROLLBAR = 100_000_000

        private val KEY_CODE_SELECT_ALL = UniversalKeyCode('A')
        private val KEY_CODE_CUT = UniversalKeyCode('X')
        private val KEY_COPY = UniversalKeyCode('C')
        private val KEY_PASTE = UniversalKeyCode('V')
        private val KEY_UNDO = UniversalKeyCode('Z')
        private val KEY_REDO = UniversalKeyCode('Y')
        private val KEY_DUPLICATE = UniversalKeyCode('D')


        val C_BLOCK_SPINE_WIDTH = Dimensions.PaddingMedium
    }

    // --- Main Layout ---

    fun UiScope.EditorLayout(body: UiScope.() -> Unit) {
        controller.update()
        updateScaleAnimation()

        Row(Grow.Std, Grow.Std) {
            val expansion by animateFloatAsState(
                if (!controller.isBlockPanelMinimized.use()) 1f else 0f,
                tween(0.3f, Easing.easeOutQuart)
            )
            if (!controller.isBlockPanelMinimized.use() || expansion > 0f) blocksPanel(expansion)

            Box(Grow.Std, Grow.Std) {
                EditorScrollbars(controller)
                ScaleOverlay()
                BlockContextMenu.draw()
                creationPopup()
                dragState()

                if (controller.isBlockPanelMinimized.use()) {
                    blocksPanel.CollapseButton {
                        modifier.align(AlignmentX.Start, AlignmentY.Top)
                            .margin(Dimensions.PaddingMedium)
                    }
                }

                modifier
                    .backgrounds(
                        BlockGridBackground(this@BlockEditor, 3.dp, Dimensions.PaddingLargeSpacing),
                        SelectionRenderer(controller, scale)
                    )
                    .setupEditorControls(this@BlockEditor)
                    .onClick {
                        controller.clearSelection()
                        controller.resetAction()
                    }
                    .onPositioned {
                        controller.scrollPaneBounds.set(
                            it.leftPx,
                            it.topPx,
                            it.widthPx,
                            it.heightPx
                        )
                    }

                // Main Content Area
                ScrollPane(controller.scrollState) {
                    modifier.layout(CellLayout)
                        .padding(Dimensions.PaddingLarge.scaled())


                    modifier.onClick {
                        controller.clearSelection()
                        controller.resetAction()
                    }

                    // Render Blocks
                    rootBlocks.use().forEach { block -> renderBlockTree(block) }

                    renderSnapAnimations(snapAnimations, scale)
                }

                // --- Selection Handling on Background ---
                modifier.onDragStart {
                    if (it.pointer.isLeftButtonDown) {
                        controller.startSelection(it.screenPosition)
                        surface.triggerUpdate()
                    }
                }
                modifier.onDrag {
                    if (it.pointer.isLeftButtonDown) {
                        controller.updateSelection(it.screenPosition)
                        surface.triggerUpdate()
                    }
                }
                modifier.onDragEnd {
                    if (it.pointer.isLeftButtonReleased) {
                        controller.endSelection()
                        surface.triggerUpdate()
                    }
                }



                body()
            }
        }
    }

    fun onKeyInput(ev: KeyEvent) {
        if (ev.isPressed) {
            val isCtrl = ev.isCtrlDown
            val isShift = ev.isShiftDown

            when {
                // --- Undo / Redo ---
                isCtrl && ev.keyCode == KEY_UNDO -> {
                    if (isShift) controller.history.redo() else controller.history.undo()
                }

                isCtrl && ev.keyCode == KEY_REDO -> controller.history.redo()

                // --- Clipboard ---
                isCtrl && ev.keyCode == KEY_COPY -> controller.copySelected()
                isCtrl && ev.keyCode == KEY_CODE_CUT -> controller.cutSelected()
                isCtrl && ev.keyCode == KEY_PASTE -> controller.paste()

                // --- Selection & Manipulation ---
                isCtrl && ev.keyCode == KEY_CODE_SELECT_ALL -> controller.selectAll()
                isCtrl && ev.keyCode == KEY_DUPLICATE -> controller.duplicateSelected()
                ev.keyCode == KeyboardInput.KEY_ESC -> controller.clearSelection()
                ev.keyCode == KeyboardInput.KEY_DEL -> controller.deleteSelected()
                ev.keyCode == KeyboardInput.KEY_TAB -> {
                    controller.isBlockPanelMinimized.set(!controller.isBlockPanelMinimized.value)
                }

                ev.keyCode == KeyboardInput.KEY_CURSOR_UP -> controller.scrollState.scrollDpY(SCROLL_SPEED_Y)
                ev.keyCode == KeyboardInput.KEY_CURSOR_DOWN -> controller.scrollState.scrollDpY(-SCROLL_SPEED_Y)
                ev.keyCode == KeyboardInput.KEY_CURSOR_LEFT -> controller.scrollState.scrollDpX(SCROLL_SPEED_X)
                ev.keyCode == KeyboardInput.KEY_CURSOR_RIGHT -> controller.scrollState.scrollDpX(-SCROLL_SPEED_X)

                ev.keyCode == KeyboardInput.KEY_HOME -> controller.resetCamera()
            }
        }
    }

    private fun UiScope.updateScaleAnimation() {
        val smoothScale = animateSpringFloatAsState(
            scaleState.use(),
            stiffness = 600f,
            damping = 0.8f
        )
        scale = smoothScale.use()
    }

    // --- Block Rendering ---

    context(scope: UiScope)
    internal fun renderBlockTree(
        block: BlockModel,
        isGhost: Boolean = false,
        canDrag: Boolean = true,
    ): Unit = with(scope) {
        val currentZoom = scale
        val isRoot = rootBlocks.use().contains(block)

        Column {
            modifier.width(FitContent)
            val additionalZLayer = if (!canDrag) modifier.zLayer else 0
            modifier.configureBlockPositionAndLayer(block, isRoot, currentZoom)
            modifier.zLayer(modifier.zLayer + additionalZLayer)

            if (block.isStatement()) {
                if (controller.canAttachBefore(block) && !controller.isDragging(block)) {
                    Column(Grow.Std) {
                        GhostPlaceholder(block)
                        controller.addDropTarget(DropAction.InsertBefore(block), uiNode)
                    }
                }
            }

            Box {
                renderBlockNode(block, isGhost, canDrag)
            }

            if (block.isStatement()) {
                val next = block.next

                if (!controller.isDragging(block)) {
                    if (controller.canAttachAfter(block)) {
                        Column(Grow.Std) {
                            GhostPlaceholder(controller.draggingBlock ?: block)
                            controller.addDropTarget(DropAction.AttachAfter(block), uiNode)
                        }
                    } else if (next == null && canDrag && block.bodyRoot.parentBlock == null) {
                        Box {
                            modifier.width(Grow.Std)
                                .height(Dimensions.PaddingExtraLarge * currentZoom)
                            controller.addDropTarget(DropAction.AttachAfter(block), uiNode)
                        }
                    }
                }
                next?.let { next -> renderBlockTree(next, isGhost, canDrag) }
            }
        }
    }

    context(scope: UiScope)
    private fun renderBlockNode(block: BlockModel, isGhost: Boolean, canDrag: Boolean): Unit = with(scope) {
        Column {
            modifier.width(FitContent)
            val isHovered = remember { mutableStateOf(false) }

            Column {
                if (block.isStatement() && block.parent == null && canDrag && block.parentBlock == null) {
                    Box {
                        modifier.width(Grow.Std)
                            .height(Dimensions.PaddingHuge * scale)
                        controller.addDropTarget(DropAction.InsertBefore(block), uiNode)
                    }
                }

                BlockHeaderVisual(isHovered, block, isGhost) {
                    if (canDrag) {
                        modifier.setupDragHandler(block, controller)
                            .onClick {
                                if (it.isRightClick) {
                                    BlockContextMenu.show(it, uiNode, block)
                                } else {
                                    // Toggle Selection logic
                                    if (KeyboardInput.isCtrlDown) {
                                        controller.toggleSelection(block)
                                    } else {
                                        if (!controller.selectedBlocks.use().contains(block)) controller.selectSingle(
                                            block
                                        )
                                    }
                                }
                            }
                    }
                    modifier
                        .onEnter { isHovered.set(true) }
                        .onHover { PointerInput.cursorShape = CursorShape.HAND }
                        .onExit { isHovered.set(false) }
                }
            }

            // Body
            if (block is ContainerBlock && !block.isCollapsed.use()) {
                renderContainerBody(block, isHovered.use(), isGhost)
            }

            modifier.onPositioned {
                controller.registerBlockBounds(block, it, scale)
            }
        }
    }

    context(scope: UiScope)
    private fun <T> renderContainerBody(
        block: T,
        isParentHovered: Boolean,
        isGhost: Boolean,
    ): Unit where T : BlockModel, T : ContainerBlock = with(scope) {
        with(InputSlotScope(this@BlockEditor, scope, block, isParentHovered, isGhost)) {
            with(block) { composeBody() }
        }

        Box {
            modifier.height(Dimensions.PaddingHuge.scaled()).width(Grow.Std)

            val isUnused = block.parentsWithSelf.none { it is StartBlock } && block.root in rootBlocks
            val baseColor = block.resolveColor(isGhost, isUnused, controller.selectedBlocks.use().contains(block))

            val animatedColor by animateColorAsState(
                if (isParentHovered) baseColor else baseColor.mulRgb(0.9f),
                tween(0.2f, Easing.easeOutQuart)
            )

            val isSelected = controller.selectedBlocks.use().contains(block)
            modifier.background(ContainerFooterBackground(animatedColor, scale, block !is EndBlock, isSelected))

            if (!controller.isDragging(block)) {
                Box {
                    modifier.width(Grow.Std).alignY(AlignmentY.Bottom).height(Grow(0.45f))
                    controller.addDropTarget(DropAction.AttachAfter(block as StatementBlock), uiNode)
                }
            }
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

            val isTrigger = block is StartBlock

            if (isTrigger) {
                val modeSize = Dimensions.PaddingHuge * scale
                modifier.padding(end = modeSize)
                    .onClick {
                        if (it.position.x > uiNode.widthPx - modeSize.px && it.pointer.isLeftButtonClicked) {
                            block.toggleMode()
                        } else {
                            it.isConsumed = false
                        }
                    }
            }

            val isUnused = block.parentsWithSelf.none { it is StartBlock } && block.root in rootBlocks
            val isSelected = controller.selectedBlocks.use().contains(block)
            val baseColor = block.resolveColor(isGhost, isUnused, isSelected)

            val animatedColor by animateColorAsState(
                if (isHovered.use()) baseColor else baseColor.mulRgb(0.9f),
                tween(0.2f, Easing.easeOutQuart)
            )
            val factor by animateFloatAsState(
                if (isTrigger && block.isGlobal.use()) 1f else 0f,
                tween(0.2f, Easing.easeOutQuart)
            )

            modifier.background(
                ScratchBlockBackground(
                    block,
                    animatedColor,
                    scale,
                    isGhost,
                    isSelected,
                    factor
                )
            )

            Row(Grow.Std) {
                modifier.padding(
                    horizontal = Dimensions.PaddingMedium.scaled(),
                    vertical = Dimensions.PaddingMedium.scaled()
                ).alignY(AlignmentY.Center)

                with(InputSlotScope(this@BlockEditor, this@Row, block, isHovered.use(), isGhost)) {
                    with(block) {
                        if (block.isCollapsed.use()) {
                            composeContentCollapsed()
                        } else {
                            composeContent()
                        }
                    }
                }
            }

            if (!controller.isDragging(block) && !block.isExpression()) {
                Box {
                    modifier.width(Grow.Std).height(Grow(0.25f)).alignY(AlignmentY.Top)
                    controller.addDropTarget(DropAction.InsertBefore(block), uiNode)
                }

                if (block !is ContainerBlock || block.isCollapsed.use()) {
                    Box {
                        modifier.width(Grow.Std).height(Grow(0.25f)).alignY(AlignmentY.Bottom)
                        controller.addDropTarget(DropAction.AttachAfter(block as StatementBlock), uiNode)
                    }
                }
            }
        }
    }

    private fun UiModifier.configureBlockPositionAndLayer(block: BlockModel, isRoot: Boolean, zoom: Float) {
        val isDragging = controller.isDragging(block)

        var baseLayer = if (isDragging) Z_LAYER_DRAGGING else UiSurface.LAYER_DEFAULT
        rootBlocks.indexOf(block.root).takeUnless { it == -1 }?.let { baseLayer += it * 1000 }
        if (block.bodyRoot.parentBlock != null) baseLayer += 100
        val finalLayer = if (block.isExpression()) baseLayer + 100 else baseLayer + 100 - block.parentCount

        zLayer(finalLayer)

        if (isRoot) {
            margin(
                start = Dp.fromPx(block.positionX.use(surface) * zoom),
                top = Dp.fromPx(block.positionY.use(surface) * zoom)
            )
        }
    }

    internal fun UiScope.GhostPlaceholder(block: BlockModel) {
        Box(Grow.Std) {
            if (block.isExpression()) modifier.size(40.dp.scaled(), 30.dp.scaled())
            else {
                modifier.margin(vertical = Dimensions.PaddingSmall.scaled())
                    .height(40.dp.scaled()).width(100.dp.scaled())
            }

            val baseColor = block.resolveColor(isGhost = true, isUnused = false, isSelected = false)

            modifier.background(
                ScratchBlockBackground(
                    block,
                    baseColor,
                    scale,
                    isGhost = true,
                    isSelected = false,
                    0f
                )
            )
        }
    }

    private fun UiScope.ScaleOverlay() {
        Row {
            modifier.align(AlignmentX.End, AlignmentY.Bottom)
                .margin(Dimensions.PaddingHuge + Dimensions.PaddingMedium)
                .padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingNormal))
                .zLayer(Z_LAYER_SCROLLBAR)

            EditorButton("<", controller.history::canUndo) { controller.history.undo() }
            EditorButton(">", controller.history::canRedo) { controller.history.redo() }

            Box(Dimensions.PaddingSmall, Grow.Std) { modifier.backgroundColor(ColorTheme.UI.BackgroundAccent) }

            EditorButton("-", { scale > 0.25f }) {
                scaleState.set((floor((scale - 0.01f) / 0.05f) * 0.05f).coerceAtLeast(0.25f))
            }

            Text("${(scale * 100f).roundToInt()}%") {
                modifier.alignY(AlignmentY.Center).textColor(ColorTheme.UI.WhiteReplacement)
            }

            EditorButton("+", { scale < 3.0f }) {
                scaleState.set((ceil((scale + 0.01f) / 0.05f) * 0.05f).coerceAtMost(3.0f))
            }
        }
    }

    private fun UiScope.EditorButton(text: String, predicate: () -> Boolean = { true }, onClick: () -> Unit) {
        Text(text) {
            val isHovered by modifier.hoverable()
            val textColor by animateColorAsState(if (isHovered && predicate()) ColorTheme.UI.WhiteReplacement else ColorTheme.UI.BackgroundAccent)
            val bgColor by animateColorAsState(if (isHovered && predicate()) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundElements)

            modifier.alignY(AlignmentY.Center)
                .textColor(textColor)
                .margin(Dimensions.PaddingNormal)
                .padding(Dimensions.PaddingNormal)
                .background(RoundRectBackground(bgColor, Dimensions.PaddingNormal))
                .onClick { if(it.isLeftClick) onClick() }
        }
    }

    open fun playConnectSound() {
        UIAudio.CONNECT.play()
    }

}

// --- Extensions & Utils ---

private fun UiModifier.setupEditorControls(editor: BlockEditor): UiModifier {
    return this
        .onWheelX { editor.controller.scrollState.scrollDpX(it.pointer.scroll.x * BlockEditor.SCROLL_SPEED_X) }
        .onWheelY {
            if (KeyboardInput.isCtrlDown) {
                // Zoom Logic
                val oldScale = editor.scaleState.value
                val factor = if (it.pointer.scroll.y > 0) 1.1f else 0.9f
                val newScale = (oldScale * factor).coerceIn(0.25f, 3.0f)
                if (oldScale != newScale) editor.scaleState.set(newScale)
            } else if (KeyboardInput.isShiftDown) {
                // Horizontal Scroll via Shift + Wheel
                editor.controller.scrollState.scrollDpX(it.pointer.scroll.y * BlockEditor.SCROLL_SPEED_X)
            } else {
                // Vertical Scroll
                editor.controller.scrollState.scrollDpY(it.pointer.scroll.y * BlockEditor.SCROLL_SPEED_Y)
            }
        }
        .onDrag {
            if (it.pointer.isLeftButtonDown) return@onDrag
            val delta = it.pointer.delta
            if (delta.x != 0f) editor.controller.scrollState.scrollDpX(Dp.fromPx(-delta.x).value)
            if (delta.y != 0f) editor.controller.scrollState.scrollDpY(Dp.fromPx(-delta.y).value)
        }
}

context(editor: BlockEditor, scope: UiScope)
private fun UiModifier.setupDragHandler(block: BlockModel, controller: BlockController): UiModifier {
    return this
        .onDragStart { ev ->
            if (ev.pointer.isLeftButtonDown) controller.handleDragStart(
                block,
                ev.screenPosition - ev.position,
                ev.position
            )
        }
        .onDrag { ev -> if (ev.pointer.isLeftButtonDown) controller.handleDrag(block, ev.screenPosition) }
        .onDragEnd { ev -> if (ev.pointer.isLeftButtonReleased) controller.handleDragEnd(block) }
}

private fun UiScope.EditorScrollbars(controller: BlockController) {
    class ScrollbarColors(val trackColor: Color, val trackHoverColor: Color, val color: Color, val hoverColor: Color)

    fun ScrollbarModifier.colors(colors: ScrollbarColors) =
        colors(colors.color, colors.hoverColor, colors.trackColor, colors.trackHoverColor)

    val barColor = ScrollbarColors(
        trackColor = ColorTheme.UI.BackgroundSecondary,
        trackHoverColor = ColorTheme.UI.BackgroundElements,
        color = ColorTheme.UI.BackgroundAccent,
        hoverColor = ColorTheme.UI.WhiteReplacement
    )

    VerticalScrollbar {
        modifier
            .width(Dimensions.PaddingMedium).margin(Dimensions.PaddingMedium)
            .margin(bottom = Dimensions.PaddingHuge)
            .colors(barColor)
            .relativeBarPos(controller.scrollState.relativeBarPosY)
            .relativeBarLen(controller.scrollState.relativeBarLenY)
            .onChange { controller.scrollState.scrollRelativeY(it) }
            .zLayer(BlockEditor.Z_LAYER_SCROLLBAR)
    }
    HorizontalScrollbar {
        modifier
            .height(Dimensions.PaddingMedium).margin(Dimensions.PaddingMedium)
            .margin(end = Dimensions.PaddingHuge)
            .colors(barColor)
            .relativeBarPos(controller.scrollState.relativeBarPosX)
            .relativeBarLen(controller.scrollState.relativeBarLenX)
            .onChange { controller.scrollState.scrollRelativeX(it) }
            .zLayer(BlockEditor.Z_LAYER_SCROLLBAR)
    }
}

private fun UiScope.renderSnapAnimations(snapAnimations: MutableList<SnapAnimation>, currentZoom: Float) {
    snapAnimations.removeIf { it.isFinished }
    if (snapAnimations.isEmpty()) return

    Box {
        modifier.width(Grow.Std).height(Grow.Std)
        modifier.background(UiRenderer { node ->
            node.apply {
                val drawList = getPlainBuilder(UiSurface.LAYER_FLOATING)
                snapAnimations.forEach { anim ->
                    val p = anim.animator.updateUsing()
                    val baseScale = 10f + p * 30f
                    val actualScale = baseScale * currentZoom
                    val alpha = Easing.easeInQuart(1f - p).coerceIn(0f, 1f)

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

// Helpers for color and node finding

fun BlockModel.resolveColor(isGhost: Boolean, isUnused: Boolean, isSelected: Boolean): Color {
    return MutableColor(color).apply {
        if (isGhost) withAlpha(0.5f, this)
        if (isUnused) {
            mix(Color.LIGHT_GRAY, 0.5f, this)
            withAlpha(0.35f, this)
        }
        if (isSelected) mix(Color.WHITE, 0.2f, this)
    }
}

inline fun <reified T> UiNode.findParentOfType(filter: (T) -> Boolean = { true }): T? {
    var current: UiNode? = this

    while (current != null && !(current is T && filter(current))) current = current.parent
    return current as? T
}