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
import ru.hollowhorizon.hollowengine.client.gui.colors.PaddingLargeSpacing
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

    // State
    val scaleState = mutableStateOf(1.0f)
    var scale: Float = 1f
        private set

    // UI Components
    private val snapAnimations = mutableListOf<SnapAnimation>()
    private val creationPopup = ItemPopupMenu<Vec2f>("BlockCreationMenu")
    private val blockPopup = ItemPopupMenu<Vec2f>("BlockPopupMenu")

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
        const val SCROLL_SPEED_X = -20f
        const val SCROLL_SPEED_Y = -50f
        const val Z_LAYER_DRAGGING = 1_000_000
        const val Z_LAYER_SCROLLBAR = 100_000_000

        val C_BLOCK_SPINE_WIDTH = Dimensions.PaddingMedium
        val DROP_SENSOR_HEIGHT = Dimensions.PaddingMedium
    }

    // --- Main Layout ---

    fun UiScope.EditorLayout(body: UiScope.() -> Unit) {
        controller.update()
        updateScaleAnimation()

        Box {
            modifier
                .size(Grow.Std, Grow.Std)
                .background(BlockGridBackground(this@BlockEditor, 3.dp, Dimensions.PaddingLargeSpacing))
                .setupEditorControls(this@BlockEditor)
                .onClick {
                    if (it.isRightClick) openCreationMenu(it)
                    controller.resetAction()
                }

            // Main Content Area
            ScrollPane(controller.scrollState) {
                modifier.layout(CellLayout).padding(Dimensions.PaddingLarge.scaled())

                // Click on empty space inside scroll pane
                modifier.onClick {
                    openCreationMenu(it)
                    controller.resetAction()
                }

                // Render Blocks
                // TODO: May be add some visibility filter?
                rootBlocks.use().forEach { block -> renderBlockTree(block) }

                renderSnapAnimations(snapAnimations, scale)
            }

            EditorScrollbars(controller)
            ScaleOverlay()

            blockPopup()
            creationPopup()

            body()
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

    // --- Menus ---

    private fun UiScope.openCreationMenu(event: PointerEvent) {
        if (event.isRightClick) {
            val rootMenu = buildMenuFromProvider(provider, uiNode)
            creationPopup.show(event.screenPosition, rootMenu, Vec2f(event.screenPosition))
        }
    }

    private fun openBlockContextMenu(block: BlockModel, event: PointerEvent, uiNode: UiNode) {
        val menuItems = SubMenuItem("Блок", null) {
            item("Дублировать") { controller.duplicateBlock(block, it) }
            item("Копировать UUID") { Clipboard.copyToClipboard(block.uuid.toString()) }
            item("Удалить") { controller.removeBlock(block) }
        }
        val relativePos = (uiNode.findParentOfType<ScrollPaneNode>() ?: uiNode).toLocal(event.screenPosition)
        blockPopup.show(Vec2f(event.screenPosition), menuItems, Vec2f(relativePos))
    }

    context(scope: UiScope)
    internal fun renderBlockTree(block: BlockModel, isGhost: Boolean = false): Unit = with(scope) {
        val currentZoom = scale
        val isRoot = rootBlocks.use().contains(block)

        Column {
            modifier.width(if (isRoot) FitContent else Grow.Std)
            modifier.configureBlockPositionAndLayer(block, isRoot, currentZoom)

            Column {
                modifier.width(Grow.Std)

                // Drop target before block
                if (!block.isExpression() && controller.canAttachBefore(block) && !controller.isDragging(block)) {
                    Column(Grow.Std) {
                        GhostPlaceholder(false)
                        controller.addDropTarget(DropAction.InsertBefore(block), uiNode)
                    }
                }

                // The Block Visual + Container Body
                Box {
                    modifier.width(FitContent)
                    renderBlockNode(block, isGhost)

                    // Drop zones around the block
                    if (!controller.isDragging(block) && !block.isExpression()) {
                        renderOuterDropZones(block)
                    }
                }

                // Recursion for Next Block (Statement Chain)
                if (block is StatementBlock) {
                    if (controller.canAttachAfter(block) && !controller.isDragging(block)) {
                        GhostPlaceholder(false)
                    }
                    block.next?.let { next -> renderBlockTree(next, isGhost) }
                }
            }
        }
    }

    context(scope: UiScope)
    private fun renderBlockNode(block: BlockModel, isGhost: Boolean): Unit = with(scope) {
        Column {
            modifier.width(FitContent)
            val isHovered = remember { mutableStateOf(false) }

            // Header (The actual block visual)
            BlockHeaderVisual(isHovered, block, isGhost) {
                modifier
                    .setupDragHandler(block, controller)
                    .onClick {
                        if (it.isRightClick) openBlockContextMenu(block, it, uiNode)
                    }
                    .onEnter { isHovered.set(true) }
                    .onHover { PointerInput.cursorShape = CursorShape.HAND }
                    .onExit { isHovered.set(false) }
            }

            // Container Body (if applicable)
            if (block is ContainerBlock) {
                renderContainerBody(block, isHovered.use(), isGhost)
            }
        }
    }

    context(scope: UiScope)
    private fun <T> renderContainerBody(block: T, isParentHovered: Boolean, isGhost: Boolean): Unit where T: BlockModel, T: ContainerBlock= with(scope) {
        // Content inside the C-shape
        with(InputSlotScope(this@BlockEditor, scope, block, isParentHovered, isGhost)) {
            with(block) { composeBody() }
        }

        // Footer of the C-shape
        Box {
            modifier.height(Dimensions.PaddingHuge.scaled()).width(Grow.Std)

            val isUnused = block.parentsWithSelf.none { it is StartBlock } && block.root in rootBlocks
            val baseColor = block.resolveColor(isGhost, isUnused)

            val animatedColor by animateColorAsState(
                if (isParentHovered) baseColor else baseColor.mulRgb(0.9f),
                tween(0.2f, Easing.easeOutQuart)
            )

            modifier.background(ContainerFooterBackground(animatedColor, scale, block !is EndBlock))

            if (!controller.isDragging(block)) {
                Box {
                    modifier.width(Grow.Std).alignY(AlignmentY.Bottom).height(DROP_SENSOR_HEIGHT.scaled())
                    controller.addDropTarget(DropAction.AttachAfter(block as StatementBlock), uiNode)
                }
            }
        }
    }

    context(scope: UiScope)
    private fun renderOuterDropZones(block: BlockModel): Unit = with(scope) {
        Box {
            modifier.width(Grow.Std).height(DROP_SENSOR_HEIGHT.scaled()).alignY(AlignmentY.Top)
            controller.addDropTarget(DropAction.InsertBefore(block), uiNode)
        }

        if (block !is ContainerBlock) {
            Box {
                modifier.width(Grow.Std).height(DROP_SENSOR_HEIGHT.scaled()).alignY(AlignmentY.Bottom)
                controller.addDropTarget(DropAction.AttachAfter(block as StatementBlock), uiNode)
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

            val isUnused = block.parentsWithSelf.none { it is StartBlock } && block.root in rootBlocks
            val baseColor = block.resolveColor(isGhost, isUnused)
            val animatedColor by animateColorAsState(
                if (isHovered.use()) baseColor else baseColor.mulRgb(0.9f),
                tween(0.2f, Easing.easeOutQuart)
            )
            modifier.background(
                ScratchBlockBackground(
                    color = animatedColor,
                    zoom = scale,
                    isExpression = block.isExpression(),
                    hasNext = !block.isExpression(),
                    hasPrev = block !is StartBlock,
                    isContainerHeader = block is ContainerBlock,
                    drawInnerShadow = block.isExpression() && block.parentBlock != null,
                )
            )

            Row(Grow.Std) {
                modifier.apply(blockModifier)
                modifier.padding(horizontal = Dimensions.PaddingMedium.scaled(), vertical = Dimensions.PaddingNormal.scaled())
                    .alignY(AlignmentY.Center)

                with(InputSlotScope(this@BlockEditor, this@Row, block, isHovered.use(), isGhost)) {
                    with(block) { composeContent() }
                }
            }
        }
    }

    private fun UiModifier.configureBlockPositionAndLayer(block: BlockModel, isRoot: Boolean, zoom: Float) {
        val isDragging = controller.isDragging(block)

        // Z-Layer Logic
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

    // --- Overlays ---

    private fun UiScope.ScaleOverlay() {
        Row {
            modifier.align(AlignmentX.End, AlignmentY.Bottom)
                .margin(Dimensions.PaddingHuge + Dimensions.PaddingMedium)
                .padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingNormal))
                .zLayer(Z_LAYER_SCROLLBAR)

            ScaleButton("-") { scaleState.set((floor((scale - 0.01f) / 0.05f) * 0.05f).coerceAtLeast(0.25f)) }

            Text("${(scale * 100f).roundToInt()}%") {
                modifier.alignY(AlignmentY.Center).textColor(ColorTheme.UI.WhiteReplacement)
            }

            ScaleButton("+") { scaleState.set((ceil((scale + 0.01f) / 0.05f) * 0.05f).coerceAtMost(3.0f)) }
        }
    }

    private fun UiScope.ScaleButton(text: String, onClick: () -> Unit) {
        Text(text) {
            val isHovered by modifier.hoverable()
            val textColor by animateColorAsState(if (isHovered) ColorTheme.UI.WhiteReplacement else ColorTheme.UI.BackgroundAccent)
            val bgColor by animateColorAsState(if (isHovered) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundElements)

            modifier.alignY(AlignmentY.Center)
                .textColor(textColor)
                .margin(Dimensions.PaddingNormal)
                .padding(Dimensions.PaddingNormal)
                .background(RoundRectBackground(bgColor, Dimensions.PaddingNormal))
                .onClick { onClick() }
        }
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
                editor.controller.scrollState.scrollDpX(it.pointer.scroll.x * BlockEditor.SCROLL_SPEED_X)
            } else {
                // Vertical Scroll
                editor.controller.scrollState.scrollDpY(it.pointer.scroll.y * BlockEditor.SCROLL_SPEED_Y)
            }
        }
        .onDrag {
            val delta = it.pointer.delta
            if (delta.x != 0f) editor.controller.scrollState.scrollDpX(Dp.fromPx(-delta.x).value)
            if (delta.y != 0f) editor.controller.scrollState.scrollDpY(Dp.fromPx(-delta.y).value)
        }
}

context(editor: BlockEditor, scope: UiScope)
private fun UiModifier.setupDragHandler(block: BlockModel, controller: BlockController): UiModifier {
    return this
        .onDragStart { ev -> controller.handleDragStart(block, ev) }
        .onDrag { ev -> controller.handleDrag(block, ev) }
        .onDragEnd { controller.handleDragEnd(block) }
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

// Helpers for color and node finding

private fun BlockModel.resolveColor(isGhost: Boolean, isUnused: Boolean): Color {
    return if (isGhost) this.color.withAlpha(0.5f)
    else if (isUnused) this.color.mix(Color.LIGHT_GRAY, 0.5f).withAlpha(0.35f)
    else this.color
}

inline fun <reified T> UiNode.findParentOfType(filter: (T) -> Boolean = { true }): T? {
    var current: UiNode? = this

    while (current != null && !(current is T && filter(current))) current = current.parent
    return current as? T
}