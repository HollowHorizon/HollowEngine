package ru.hollowhorizon.hollowengine.client.docs.pages.story_events.npc

import imgui.ImGui
import ru.hollowhorizon.hollowengine.client.docs.DocsPage
import ru.hollowhorizon.hollowengine.client.docs.DocsRenderer
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.text
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.titleImg

const val a = "1_he.2_story_events.0_npc.0_create"

@DocsPage(a)
fun DocsRenderer.npcCreate() {
  text("$a.text0", 50)

  ImGui.newLine()

  titleImg("")
}