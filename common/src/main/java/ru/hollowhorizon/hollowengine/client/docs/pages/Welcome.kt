package ru.hollowhorizon.hollowengine.client.docs.pages

import imgui.ImGui
import ru.hollowhorizon.hollowengine.client.docs.DocsPage
import ru.hollowhorizon.hollowengine.client.docs.DocsRenderer

@DocsPage("he.welcome")
fun DocsRenderer.heWelcome() {
  ImGui.text("Добро пожаловать на документацию по HollowEngine 2.0")
}