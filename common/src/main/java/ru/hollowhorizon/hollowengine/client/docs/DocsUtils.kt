package ru.hollowhorizon.hollowengine.client.docs

import imgui.ImGui
import imgui.extension.texteditor.TextEditor
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import net.minecraft.Util
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hollowengine.client.gui.scripting.KOTLIN_LANG
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

/**
 * Утилиты специально для документации
 */
object DocsUtils {
    private val docsLang = DocsLanguage.getInstance()

    /**
     * Отображает текст на странице
     * @param textId ID текст для перевода
     * @param fontSize Размер текста. Размер может быть только: 10, 20, 40, 0, 70, 90 или 100
     * @param center Определяет, должен ли текст быть по центру страницы
     *
     * @author _BENDY659_ and HollowHorizon.
     */
    fun text(textId: String, fontSize: Int = 30, center: Boolean = true, shadow: Boolean = false) {
        ImGuiMethods.pushFontSize(fontSize) {
            val contextWidth = ImGui.getContentRegionAvailX()
            val text =
                if (docsLang.has(textId)) docsLang.getOrDefault(textId)
                else textId

            val textWidth = ImGui.calcTextSize(text, true, contextWidth).x

            if (center) ImGui.sameLine(contextWidth / 2 - textWidth / 2)
            if (shadow) textShadow(text) else ImGui.textWrapped(text)
        }
    }

    /**
     * Показывает изображение как титульник для страницы
     * @param titleId ID титульника. Чтобы изображение появилось корректно, нужно чтобы в пути `hollowengine:docs/titles/` был файл изображения с таким же названием как и название титульника
     * @param customSize Размер титульника. По умолчанию стоит 1920f на 1080f
     * @param titleScale Процент отношения размера титульника от размера страницы
     * Титульник так же имеет hover текст. Чтобы он корректно отображался, нужно в папке перевода `lang/docs/<код_языка>.json` в id текста написать `titles.<имя титульника>.txt`
     *
     * @author _BENDY659_ and HollowHorizon.
     */
    fun titleImg(titleId: String, customSize: Array<Float> = arrayOf(1920f, 1080f), titleScale: Float = 1.0f) {
        val (imageWidth, imageHeight) = customSize[0] to customSize[1]
        val imgW = ImGui.getContentRegionAvailX() * titleScale
        val imgH = imgW * imageHeight / imageWidth

        ImGui.setCursorPosX(ImGui.getContentRegionAvailX() / 2 - imgW / 2)
        ImGui.image("hollowengine:docs/titles/$titleId.png".rl.toTexture().id, imgW, imgH)

        val (titleDesc, descExist) =
            if (docsLang.has("${titleId}_desc")) docsLang.getOrDefault("${titleId}_desc") to true
            else titleId to false

        if (ImGui.isItemHovered() && descExist) ImGui.setTooltip(docsLang.getOrDefault(titleDesc))
    }

    val tableSizes = mutableMapOf<String, Float>()

    /**
     * Красивая рамочка (разноцветная)
     * @param name Название рамочки сверху
     * @param type Тип рамки. Есть: note (серая), info (синяя), warn (жёлтая) и err (красная)
     * @param tableContainer Всё что будет внутри этой рамки
     *
     * @author _BENDY659_, HollowHorizon and Halva
     */
    fun table(name: String, type: TableType = TableType.NOTE, tableContainer: () -> Unit) {
        val borderColor = type.borderColor
        val bgColor = type.backgroundColor

        val iconPath = "hollowengine:docs/icons/table_$type.png".rl.toTexture().id
        val tableWidth = ImGui.getContentRegionAvailX() * 0.9f

        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 4f)
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 16f)
        ImGui.pushStyleColor(ImGuiCol.Border, borderColor[0], borderColor[1], borderColor[2], borderColor[3])
        ImGui.pushStyleColor(ImGuiCol.ChildBg, bgColor[0], bgColor[1], bgColor[2], bgColor[3])

        ImGui.setCursorPosX(ImGui.getContentRegionAvailX() / 2 - tableWidth / 2)
        ImGui.beginChild(
            "##table_$name", tableWidth, tableSizes.computeIfAbsent(name) { 100f }, true,
            ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize
        )

        val startHeight = ImGui.getCursorPosY()

        ImGui.setCursorPosX(0f + 8)
        ImGui.image(
            iconPath,
            58f, 58f,
            0f, 0f, 1f, 1f,
            borderColor[0].toFloat(), borderColor[1].toFloat(), borderColor[2].toFloat(), borderColor[3].toFloat()
        )
        text(name, 40, true, true); ImGui.sameLine()
        ImGui.setCursorPosX(ImGui.getWindowWidth() - 58f - 8)
        ImGui.image(
            iconPath,
            58f, 58f,
            0f, 0f, 1f, 1f,
            borderColor[0].toFloat(), borderColor[1].toFloat(), borderColor[2].toFloat(), borderColor[3].toFloat()
        )

        ImGui.separator()
        ImGui.newLine()

        tableContainer()

        val endHeight = ImGui.getCursorPosY()

        tableSizes[name] = (endHeight - startHeight) + ImGui.getStyle().windowPaddingY * 2

        ImGui.endChild()
        ImGui.popStyleColor(2)
        ImGui.popStyleVar(2)
    }

    /**
     * Улучшенный сепаратор
     *
     * @author _BENDY659_
     */
    fun dline() {
        ImGui.newLine()
        ImGui.separator()
        ImGui.newLine()
    }

    /**
     * @author _BENDY659_
     *
     * Better Button / Улучшенная кнопка
     */
    fun button(
        buttonId: String,
        center: Boolean = true,
        buttonSize: Float = 24f,
        customColor: Array<Int> = arrayOf(86, 86, 86, 255),
        buttonAction: () -> Unit,
    ): Boolean {
        val contextWidth = ImGui.getContentRegionAvailX()
        val textWidth = ImGui.calcTextSize(buttonId).x

        ImGui.pushStyleColor(ImGuiCol.Button, customColor[0], customColor[1], customColor[2], customColor[3])
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 8f)
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, buttonSize, buttonSize)

        val buttonText =
            if (docsLang.has(buttonId)) docsLang.getOrDefault(buttonId)
            else buttonId

        if (center) ImGui.sameLine((contextWidth / 2 - textWidth / 2))
        val buttonResult = ImGui.button(buttonText)

        ImGui.popStyleVar(2)
        ImGui.popStyleColor()

        val (buttonDesc, descExist) =
            if (docsLang.has("${buttonId}_desc")) docsLang.getOrDefault("${buttonId}_desc") to true
            else buttonId to false

        if (ImGui.isItemHovered() && descExist) ImGui.setTooltip(docsLang.getOrDefault(buttonDesc))

        if (buttonResult) {
            buttonAction()
        }

        return buttonResult
    }

    /**
     * Open directory
     *
     * @author _BENDY659_
     */
    fun openDir(dir: String) {
        val directory = Path(dir)

        if (!directory.exists()) directory.createDirectory()

        Util.getPlatform().openPath(directory)
    }

    /**
     * Code block
     *
     * @author HollowHorizon
     */
    fun code(id: String, lang: String = "kts", title: String, code: () -> String) {
        val text = code()

        val codeBlockSize = ImGui.calcTextSize(text)

        ImGui.pushStyleColor(ImGuiCol.ScrollbarBg, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrab, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrabActive, 0f, 0f, 0f, 0f)
        ImGui.pushStyleColor(ImGuiCol.ScrollbarGrabHovered, 0f, 0f, 0f, 0f)
        ImGui.beginChild(
            "##code_block-$id",
            ImGui.getContentRegionAvailX() * 0.9f / 2 - ImGui.getContentRegionAvailX() / 2, codeBlockSize.y+35f,
            true,
            ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize
        )

        EDITOR.text = text
        EDITOR.render("##code_block-$id")

        ImGui.endChild()
        ImGui.popStyleColor(4)
    }
}

val EDITOR = TextEditor().apply {
    setLanguageDefinition(KOTLIN_LANG)

    tabSize = 4
    text = ""
    isReadOnly = true
}

/* Classes class helpers */

enum class TableType(val borderColor: IntArray, val backgroundColor: IntArray) {
    NOTE(intArrayOf(163, 163, 136, 255), intArrayOf(30, 40, 60, 156)),
    INFO(intArrayOf(50, 120, 207, 255), intArrayOf(7, 27, 96, 156)),
    WARN(intArrayOf(230, 154, 0, 255), intArrayOf(70, 19, 0, 186)),
    ERR(intArrayOf(255, 0, 0, 255), intArrayOf(93, 0, 0, 186))
}