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

val contextWidth get() = ImGui.getContentRegionAvailX()

val text0 get() = DocsLanguage.getInstance().getOrDefault("1_he.0_welcome.text0")

@DocsPage("1_he.0_welcome")
fun DocsRenderer.heWelcome() {
  text(text0, 50)

  ImGui.newLine()
  ImGui.newLine()

  titleImg("welcome", arrayOf(1387f, 895f), 0.55f)

  dline()

  text(language.getOrDefault("1_he.0_welcome.text1"))
  ImGui.newLine()
  text(language.getOrDefault("1_he.0_welcome.text2"))

  dline()

  table(language.getOrDefault("1_he.0_welcome.table0"), "warn", 275f) {
    text(language.getOrDefault("1_he.0_welcome.table0.text0"))
    ImGui.newLine()
    if(button(language.getOrDefault("1_he.0_welcome.table0.text1"))) Util.getPlatform().openUri("https://kotlinlang.org/docs/home.html")
    ImGui.newLine()
    text(language.getOrDefault("1_he.0_welcome.table0.text2"))
  }

  dline()

  table(language.getOrDefault("1_he.0_welcome.table1"), "err") {
    text(language.getOrDefault("1_he.0_welcome.table1.text0"))
    ImGui.newLine()
    text(language.getOrDefault("1_he.0_welcome.table1.text1"))

    dline()

    text(language.getOrDefault("1_he.0_welcome.table1.text2"))
  }
}