package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.isExpression
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.parentsWithSelf
import ru.hollowhorizon.hollowengine.common.codeblocks.root
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.InputValue

class InputSlotScope(
    private val editor: BlockEditor, uiScope: UiScope,
    val parentBlock: BlockModel,
    val isHovered: Boolean,
    val isGhost: Boolean,
) : UiScope by uiScope {

    fun Dp.scaled() = this * editor.scale

    val boldFont = editor.getFont(Dimensions.FontNormal, isBold = true)
    val font = editor.getFont(Dimensions.FontNormal - 1f)

    fun TextModifier.bold() = font(boldFont)
    fun TextModifier.regular() = font(this@InputSlotScope.font)

    fun notifyChanged() { editor.notifyChanged() }

    fun UiScope.InputSlot(name: String, type: ExpressionType) = with(editor) {
        parentBlock.inputTypes[name] = type
        val attached = parentBlock.inputs[name]
        val isTargeted = editor.controller.canAttachToInput(parentBlock, name) && !editor.controller.isStatementSlot

        Box {
            modifier.align(AlignmentX.End, AlignmentY.Center).margin(horizontal = Dimensions.PaddingMedium.scaled())

            if (attached != null) {
                if (editor.controller.draggingBlock == attached) EmptySlotVisual(isTargeted)
                else {
                    renderBlockTree(attached)
                    if (isTargeted) modifier.border(RectBorder(Color.WHITE, 2.dp.scaled()))
                }
            } else {
                editor.controller.addDropTarget(DropAction.AttachToInput(parentBlock, name, false), uiNode)

                val dragBlock = editor.controller.draggingBlock
                if (isTargeted && dragBlock?.isExpression() == true) GhostPlaceholder(dragBlock)
                else EmptySlotVisual(false)
            }
        }
    }

    fun UiScope.InputSlot(input: InputValue<*>) = InputSlot(input.name, input.type)

    /**
     * Да, костыль, зато какой :)
     */
    fun UiScope.InputSlotList(baseName: String, type: ExpressionType) {
        val usedIndices = parentBlock.inputs.keys
            .filter { it.startsWith("${baseName}_") }
            .mapNotNull { it.substringAfterLast("_").toIntOrNull() }
            .sorted()

        val maxIndex = usedIndices.lastOrNull() ?: -1

        Row {
            modifier.alignY(AlignmentY.Center)

            for (i in 0..maxIndex + 1) {
                val slotName = "${baseName}_$i"
                InputSlot(slotName, type)
            }
        }
    }

    fun UiScope.BodySlot(name: String) = with(editor) {
        val attached = parentBlock.inputs[name]
        val isTargeted = editor.controller.canAttachToInput(parentBlock, name) && editor.controller.isStatementSlot

        Row {
            modifier.width(Grow.Std)
            val isUnused = parentBlock.parentsWithSelf.none { it is StartBlock } && parentBlock.root in rootBlocks
            val isSelected = controller.selectedBlocks.use().contains(parentBlock)

            val baseColor = parentBlock.resolveColor(isGhost, isUnused, isSelected)
            val animatedColor by animateColorAsState(
                if (isHovered) baseColor else baseColor.mulRgb(0.9f),
                tween(0.2f, Easing.easeOutQuart)
            )
            val factor by animateFloatAsState(
                if (parentBlock is StartBlock && parentBlock.mode.use().isGlobal()) 1f else 0f,
                tween(0.2f, Easing.easeOutQuart)
            )
            Box {

                modifier.width(BlockEditor.C_BLOCK_SPINE_WIDTH.scaled())
                    .height(Grow.Std)
                    .zLayer(modifier.zLayer + 1)
                    .background(SpineBackground(animatedColor, scale, isSelected=isSelected))
            }

            Column {
                modifier.width(Grow.Std)
                Box {
                    if (attached == null) {
                        modifier.height(30.dp.scaled()).width(100.dp.scaled())
                        if (isTargeted) modifier.background(
                            ScratchBlockBackground(
                                controller.draggingBlock ?: parentBlock,
                                controller.draggingBlock?.resolveColor(true, false, false) ?: animatedColor,
                                scale,
                                isGhost,
                                isSelected,
                                factor
                            )
                        )
                    }
                    editor.controller.addDropTarget(DropAction.AttachToInput(parentBlock, name, true), uiNode)
                }

                if (attached != null) {
                    renderBlockTree(attached, isGhost)
                }
            }
        }
    }

    fun UiScope.SectionSeparator(label: String) = with(editor) {
        val isUnused = parentBlock.parentsWithSelf.none { it is StartBlock } && parentBlock.root in rootBlocks
        val isSelected = controller.selectedBlocks.use().contains(parentBlock)

        val baseColor = parentBlock.resolveColor(isGhost, isUnused, isSelected)
        val animatedColor by animateColorAsState(
            if (isHovered) baseColor else baseColor.mulRgb(0.9f),
            tween(0.2f, Easing.easeOutQuart)
        )
        Row {
            modifier.width(Grow.Std).height(FitContent)
            Box {
                modifier.width(Grow.Std).height(Dimensions.PaddingExtraLarge.scaled())

                modifier.background(ContainerMiddleBackground(animatedColor, scale, isSelected=isSelected))
                Text(label) {
                    modifier.alignY(AlignmentY.Center)
                        .margin(start = (BlockEditor.C_BLOCK_SPINE_WIDTH + Dimensions.PaddingMedium) * scale)
                        .textColor(Color.WHITE).bold()
                }
            }
        }
    }

    private fun UiScope.EmptySlotVisual(highlight: Boolean) = with(editor) {
        Box {
            modifier.size(40.dp.scaled(), 30.dp.scaled())

            val color = parentBlock.resolveColor(false, parentBlock.parentsWithSelf.none { it is StartBlock }, controller.selectedBlocks.contains(parentBlock))

            modifier.background(SlotBackground(color.mix(Color.BLACK, 0.3f), highlight, scale))
            if (highlight) modifier.border(RectBorder(Color.WHITE, 2.dp.scaled()))
        }
    }
}