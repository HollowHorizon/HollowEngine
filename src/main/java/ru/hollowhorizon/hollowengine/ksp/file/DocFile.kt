package ru.hollowhorizon.hollowengine.ksp.file

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import java.io.File

@Serializable
@SerialName("File")
class DocFile(val title: String, val description: String) : Composable {
    val declarations = mutableListOf<DocDeclaration>()

    override fun UiScope.compose() {
        Column {
            modifier.padding(sizes.smallGap)

            Text(title) {
                modifier.alignX(AlignmentX.Center)
                    .font(MsdfFont(HACK_FONT, 30f))
            }

            divider(thickness = sizes.borderWidth)

            Text(description) {
                modifier.padding(horizontal = sizes.smallGap)
                    .width(Grow.Std).isWrapText(true)
            }

            divider(thickness = sizes.borderWidth)

            declarations.forEach {
                Box {
                    modifier.width(Grow.Std)
                        .background(RoundRectBackground(colors.backgroundVariant, sizes.gap))
                        .border(RoundRectBorder(colors.primaryVariant, sizes.gap, sizes.borderWidth))
                        .padding(sizes.smallGap)
                        .margin(vertical = sizes.smallGap)

                    Column {
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
        val returnDescription: String?
    )

    fun parseDocText(): DocTextParts {
        val mainDescription = mutableListOf<String>()
        val paramDescriptions = mutableMapOf<String, String>()
        var returnDescription: String? = null
        var currentTag: String? = null

        for (line in docText) {
            when {
                line.startsWith("@param") -> {
                    val parts = line.substringAfter("@param").trim().split(" ", limit = 2)
                    if (parts.size == 2) {
                        paramDescriptions[parts[0]] = parts[1]
                        currentTag = "param_${parts[0]}"
                    } else {
                        currentTag = null
                    }
                }
                line.startsWith("@return") -> {
                    returnDescription = line.substringAfter("@return").trim()
                    currentTag = "return"
                }
                line.startsWith("@") -> currentTag = null
                else -> {
                    when (currentTag) {
                        null -> mainDescription.add(line)
                        "return" -> returnDescription = (returnDescription ?: "") + " $line"
                        else -> {
                            val paramName = currentTag.substringAfter("param_")
                            paramDescriptions[paramName] = (paramDescriptions[paramName] ?: "") + " $line"
                        }
                    }
                }
            }
        }
        return DocTextParts(mainDescription, paramDescriptions, returnDescription)
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
                            .textColor(methodColor)
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
                    Arrow(if(isMethodsExpanded.use()) ArrowScope.ROTATION_DOWN else ArrowScope.ROTATION_LEFT) {
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
                    Arrow(if(isFieldsExpanded.use()) ArrowScope.ROTATION_DOWN else ArrowScope.ROTATION_LEFT) {
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
    class DocMethod(val name: String, val valueParameters: List<DocParameter>, val returnType: DocType) :
        DocDeclaration() {
        override fun UiScope.compose() {
            val parts = parseDocText()
            Column {
                modifier.padding(sizes.smallGap)

                // Сигнатура метода
                Row {
                    modifier.background(RectBackground(colors.backgroundMid))
                        .padding(sizes.smallGap)
                    Text("fun ") {
                        modifier.font(codeFont).textColor(keywordColor)
                    }
                    Text(name) {
                        modifier.font(codeFont).textColor(methodColor)
                    }
                    Text("(") {
                        modifier.font(codeFont).textColor(textColor)
                    }
                    valueParameters.forEachIndexed { i, param ->
                        if (i > 0) Text(", ") {
                            modifier.font(codeFont).textColor(textColor)
                        }
                        Text(param.name) {
                            modifier.font(codeFont).textColor(paramColor)
                        }
                        Text(": ") {
                            modifier.font(codeFont).textColor(textColor)
                        }
                        Text(param.type.type) {
                            modifier.font(codeFont).textColor(typeColor)
                        }
                    }
                    Text("): ") {
                        modifier.font(codeFont).textColor(textColor)
                    }
                    Text(returnType.type) {
                        modifier.font(codeFont).textColor(typeColor)
                    }
                }

                if (parts.paramDescriptions.isNotEmpty()) {
                    Text("Параметры:") {
                        modifier.font(boldFont).textColor(textColor)
                    }
                    for ((paramName, desc) in parts.paramDescriptions) {
                        Text("- $paramName: $desc") {
                            modifier.textColor(textColor)
                                .width(Grow.Std).isWrapText(true)
                        }
                    }
                }

                if (parts.returnDescription != null) {
                    Text("Возвращаемое значение:") {
                        modifier.font(boldFont).textColor(textColor)
                    }
                    Text(parts.returnDescription) {
                        modifier.textColor(textColor)
                            .width(Grow.Std).isWrapText(true)
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
            }
        }
    }

    @Serializable
    @SerialName("Field")
    class DocField(val name: String, val _type: DocType) : DocDeclaration() {
        override fun UiScope.compose() {
            val parts = parseDocText()
            Column {
                modifier.padding(sizes.smallGap)

                // Сигнатура поля
                Row {
                    modifier.backgroundColor(colors.backgroundMid)
                        .padding(sizes.smallGap)
                    Text("val ") {
                        modifier.font(codeFont).textColor(keywordColor)
                    }
                    Text(name) {
                        modifier.font(codeFont).textColor(fieldColor)
                    }
                    Text(": ") {
                        modifier.font(codeFont).textColor(textColor)
                    }
                    Text(_type.type) {
                        modifier.font(codeFont).textColor(typeColor)
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
            }
        }
    }
}

// Определение цветовой схемы для стилизации кода
val keywordColor = Color.BLUE
val methodColor = Color.GREEN
val fieldColor = Color(0f, 0.5f, 0f, 1f) // Темно-зеленый
val paramColor = Color.MAGENTA
val typeColor = Color.CYAN
val textColor = Color.WHITE
val codeFont by lazy{ MsdfFont(HACK_FONT, 16f)}
val boldFont by lazy{ MsdfFont(HACK_FONT, 16f, weight = 0.1f)}

@Serializable
class DocParameter(val name: String, val type: DocType)

@Serializable
class DocType(val type: String)

fun main() {
    println(
        Json.decodeFromStream<DocFile>(File("C:\\Users\\Artem\\Modding\\HollowEngine\\src\\main\\resources\\actions.json").inputStream())
    )
}