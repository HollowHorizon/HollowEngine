package ru.hollowhorizon.hollowengine.client.docs

import imgui.ImGui

@DocsPage("main")
fun DocsRenderer.main() {
    ImGui.text("Привет 1")
}

@DocsPage("main.first")
fun DocsRenderer.main1() {
    ImGui.text("Привет 2")
}

@DocsPage("main.second")
fun DocsRenderer.main2() {
    ImGui.text("Привет 3")
}
