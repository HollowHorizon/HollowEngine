package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.isExpression
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.InputValue

class InputSlotScope(
    private val editor: BlockEditor, uiScope: UiScope,
    val parentBlock: BlockModel,
    val isHovered: Boolean,
    val isGhost: Boolean,
) : UiScope by uiScope {
    val boldFont = MsdfFont(
        (IdeTheme.sizes.normalText as MsdfFont).data,
        16f,
        MsdfFont.Companion.ITALIC_NONE,
        0.2f
    )
    val font = MsdfFont((IdeTheme.sizes.normalText as MsdfFont).data, 15f, MsdfFont.Companion.ITALIC_NONE)

    fun TextModifier.bold() = font(boldFont)
    fun TextModifier.regular() = font(boldFont)

    fun notifyChanged() {
        editor.notifyChanged()
    }

    fun UiScope.InputSlot(name: String, type: ExpressionType) = with(editor) {
        parentBlock.inputTypes[name] = type
        val attached = parentBlock.inputs[name]
        val isTargeted = editor.controller.canAttachToInput(parentBlock, name) && !editor.controller.isStatementSlot

        Box {
            modifier.align(AlignmentX.End, AlignmentY.Center).margin(horizontal = sizes.gap)

            if (attached != null) {
                if (editor.controller.draggingBlock == attached) EmptySlotVisual(isTargeted)
                else {
                    renderBlockRecursively(attached)
                    if (isTargeted) modifier.border(RectBorder(Color.Companion.WHITE, 2.dp))
                }
            } else {
                editor.controller.addDropTarget(DropAction.AttachToInput(parentBlock, name, false), uiNode)

                if (isTargeted && editor.controller.draggingBlock?.isExpression() == true) GhostPlaceholder(true)
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
            modifier.width(Grow.Companion.Std)

            val bgColor = if (isGhost) parentBlock.color.withAlpha(0.5f) else parentBlock.color
            val color by animateColorAsState(
                if (isHovered) bgColor else bgColor.mulRgb(0.9f),
                tween(0.2f, Easing.quadRev)
            )
            Box {
                modifier
                    .width(Dp.Companion.fromPx(BlockEditor.Companion.C_BLOCK_SPINE_WIDTH) - sizes.smallGap * 0.5f)
                    .height(Grow.Companion.Std)
                    .background(SpineBackground(color))
            }

            Box { modifier.size(sizes.smallGap * 0.5f, Grow.Companion.Std) }

            Column {
                modifier.width(Grow.Companion.Std)

                Box {
                    if (attached == null) {
                        modifier.height(30.dp).width(100.dp)
                        if (isTargeted) modifier.background(
                            ScratchBlockBackground(
                                Color.Companion.WHITE.withAlpha(0.2f),
                                isExpression = false,
                                hasNext = true,
                                drawInnerShadow = true
                            )
                        )
                    } else {
                        modifier.height(sizes.smallGap).width(Grow.Companion.Std)
                    }
                    editor.controller.addDropTarget(DropAction.AttachToInput(parentBlock, name, true), uiNode)
                }

                if (attached != null) {
                    if (editor.controller.draggingBlock == attached) {
                        Box {
                            modifier.size(50.dp, 20.dp)
                                .background(RectBackground(Color.Companion.WHITE.withAlpha(0.1f)))
                        }
                    } else {
                        renderBlockRecursively(attached, isGhost)
                    }
                }

                Box {
                    modifier.height(sizes.smallGap).width(Grow.Companion.Std)
                }
            }
        }
    }

    fun UiScope.SectionSeparator(label: String) {
        val bgColor = if (isGhost) parentBlock.color.withAlpha(0.5f) else parentBlock.color
        val color by animateColorAsState(
            if (isHovered) bgColor else bgColor.mulRgb(0.9f),
            tween(0.2f, Easing.quadRev)
        )
        Row {
            modifier.width(Grow.Companion.Std).height(FitContent)
            Box {
                modifier.width(Grow.Companion.Std).height(30.dp)
                modifier.background(ContainerMiddleBackground(color))
                Text(label) {
                    modifier.alignY(AlignmentY.Center)
                        .margin(start = Dp.Companion.fromPx(BlockEditor.Companion.C_BLOCK_SPINE_WIDTH + 10f))
                        .textColor(Color.Companion.WHITE).bold()
                }
            }
        }
    }

    private fun UiScope.EmptySlotVisual(highlight: Boolean) {
        Box {
            modifier.size(40.dp, 30.dp)
            modifier.background(SlotBackground(parentBlock.color.mix(Color.Companion.BLACK, 0.3f), highlight))
            if (highlight) modifier.border(RectBorder(Color.Companion.WHITE, 2.dp))
        }
    }
}