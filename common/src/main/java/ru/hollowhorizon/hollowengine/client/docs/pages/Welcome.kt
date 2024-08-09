package ru.hollowhorizon.hollowengine.client.docs.pages

import imgui.ImGui
import ru.hollowhorizon.hollowengine.client.docs.*
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.table
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.text
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.titleImg

val contextWidth get() = ImGui.getContentRegionAvailX()

val text0 = "Добро пожаловать на документацию по HollowEngine 2.0"

@DocsPage("he.welcome")
fun DocsRenderer.heWelcome() {
  text(text0, 50)

  ImGui.newLine()
  ImGui.newLine()

  titleImg("welcome", arrayOf(1387f, 895f), 0.7f)

  ImGui.newLine()
  ImGui.separator()
  ImGui.newLine()

  table("Test table", "err") {
    text("Test text")
  }
}