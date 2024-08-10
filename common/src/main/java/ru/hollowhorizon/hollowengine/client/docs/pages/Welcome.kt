package ru.hollowhorizon.hollowengine.client.docs.pages

import imgui.ImGui
import net.minecraft.Util
import ru.hollowhorizon.hollowengine.client.docs.DocsLanguage
import ru.hollowhorizon.hollowengine.client.docs.DocsPage
import ru.hollowhorizon.hollowengine.client.docs.DocsRenderer
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.button
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.dline
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.table
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.text
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.titleImg
import ru.hollowhorizon.hollowengine.client.docs.TableType

const val a = "1_he.0_welcome"

@DocsPage(a)
fun DocsRenderer.heWelcome() {
  text("${a}.text0", 50)

  ImGui.newLine()
  ImGui.newLine()

  titleImg("welcome", arrayOf(1387f, 895f), 0.55f)

  dline()

  text("${a}.text1")
  ImGui.newLine()
  text("${a}.text2")

  dline()

  table("${a}.table0", TableType.WARN, 300f) {
    text("${a}.table0.text0")
    ImGui.newLine()
    button(language.getOrDefault("${a}.table0.text1"), true) { Util.getPlatform().openUri("https://kotlinlang.org/docs/home.html") }
    ImGui.newLine()
    text("${a}.table0.text2")
  }

  dline()

  table("${a}.table1", TableType.ERR, 350f) {
    text("${a}.table1.text0")
    ImGui.newLine()
    text("${a}.table1.text1")

    dline()

    text("${a}.table1.text2")
  }
}