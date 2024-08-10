package ru.hollowhorizon.hollowengine.client.docs.pages.begin

import imgui.ImGui
import ru.hollowhorizon.hollowengine.client.docs.DocsPage
import ru.hollowhorizon.hollowengine.client.docs.DocsRenderer
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.dline
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.text

@DocsPage("1_he.1_begin.0_mod_requests")
fun DocsRenderer.he_begin_modRequests() {
  text("Есть ли смысл говорить об этом?", 90)

  dline()

  text("Потому что - если ты читаешь это, то это значит что ты установил все зависимости")
  ImGui.newLine()
  text("А значит и нету смысла от этой вкладки")

  dline()

  text("Но я её оставлю по приколу)")

}