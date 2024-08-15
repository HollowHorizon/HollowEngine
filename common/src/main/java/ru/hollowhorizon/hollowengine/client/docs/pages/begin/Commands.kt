package ru.hollowhorizon.hollowengine.client.docs.pages.begin

import imgui.ImGui
import ru.hollowhorizon.hollowengine.client.docs.DocsPage
import ru.hollowhorizon.hollowengine.client.docs.DocsRenderer
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.dline
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.text
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.titleImg

const val tels = "1_he.1_begin.2_commands"

@DocsPage(tels)
fun DocsRenderer.commands() {
  text("${tels}.text0")

  ImGui.newLine()

  titleImg("commands", arrayOf(1920f, 1080f), 0.5f)

  ImGui.newLine()
  ImGui.newLine()
  
  text("${tels}.text1")

  dline()
}