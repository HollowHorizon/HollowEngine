package ru.hollowhorizon.hollowengine.client.gui.markdown

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import org.intellij.markdown.MarkdownElementTypes.ATX_1
import org.intellij.markdown.MarkdownElementTypes.ATX_2
import org.intellij.markdown.MarkdownElementTypes.ATX_3
import org.intellij.markdown.MarkdownElementTypes.ATX_4
import org.intellij.markdown.MarkdownElementTypes.ATX_5
import org.intellij.markdown.MarkdownElementTypes.ATX_6
import org.intellij.markdown.MarkdownElementTypes.CODE_BLOCK
import org.intellij.markdown.MarkdownElementTypes.CODE_FENCE
import org.intellij.markdown.MarkdownElementTypes.IMAGE
import org.intellij.markdown.MarkdownElementTypes.ORDERED_LIST
import org.intellij.markdown.MarkdownElementTypes.UNORDERED_LIST
import org.intellij.markdown.MarkdownTokenTypes.Companion.BLOCK_QUOTE
import org.intellij.markdown.MarkdownTokenTypes.Companion.EOL
import org.intellij.markdown.MarkdownTokenTypes.Companion.HORIZONTAL_RULE
import org.intellij.markdown.MarkdownTokenTypes.Companion.TEXT
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes.TABLE
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme.Fonts.MONOCRAFT
import ru.hollowhorizon.hollowengine.client.gui.markdown.components.*


data class MarkdownStyle(
    val h1Font: MsdfFont = MsdfFont(MONOCRAFT, sizePts = 34f),
    val h2Font: MsdfFont = MsdfFont(MONOCRAFT, sizePts = 30f),
    val h3Font: MsdfFont = MsdfFont(MONOCRAFT, sizePts = 26f),
    val h4Font: MsdfFont = MsdfFont(MONOCRAFT, sizePts = 22f),
    val h5Font: MsdfFont = MsdfFont(MONOCRAFT, sizePts = 20f),
    val h6Font: MsdfFont = MsdfFont(MONOCRAFT, sizePts = 18f),

    val bodyFont: MsdfFont = MsdfFont(MONOCRAFT, sizePts = 16f),
    val codeFont: MsdfFont = MsdfFont(MONOCRAFT, sizePts = 14f),
    val italicFont: MsdfFont = MsdfFont(MONOCRAFT, sizePts = 16f, italic = MsdfFont.ITALIC_STD),
    val boldFont: MsdfFont = MsdfFont(MONOCRAFT, sizePts = 16f, weight = MsdfFont.WEIGHT_EXTRA_BOLD),

    val textColor: Color = Color.WHITE,
    val codeBackgroundColor: Color = Color.WHITE.withAlpha(0.1f),
    val linkColor: Color = Color("4D90FE"),

    val tableBorderColor: Color = Color.WHITE.withAlpha(0.3f),
    val tableHeaderBgColor: Color = Color.WHITE.withAlpha(0.2f),
    val tableEvenRowColor: Color? = null,
    val tableOddRowColor: Color? = Color.WHITE.withAlpha(0.05f),
)

class MarkdownEditorHandler(initialText: String = "") : TextLineProvider {
    val style: MarkdownStyle = MarkdownStyle()
    var lines = initialText.toLines(style.bodyFont)
    var isEditing = mutableStateOf(true)

    val text: String
        get() = lines.joinToString(separator = "\n") { it.text }

    fun toggleMode() {
        isEditing.set(!isEditing.value)
    }

    override val size: Int
        get() = lines.size

    override fun get(index: Int): TextLine = lines[index]
}

fun UiScope.MarkdownEditor(
    handler: MarkdownEditorHandler,
    width: Dimension = Grow.Std,
    height: Dimension = Grow.Std,
) {
    // TODO: Тут лучше сделать фичу как в той же IDEA - справа вверху сделать панель с 3 режимами: Только редактор, Редактор + Предпросмотр, Только предпросмотр
    Column(width, height) {
        Row(width = Grow.Std, height = Dp(40f)) {
            modifier.padding(bottom = Dp(8f))

            Button(if (handler.isEditing.use()) "Preview" else "Edit") {
                modifier
                    .onClick { handler.toggleMode() }
                    .alignY(AlignmentY.Center)
            }
        }

        Box(width = Grow.Std, height = Grow.Std) {
            if (handler.isEditing.use()) {
                // TODO: Лучше тут использовать ту же штуку, что и в ScriptFileData, там поле ввода текста обрабатывает горячие клавиши +
                //  можно будет добавить подсветку синтаксиса и автоматические скобки
                TextArea(
                    lineProvider = handler,
                    width = Grow.Std,
                    height = Grow.Std
                ) {
                    modifier
                        .editorHandler(DefaultTextEditorHandler(handler.lines))
                        .backgroundColor(Color.BLACK.withAlpha(0.3f))
                    installDefaultSelectionHandler()
                }
            } else {
                val width = remember(0f)

                ScrollArea(containerModifier = {
                    it.background(null).width(Grow.Std)
                    it.onMeasured {
                        width.set(it.innerWidthPx)
                    }
                }) {
                    modifier.width(Grow(1f, max = Dp.fromPx(width.use())))

                    MarkdownViewer(handler.text, handler.style) {

                    }
                }
            }
        }
    }
}

private fun String.toLines(font: MsdfFont): MutableList<TextLine> {
    val lines = this.split('\n')
    val attributes = TextAttributes(font, Color.WHITE)
    return lines.map { TextLine(listOf(it to attributes)) }.toMutableList()
}

fun <T, V> UiScope.rememberTarget(vararg targets: T, generator: () -> V): V {
    val value = remember { mutableStateOf(generator()) }
    LaunchedEffect(*targets) {
        value.set(generator())
    }
    return value.use()
}

fun UiScope.MarkdownViewer(
    markdownSource: String,
    style: MarkdownStyle,
    block: UiScope.() -> Unit = {},
) {
    val parsedTree = rememberTarget(markdownSource) {
        val parser = MarkdownParser(GFMFlavourDescriptor())
        parser.buildMarkdownTreeFromString(markdownSource)
    }

    Box(Grow.Std, FitContent) {
        block()

        val contentWidth = remember(0f)

        modifier.onMeasured {
            contentWidth.set(it.innerWidthPx)
        }

        Column(Grow.Std, Grow.MinFit) {
            MarkdownElement(parsedTree, markdownSource, style, contentWidth.value)
        }
    }
}

fun UiScope.MarkdownElement(
    node: ASTNode,
    content: String,
    style: MarkdownStyle,
    availableWidth: Float,
    includeSpacer: Boolean = true,
) {
    if (includeSpacer) Box { modifier.height(MarkdownPadding.block) }

    when (node.type) {
        TEXT -> MarkdownText(node, content, style)
        EOL -> {}
        CODE_FENCE -> MarkdownCodeFence(content, node, style)
        CODE_BLOCK -> MarkdownCodeBlock(content, node, style)
        ATX_1 -> MarkdownHeader(node, content, style.h1Font, style.textColor, availableWidth)
        ATX_2 -> MarkdownHeader(node, content, style.h2Font, style.textColor, availableWidth)
        ATX_3 -> MarkdownHeader(node, content, style.h3Font, style.textColor, availableWidth)
        ATX_4 -> MarkdownHeader(node, content, style.h4Font, style.textColor, availableWidth)
        ATX_5 -> MarkdownHeader(node, content, style.h5Font, style.textColor, availableWidth)
        ATX_6 -> MarkdownHeader(node, content, style.h6Font, style.textColor, availableWidth)
        BLOCK_QUOTE -> MarkdownBlockQuote(content, node.parent ?: return, style, availableWidth)
        ORDERED_LIST -> MarkdownOrderedList(content, node, style, availableWidth)
        UNORDERED_LIST -> MarkdownBulletList(content, node, style, availableWidth)
        IMAGE -> MarkdownImage(node, content, style)
        HORIZONTAL_RULE -> {
            Box {
                modifier.width(Grow.Std).height(MarkdownDimens.dividerThickness)
                    .backgroundColor(ColorTheme.UI.WhiteReplacement)
            }
        }

        TABLE -> MarkdownTable(node, content, style)
        else -> {
            node.children.forEach { child ->
                MarkdownElement(child, content, style, availableWidth, includeSpacer)
            }
        }
    }
}