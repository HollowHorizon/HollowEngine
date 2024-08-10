package ru.hollowhorizon.hollowengine.client.docs

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture

/**
 * English:
 * Utilities special for Docs
 *
 * Russian:
 * Утилиты специально для документации
 */
object DocsUtils {
  private val docsLang = DocsLanguage.getInstance() 
  
  /**
   * @author _BENDY659_ and HollowHorizon
   *
   * > English:
   * Displays the text in of the page
   * @param text Your text to be shown
   * @param fontSize Text size.The size can only be: 10b 20b 40b 50b 70b 90 or 100
   * @param center Determines whether the text should be centered on the page
   *
   * > Russian:
   * Отображает текст на странице
   * @param text Ваш текст который будет показан
   * @param fontSize Размер текста. Размер может быть только: 10, 20, 40, 0, 70, 90 или 100
   * @param center Определяет, должен ли текст быть по центру страницы
   */
  fun text(text: String, fontSize: Int = 30, center: Boolean = true, shadow: Boolean = false) {
    ImGuiMethods.pushFontSize(fontSize) {
      val contextWidth = ImGui.getContentRegionAvailX()
      val textWidth = ImGui.calcTextSize(text).x

      if(center) ImGui.sameLine(contextWidth / 2 - textWidth / 2)
      if(shadow) textShadow(text) else ImGui.textWrapped(text)
    }
  }

  /**
   * @author _BENDY659_ and HollowHorizon
   *
   * > English:
   *
   *
   * > Russian:
   * Показывает изображение как титульник для страницы
   * @param titleName Имя титульника. Чтобы изображение появилось корректно, нужно чтобы в пути `hollowengine:docs/titles/` был файл изображения с таким же названием как и название титульника
   * @param customSize Размер титульника. По умолчанию стоит 1920f на 1080f
   * @param titleScale Процент отношения размера титульника от размера страницы
   * Титульник так же имеет hover текст. Чтобы он корректно отображался, нужно в папке перевода `lang/docs/<код_языка>.json` в id текста написать `titles.<имя титульника>.txt`
   */
  fun titleImg(titleName: String, customSize: Array<Float> = arrayOf(1920f, 1080f), titleScale: Float = 1.0f) {
    val (imageWidth, imageHeight) = customSize[0] to customSize[1]
    val imgW = ImGui.getContentRegionAvailX() * titleScale
    val imgH = imgW * imageHeight / imageWidth

    ImGui.setCursorPosX(ImGui.getContentRegionAvailX() / 2 - imgW / 2)
    ImGui.image("hollowengine:docs/titles/$titleName.png".rl.toTexture().id, imgW,imgH)

    val titleText = "title.$titleName.txt"

    if (ImGui.isItemHovered() && docsLang.has(titleText)) ImGui.setTooltip(docsLang.getOrDefault(titleText))
  }

  /**
   * @author _BENDY659_ and Halva
   *
   * > English:
   *
   *
   * > Russian:
   * Красивая рамочка (разноцветная)
   * @param name Название рамочки сверху
   * @param type Тип рамки. Есть: note (серая), info (синяя), warn (жёлтая) и err (красная)
   * @param tableContainer Всё что будет внутри этой рамки
   */
  fun table(name: String, type: String = "note", height: Float = 512f, tableContainer: () -> Unit) {
    val typeColor = when (type) {
      "note" -> arrayOf(
      163, 163, 136, 255, // RGBA Border
      30, 40, 60, 156 // RGBA Background
      )
      "info" -> arrayOf(
      50, 120, 207, 255, // RGBA Border
      7, 27, 96, 156 // RGBA Background
      )
      "warn" -> arrayOf(
      230, 154, 0, 255, // RGBA Border
      70, 19, 0, 186 // RGBA Background
      )
      "err" -> arrayOf(
      255, 0, 0, 255, // RGBA Border
      93, 0, 0, 186 // RGBA Background
      )
      else -> throw IllegalArgumentException("Unknown type: $type")
    }
    val iconPath = "hollowengine:docs/icons/table_$type.png".rl.toTexture().id
    val tableWidth = ImGui.getContentRegionAvailX() * 0.9f

    ImGui.pushStyleVar(ImGuiStyleVar.WindowTitleAlign, 0.5f, 0.5f)
    ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 64f)
    ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 32f)
    ImGui.pushStyleColor(ImGuiCol.Border, typeColor[0], typeColor[1], typeColor[2], typeColor[3])
    ImGui.pushStyleColor(ImGuiCol.ChildBg, typeColor[4], typeColor[5], typeColor[6], typeColor[7])

    ImGui.setCursorPosX(ImGui.getContentRegionAvailX() / 2 - tableWidth / 2)
    ImGui.beginChild(
      "##table_$name", tableWidth, height, true,
      ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize
    )

    ImGui.setCursorPosX(0f + 8)
    ImGui.image(
      iconPath,
      58f, 58f,
      0f, 0f, 1f, 1f,
      typeColor[0].toFloat(), typeColor[1].toFloat(), typeColor[2].toFloat(), typeColor[3].toFloat()
    )
    text(name, 40, true, true); ImGui.sameLine()
    ImGui.setCursorPosX(ImGui.getWindowWidth() - 58f - 8)
    ImGui.image(
      iconPath,
      58f, 58f,
      0f, 0f, 1f, 1f,
      typeColor[0].toFloat(), typeColor[1].toFloat(), typeColor[2].toFloat(), typeColor[3].toFloat()
    )

    ImGui.separator()
    ImGui.newLine()

    tableContainer()

    ImGui.endChild()
    ImGui.popStyleColor(2)
    ImGui.popStyleVar(3)
  }

  /**
   * @author _BENDY659_
   *
   * English: Better separator
   *
   * Russian: Улучшенный сепаратор
   */
  fun dline() {
    ImGui.newLine()
    ImGui.newLine()
    ImGui.separator()
    ImGui.newLine()
    ImGui.newLine()
  }

  /**
   * @author _BENDY659_
   *
   * English: Better Button
   *
   * Russian: Улучшенная кнопка
   */
  fun button(buttonName: String, center: Boolean = true): Boolean {
    val contextWidth = ImGui.getContentRegionAvailX()
    val textWidth = ImGui.calcTextSize(buttonName).x

    ImGui.newLine()
    if(center) ImGui.sameLine(contextWidth / 2 - textWidth / 2)
    val buttonResult = ImGui.button(buttonName)
    ImGui.newLine()
    
    if(ImGui.isItemHovered() && docsLang.has("${buttonName}_desc")) ImGui.setTooltip(docsLang.getOrDefault("${buttonName}_desc"))
    
    return buttonResult
  }
}