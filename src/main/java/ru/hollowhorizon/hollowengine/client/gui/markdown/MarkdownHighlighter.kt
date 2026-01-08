package ru.hollowhorizon.hollowengine.client.gui.markdown

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme.Fonts.MONOCRAFT


data class MarkdownStyle(
    // TODO: Там AxelEncore скидывал готовые размеры и подходящие параметры для цветов и шрифтов, надо с ними свериться
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
                ScrollPane(remember { ScrollState() }) {
                    modifier.width(Grow.Std).height(Grow.Std)

                    MarkdownViewer(handler.text, handler.style) {
                        modifier.width(Grow.Std).height(Grow.MinFit)
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
        val parser = MarkdownParser(GFMFlavourDescriptor()) // CommonMark не поддерживает таблицы :>
        parser.buildMarkdownTreeFromString(markdownSource)
    }

    Box {
        block()

        // В теории это работает так: создаётся contentWidth, при первом кадре рассчитывает доступные размеры в onMeasured, и уже во 2 кадре использует их для переноса строк
        val contentWidth = remember(0f)

        modifier.onMeasured {
            if (it.contentWidthPx != contentWidth.value) {
                contentWidth.set(it.contentWidthPx)
            }
        }

        Column(width = Grow.Std, height = Grow.MinFit) {
            renderMarkdownNode(parsedTree, markdownSource, style, contentWidth.value)
        }
    }
}

private fun UiScope.renderMarkdownNode(
    node: ASTNode,
    source: String,
    style: MarkdownStyle,
    availableWidth: Float,
) {
    node.children.forEach { child ->
        when (child.type) {
            MarkdownElementTypes.PARAGRAPH -> {
                MarkdownParagraph(child, source, style)
            }

            MarkdownElementTypes.ATX_1 -> {
                MarkdownHeader(child, source, style.h1Font, style.textColor, availableWidth)
            }

            MarkdownElementTypes.ATX_2 -> {
                MarkdownHeader(child, source, style.h2Font, style.textColor, availableWidth)
            }

            MarkdownElementTypes.ATX_3 -> {
                MarkdownHeader(child, source, style.h3Font, style.textColor, availableWidth)
            }

            MarkdownElementTypes.ATX_4 -> {
                MarkdownHeader(child, source, style.h4Font, style.textColor, availableWidth)
            }

            MarkdownElementTypes.ATX_5 -> {
                MarkdownHeader(child, source, style.h5Font, style.textColor, availableWidth)
            }

            MarkdownElementTypes.ATX_6 -> {
                MarkdownHeader(child, source, style.h6Font, style.textColor, availableWidth)
            }


            MarkdownElementTypes.CODE_BLOCK, MarkdownElementTypes.CODE_FENCE, MarkdownTokenTypes.CODE_FENCE_CONTENT -> {
                MarkdownCodeBlock(child, source, style)
            }

            MarkdownElementTypes.IMAGE -> {
                MarkdownImage(child, source, style)
            }

            GFMElementTypes.TABLE -> MarkdownTable(child, source, style)

            else -> {
                renderMarkdownNode(child, source, style, availableWidth)
            }
        }
    }
}