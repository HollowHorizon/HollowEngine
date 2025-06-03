package ru.hollowhorizon.hollowengine.ksp.file

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.ScriptColorizer
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.SyntaxHighlight

@Serializable
@SerialName("File")
class DocFile(val title: String, val description: String) : Composable {
    val declarations = mutableListOf<DocDeclaration>()

    override fun UiScope.compose() {
        Column(Grow.Std) {
            modifier.padding(sizes.smallGap)

            Text(title) {
                modifier.alignX(AlignmentX.Center)
                    .font(MsdfFont(HACK_FONT, 30f))
            }

            divider(thickness = sizes.borderWidth)

            Text(description.trimIndent()) {
                modifier.padding(horizontal = sizes.smallGap)
                    .width(Grow.Std).isWrapText(true)
            }

            divider(thickness = sizes.borderWidth)

            declarations.forEach {
                Box(Grow.Std) {
                    modifier.width(Grow.Std)
                        .background(RoundRectBackground(colors.backgroundVariant, sizes.gap))
                        .border(RoundRectBorder(colors.secondaryVariant, sizes.gap, sizes.borderWidth))
                        .padding(sizes.smallGap)
                        .margin(vertical = sizes.smallGap)

                    Column(Grow.Std) {
                        it()
                    }
                }
            }
        }
    }
}

@Serializable
@SerialName("Declaration")
sealed class DocDeclaration : Composable {
    @SerialName("doc_text")
    var docText = mutableListOf<String>()

    data class DocTextParts(
        val mainDescription: List<String>,
        val paramDescriptions: Map<String, String>,
        val returnDescription: String?,
        val templates: List<String>, // Поле для хранения шаблонов
    ) {
        fun extractCodeFromTemplate(): Pair<String, String>? {
            val regex = """```([\w.]+)\R([\s\S]*?)```""".toRegex()
            val match = regex.find(templates.joinToString{it+"\n"})
            return match?.let {
                val language = it.groups[1]?.value ?: ""
                val code = it.groups[2]?.value?.trim() ?: ""
                language to code
            }
        }
    }

    fun parseDocText(): DocTextParts {
        val mainDescription = mutableListOf<String>()
        val paramDescriptions = mutableMapOf<String, String>()
        var returnDescription: String? = null
        val templates = mutableListOf<String>() // Список шаблонов
        var currentTag: String? = null
        var templateBuilder: StringBuilder? = null // Для сборки многострочного шаблона

        for (line in docText) {
            when {
                line.startsWith("@param") -> {
                    templateBuilder?.let {
                        templates.add(it.toString().trim())
                        templateBuilder = null
                    }
                    val parts = line.substringAfter("@param").trim().split(" ", limit = 2)
                    if (parts.size == 2) {
                        paramDescriptions[parts[0]] = parts[1]
                        currentTag = "param_${parts[0]}"
                    } else {
                        currentTag = null
                    }
                }

                line.startsWith("@return") -> {
                    templateBuilder?.let {
                        templates.add(it.toString().trim())
                        templateBuilder = null
                    }
                    returnDescription = line.substringAfter("@return").trim()
                    currentTag = "return"
                }

                line.startsWith("@template") -> {
                    templateBuilder?.let {
                        templates.add(it.toString().trim())
                    }
                    templateBuilder = StringBuilder(line.substringAfter("@template").trim())
                    currentTag = "template"
                }

                line.startsWith("@") -> {
                    templateBuilder?.let {
                        templates.add(it.toString().trim())
                        templateBuilder = null
                    }
                    currentTag = null
                }

                else -> {
                    when (currentTag) {
                        null -> mainDescription.add(line)
                        "return" -> returnDescription = (returnDescription ?: "") + " $line"
                        "template" -> templateBuilder?.append("\n")?.append(line)
                        else -> {
                            val paramName = currentTag.substringAfter("param_")
                            paramDescriptions[paramName] = (paramDescriptions[paramName] ?: "") + " $line"
                        }
                    }
                }
            }
        }

        // Добавляем последний шаблон, если он есть
        templateBuilder?.let {
            templates.add(it.toString().trim())
        }

        return DocTextParts(mainDescription, paramDescriptions, returnDescription, templates)
    }

    @Serializable
    @SerialName("Class")
    class DocClass(val name: String, private val pkg: String) : DocDeclaration() {
        private val declarations = mutableListOf<DocDeclaration>()

        override fun UiScope.compose() {
            val isMethodsExpanded = remember { mutableStateOf(false) }
            val isFieldsExpanded = remember { mutableStateOf(false) }
            val parts = parseDocText()

            Column {
                modifier.padding(sizes.smallGap)

                Row {
                    Text("Класс: $name") {
                        modifier.font(MsdfFont(HACK_FONT, 24f))
                            .textColor(SyntaxHighlight.EXTENSION_RECEIVER)
                    }
                    Text(pkg) {
                        modifier.padding(sizes.smallGap)
                            .background(RoundRectBackground(colors.backgroundMid, sizes.gap))
                    }
                }

                if (parts.mainDescription.isNotEmpty()) {
                    Text("Описание:") {
                        modifier.font(boldFont).textColor(textColor)
                    }
                    for (line in parts.mainDescription) {
                        Text(line) {
                            modifier.textColor(textColor)
                                .width(Grow.Std).isWrapText(true)
                        }
                    }
                }

                divider(thickness = sizes.borderWidth)

                // Секция методов
                Row {
                    modifier.onClick { isMethodsExpanded.value = !isMethodsExpanded.value }
                        .background(RectBackground(Color.LIGHT_GRAY))
                        .padding(sizes.smallGap)
                    Text("Методы") {
                        modifier.textColor(textColor)
                    }
                    Arrow(if (isMethodsExpanded.use()) ArrowScope.ROTATION_DOWN else ArrowScope.ROTATION_LEFT) {
                        modifier.size(sizes.largeGap, sizes.largeGap)
                            .alignY(AlignmentY.Center)
                    }
                }
                if (isMethodsExpanded.use()) {
                    Column {
                        declarations.filterIsInstance<DocMethod>().forEach { it() }
                    }
                }

                // Секция полей
                Row {
                    modifier.onClick { isFieldsExpanded.value = !isFieldsExpanded.value }
                        .background(RectBackground(Color.LIGHT_GRAY))
                        .padding(sizes.smallGap)
                    Text("Поля") {
                        modifier.textColor(textColor)
                    }
                    Arrow(if (isFieldsExpanded.use()) ArrowScope.ROTATION_DOWN else ArrowScope.ROTATION_LEFT) {
                        modifier.size(sizes.largeGap, sizes.largeGap)
                            .alignY(AlignmentY.Center)
                    }
                }
                if (isFieldsExpanded.value) {
                    Column {
                        declarations.filterIsInstance<DocField>().forEach { it() }
                    }
                }
            }
        }
    }

    @Serializable
    @SerialName("Method")
    class DocMethod(
        val name: String,
        @SerialName("extension_receiver")
        val extReceiver: DocType? = null,
        val valueParameters: List<DocParameter>,
        val returnType: DocType,
    ) :
        DocDeclaration() {
        override fun UiScope.compose() {
            val parts = parseDocText()
            Column(Grow.Std) {
                modifier.padding(sizes.smallGap)

                // Сигнатура метода
                Row(Grow.Std) {
                    modifier.background(RoundRectBackground(colors.backgroundMid.mulRgb(0.75f), sizes.smallGap))
                        .border(RoundRectBorder(colors.secondaryVariant, sizes.smallGap, sizes.borderWidth))
                        .padding(sizes.smallGap)

                    Text("Функция") {
                        modifier.font(boldFont.derive(20f)).textColor(SyntaxHighlight.KEYWORD)
                            .background(
                                RoundRectBackground(
                                    IdeTheme.colors.background.mix(
                                        SyntaxHighlight.KEYWORD,
                                        0.35f
                                    ), sizes.smallGap
                                )
                            )
                            .padding(sizes.smallGap * 0.5f)
                            .margin(end = sizes.smallGap)
                            .alignY(AlignmentY.Center)
                    }
                    if (extReceiver != null) {
                        Row {
                            modifier.alignY(AlignmentY.Center)
                            Text(extReceiver.type) {
                                modifier.font(codeFont).textColor(SyntaxHighlight.NAME_REFERENCE)
                                    .alignY(AlignmentY.Center)
                            }
                            Text(".") {
                                modifier.font(codeFont).textColor(textColor)
                                    .alignY(AlignmentY.Center)
                            }
                        }
                    }
                    Text(name) {
                        modifier.font(codeFont).textColor(SyntaxHighlight.EXTENSION_RECEIVER)
                            .alignY(AlignmentY.Center)
                    }
                    Text("(") {
                        modifier.font(codeFont).textColor(textColor)
                            .alignY(AlignmentY.Center)
                    }
                    valueParameters.forEachIndexed { i, param ->
                        if (i > 0) Text(", ") {
                            modifier.font(codeFont).textColor(textColor)
                                .alignY(AlignmentY.Center)
                        }
                        Text(param.name) {
                            modifier.font(codeFont).textColor(SyntaxHighlight.PROPERTY_IDENTIFIER)
                                .alignY(AlignmentY.Center)
                        }
                        Text(": ") {
                            modifier.font(codeFont).textColor(textColor)
                                .alignY(AlignmentY.Center)
                        }
                        Text(param.type.type) {
                            modifier.font(codeFont).textColor(SyntaxHighlight.NAME_REFERENCE)
                                .alignY(AlignmentY.Center)
                        }
                    }
                    Text("): ") {
                        modifier.font(codeFont).textColor(textColor)
                            .alignY(AlignmentY.Center)
                    }
                    Text(returnType.type) {
                        modifier.font(codeFont).textColor(SyntaxHighlight.NAME_REFERENCE)
                            .alignY(AlignmentY.Center)
                    }
                    divider(marginBottom = sizes.largeGap, marginTop = sizes.smallGap)
                }

                if (parts.paramDescriptions.isNotEmpty()) {
                    Text("Параметры:") {
                        modifier.font(boldFont).textColor(textColor)
                    }
                    for ((paramName, desc) in parts.paramDescriptions) {
                        Row(Grow.Std) {
                            modifier.padding(sizes.smallGap * 0.5f)
                            Text("◦") {
                                modifier.font(codeFont).textColor(textColor)
                                    .alignY(AlignmentY.Center)
                                    .margin(end = sizes.smallGap)
                            }
                            Text(paramName) {
                                modifier.font(codeFont).textColor(SyntaxHighlight.PROPERTY_IDENTIFIER)
                                    .alignY(AlignmentY.Center)
                            }
                            Text(": $desc") {
                                modifier.font(codeFont).textColor(textColor)
                                    .width(Grow.Std).isWrapText(true)
                                    .alignY(AlignmentY.Center)
                            }
                        }
                    }
                    divider(marginBottom = sizes.gap, marginTop = sizes.gap)

                }
                if (parts.returnDescription != null) {
                    Text("Возвращаемое значение:") {
                        modifier.font(boldFont).textColor(textColor)
                    }
                    Text(parts.returnDescription) {
                        modifier.textColor(textColor)
                            .width(Grow.Std).isWrapText(true)
                    }
                    divider(marginBottom = sizes.gap, marginTop = sizes.gap)
                }

                if (parts.mainDescription.isNotEmpty()) {
                    Text("Описание:") {
                        modifier.font(boldFont).textColor(textColor)
                    }
                    for (line in parts.mainDescription) {
                        Text(line) {
                            modifier.textColor(textColor)
                                .width(Grow.Std).isWrapText(true)
                        }
                    }
                }

                parts.extractCodeFromTemplate()?.let { (lang, code) ->
                    val coloredText = Templates.TEMPLATES.getOrPut(lang + code) {
                        ScriptColorizer.parse("template.$lang", code).toMutableList()
                    }
                    Text("Пример:") {
                        modifier.font(boldFont).textColor(textColor)
                            .margin(bottom = sizes.smallGap)
                    }
                    Column {
                        modifier.padding(sizes.smallGap)
                            .background(RoundRectBackground(colors.backgroundMid.mulRgb(0.75f), sizes.gap))
                            .border(RoundRectBorder(colors.secondaryVariant, sizes.smallGap, sizes.borderWidth))
                            .alignX(AlignmentX.Center)
                            .width(Grow.Std)

                        coloredText.forEach {
                            AttributedText(it) {}
                        }
                    }
                }
            }
        }
    }

    @Serializable
    @SerialName("Field")
    class DocField(val name: String, val extReceiver: DocType?, val returnType: DocType) : DocDeclaration() {
        override fun UiScope.compose() {
            val parts = parseDocText()
            Column(Grow.Std) {
                modifier.padding(sizes.smallGap)

                // Сигнатура поля
                Row(Grow.Std) {
                    modifier.background(RoundRectBackground(colors.backgroundMid.mulRgb(0.75f), sizes.smallGap))
                        .border(RoundRectBorder(colors.secondaryVariant, sizes.smallGap, sizes.borderWidth))
                        .padding(sizes.smallGap)
                    Text("Переменная") {
                        modifier.font(boldFont.derive(20f)).textColor(SyntaxHighlight.EXTENSION_RECEIVER)
                            .background(
                                RoundRectBackground(
                                    IdeTheme.colors.background.mix(
                                        SyntaxHighlight.EXTENSION_RECEIVER,
                                        0.35f
                                    ), sizes.smallGap
                                )
                            )
                            .padding(sizes.smallGap * 0.5f)
                            .margin(end = sizes.smallGap)
                            .alignY(AlignmentY.Center)
                    }
                    if (extReceiver != null) {
                        Row {
                            modifier.alignY(AlignmentY.Center)
                            Text(extReceiver.type) {
                                modifier.font(codeFont).textColor(SyntaxHighlight.NAME_REFERENCE)
                                    .alignY(AlignmentY.Center)
                            }
                            Text(".") {
                                modifier.font(codeFont).textColor(textColor)
                                    .alignY(AlignmentY.Center)
                            }
                        }
                    }
                    Text(name) {
                        modifier.font(codeFont).textColor(SyntaxHighlight.PROPERTY_IDENTIFIER)
                            .alignY(AlignmentY.Center)
                    }
                    Text(": ") {
                        modifier.font(codeFont).textColor(textColor)
                            .alignY(AlignmentY.Center)
                    }
                    Text(returnType.type) {
                        modifier.font(codeFont).textColor(SyntaxHighlight.NAME_REFERENCE)
                            .alignY(AlignmentY.Center)
                    }

                    divider(marginTop = sizes.smallGap, marginBottom = sizes.gap)
                }

                if (parts.paramDescriptions.isNotEmpty()) {
                    Text("Параметры:") {
                        modifier.font(boldFont).textColor(textColor)
                    }
                    for ((paramName, desc) in parts.paramDescriptions) {
                        Row(Grow.Std) {
                            modifier.padding(sizes.smallGap * 0.5f)
                            Text("◦") {
                                modifier.font(codeFont).textColor(textColor)
                                    .alignY(AlignmentY.Center)
                                    .margin(end = sizes.smallGap)
                            }
                            Text(paramName) {
                                modifier.font(codeFont).textColor(SyntaxHighlight.PROPERTY_IDENTIFIER)
                                    .alignY(AlignmentY.Center)
                            }
                            Text(": $desc") {
                                modifier.font(codeFont).textColor(textColor)
                                    .width(Grow.Std).isWrapText(true)
                                    .alignY(AlignmentY.Center)
                            }
                        }
                    }
                    divider(marginBottom = sizes.gap, marginTop = sizes.gap)

                }

                if (parts.returnDescription != null) {
                    Text("Возвращаемое значение:") {
                        modifier.font(boldFont).textColor(textColor)
                    }
                    Text(parts.returnDescription) {
                        modifier.textColor(textColor)
                            .width(Grow.Std).isWrapText(true)
                    }
                    divider(marginBottom = sizes.gap, marginTop = sizes.gap)
                }

                if (parts.mainDescription.isNotEmpty()) {
                    Text("Описание:") {
                        modifier.font(boldFont).textColor(textColor)
                    }
                    for (line in parts.mainDescription) {
                        Text(line) {
                            modifier.textColor(textColor)
                                .width(Grow.Std).isWrapText(true)
                        }
                    }
                }

                parts.extractCodeFromTemplate()?.let { (lang, code) ->
                    val coloredText = Templates.TEMPLATES.getOrPut(lang + code) {
                        ScriptColorizer.parse("template.$lang", code).toMutableList()
                    }
                    Text("Пример:") {
                        modifier.font(boldFont).textColor(textColor)
                            .margin(bottom = sizes.smallGap)
                    }
                    Column {
                        modifier.padding(sizes.smallGap)
                            .background(RoundRectBackground(colors.backgroundMid.mulRgb(0.75f), sizes.gap))
                            .border(RoundRectBorder(colors.secondaryVariant, sizes.smallGap, sizes.borderWidth))
                            .alignX(AlignmentX.Center)
                            .width(Grow.Std)

                        coloredText.forEach {
                            AttributedText(it) {}
                        }
                    }
                }
            }
        }
    }
}

// Определение цветовой схемы для стилизации кода
val textColor = Color.WHITE
val codeFont by lazy { MsdfFont(HACK_FONT, 16f) }
val boldFont by lazy { MsdfFont(HACK_FONT, 16f, weight = 0.1f) }

@Serializable
class DocParameter(val name: String, val type: DocType)

@Serializable
class DocType(val type: String)

object Templates {
    val TEMPLATES = HashMap<String, MutableList<TextLine>>()
}