package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.psi

import de.fabmax.kool.KoolContext
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.copy
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors
import ru.hollowhorizon.hollowengine.common.project.kt.CompiledFile
import ru.hollowhorizon.hollowengine.common.project.kt.InlayHintsConfiguration
import ru.hollowhorizon.hollowengine.common.project.kt.inlayhints.provideHints
import ru.hollowhorizon.hollowengine.common.project.kt.position.range
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.getElementColor
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.shouldHighlight

class PsiFileEditor(val file: CompiledFile) {
    val cursors = mutableListOf(Caret())
    val font = MsdfFont(HACK_FONT, 18f)

    fun UiScope.render() {
        val cursor = cursors.firstOrNull()?.let {
            (file.parse.viewProvider.document.getLineStartOffset(it.start.line) + it.start.column)
        } ?: -1

        val lineBuilder = mutableListOf<UiScope.() -> Unit>()
        val inlays = provideHints(file, InlayHintsConfiguration(true, true, true))

        val state = rememberListState()
        LazyColumn(state = state) {
            modifier.width(Grow.MinFit)
            val lineRenderers = mutableListOf<UiScope.() -> Unit>()

            file.parse.accept(object : PsiRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)

                    if (!element.allChildren.isEmpty || element.text.isEmpty()) return

                    val primary = getElementColor(element, file.compile)
                    val background = if (element.shouldHighlight(file.compile, file.elementAtPoint(cursor))) {
                        IdeTheme.colors.background.mix(primary, 0.35f)
                    } else null

                    val range = range(file.content, element.textRange)
                    inlays.find { it.position.line == range.start.line && it.position.character == range.start.character }
                        ?.let { hint ->
                            lineBuilder.add {
                                Box {
                                    val color = hoverColors(
                                        0.5f,
                                        IdeTheme.colors.background.mix(primary, 0.1f),
                                        IdeTheme.colors.background.mix(primary, 0.2f)
                                    )

                                    modifier.padding(horizontal = sizes.smallGap)
                                        .margin(horizontal = sizes.smallGap * 0.5f)
                                        .background(RoundRectBackground(color, sizes.smallGap))
                                        .alignY(AlignmentY.Center)
                                        .height(Grow.Std)

                                    Text(hint.label.left) {
                                        modifier.font(font.derive(14f))
                                            .alignY(AlignmentY.Center)
                                    }
                                }
                            }
                        }

                    val spans = element.text.split('\n')
                    spans.forEachIndexed { i, span ->
                        lineBuilder.add {
                            Text(span) {
                                modifier.textColor(primary)
                                    .font(font)
                                    .backgroundColor(background)
                                    .alignY(AlignmentY.Center)
                            }
                        }
                        if (i != spans.lastIndex) {
                            val copy = lineBuilder.copy()
                            lineRenderers.add {
                                Row {
                                    copy.forEach { it() }
                                }
                            }
                            lineBuilder.clear()
                        }
                    }
                }
            })
            if (lineBuilder.isNotEmpty()) {
                val copy = lineBuilder.copy()
                lineRenderers.add {
                    Row {
                        copy.forEach { it() }
                    }
                }
                lineBuilder.clear()
            }

            val lines = file.content.lines()
            val linesCount = lines.size
            val maxWidth = font.textDimensions(linesCount.toString()).width.dp

            val indentStack = mutableListOf<Int>()
            for (i in 0..<state.itemsFrom.use()) {
                val line = lines[i]
                val indentIndex = line.indexOfFirst { it != ' ' }

                indentIndex.let {
                    if (it == -1 && line.length != 0) return@let
                    while (it <= (indentStack.lastOrNull() ?: 0) && indentStack.isNotEmpty()) {
                        indentStack.pop()
                        if (line.length == 0) break
                    }
                    if (it > 0 && it != indentStack.lastOrNull()) {
                        indentStack.push(it)
                    }
                }
            }

            itemsIndexed(lineRenderers) { i, lineRenderer ->
                val line = lines[i]
                val indentIndex = line.indexOfFirst { it != ' ' }
                indentIndex.let {
                    if (it == -1 && line.length != 0) return@let
                    while (it <= (indentStack.lastOrNull() ?: 0) && indentStack.isNotEmpty()) {
                        indentStack.pop()
                        if (line.length == 0) break
                    }
                }
                val lineItem = uiNode.createChild("Line-Item", LineItem::class, lineItemFactory(i))
                lineItem.indents = (indentStack + 0).toIntArray()
                lineItem.modifier.width(Grow.Std).layout(RowLayout)
                    .padding(vertical = sizes.smallGap * 0.5f)
                with(lineItem) {

                    Box(maxWidth) {
                        modifier.margin(horizontal = sizes.smallGap).alignY(AlignmentY.Center)
                        Text((i + 1).toString()) {
                            modifier.font(font).textColor(Color("717888FF"))
                                .align(AlignmentX.End, AlignmentY.Center)
                        }
                    }

                    Box(sizes.borderWidth, Grow.Std) {
                        modifier.backgroundColor(Color("3C3C4A00")).alignY(AlignmentY.Center)
                            .margin(end = sizes.smallGap)
                    }

                    lineRenderer()
                }

                indentIndex.let {
                    if (it > 0 && it != indentStack.lastOrNull()) {
                        indentStack.push(it)
                    }
                }
            }
        }
    }

    inner class LineItem(parent: UiNode?, surface: UiSurface, val lineIndex: Int) : RowNode(parent, surface) {
        lateinit var indents: IntArray

        private val guideColor = Color("3C3C4AFF")

        override fun render(ctx: KoolContext) {
            super.render(ctx)

            val carets = cursors.filter { lineIndex == it.start.line }
            val textNode = children.getOrNull(2)
            if (indents.isNotEmpty() && textNode != null) {
                val spaceWidth = font.charWidth(' ').dp.px
                val prims = getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                val textNodeXInRow = textNode.leftPx - this.leftPx
                val guideStartX = textNodeXInRow + textNode.paddingStartPx

                for (i in indents) {
                    val x = guideStartX + i * spaceWidth - sizes.smallGap.px * 0.5f
                    prims.localRect(x, 0f, 2f, heightPx, guideColor.withAlpha(0.5f))
                }
            }
            if(textNode != null) carets.forEach { caret ->
                val textNodeXInRow = textNode.leftPx - this.leftPx

                val startOffset = file.parse.viewProvider.document.getLineStartOffset(caret.start.line)
                val caretX = textNodeXInRow + textNode.paddingStartPx + font.textDimensions(file.content.substring(startOffset, startOffset + caret.start.column)).width
                val caretPositionX = caretX

                getUiPrimitives(UiSurface.LAYER_FLOATING).localRect(
                    caretPositionX, 0f, 2f, heightPx, guideColor
                )
            }
        }
    }

    private fun lineItemFactory(lineIndex: Int): (UiNode, UiSurface) -> LineItem =
        { parent, surface -> LineItem(parent, surface, lineIndex) }


    class Caret {
        var start = Position()
        val end = start.copy()

        data class Position(val line: Int = 1, val column: Int = 4)
    }
}