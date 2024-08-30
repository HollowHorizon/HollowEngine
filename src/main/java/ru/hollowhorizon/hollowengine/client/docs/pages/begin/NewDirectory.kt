package ru.hollowhorizon.hollowengine.client.docs.pages.begin

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import net.minecraft.Util
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hollowengine.client.docs.DocsPage
import ru.hollowhorizon.hollowengine.client.docs.DocsRenderer
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.button
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.code
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.dline
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.openDir
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.text
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.titleImg
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.HOLLOW_ENGINE
import kotlin.io.path.Path
import kotlin.io.path.pathString

const val a = "1_he.1_begin.1_new_directory"

@DocsPage(a)
fun DocsRenderer.newDirectory() {
  text(language.getOrDefault("$a.text0"), 50)

  ImGui.newLine()

  titleImg("new_folder", arrayOf(288f, 44f), 0.5f)

  ImGui.newLine()
  ImGui.newLine()

  text("$a.text1")

  dline()

  ImGui.newLine()
  button("$a.text2") {
    openDir(Minecraft.getInstance().gameDirectory.resolve("hollowengine").path)
  }

  dline()

  ImGui.newLine()
  text("$a.text3")
  ImGui.newLine()

  /* Variables - Dirs */

  val varDir = "$a.var_dir"

  ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 16f)
  ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 4f)

  val oldCursorPosX = ImGui.getCursorPosX()
  val pageWidth = ImGui.getContentRegionAvailX() * 0.9f

  ImGui.setCursorPosX(ImGui.getContentRegionAvailX() / 2 - pageWidth / 2)
  ImGui.beginChild(
    "##variables.dirs",
    pageWidth,
    512f,
    true,
    ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize
  )

  ImGui.beginTabBar("tabs.dir")
  val tabsDir = "$a.tabs"

  ImGui.pushStyleColor(ImGuiCol.TabActive, 45, 45, 45, 255)
  if (ImGui.beginTabItem(language.getOrDefault("$tabsDir.tab0"))) { // Null
    ImGui.newLine()
    text("$tabsDir.tab0.text")

    ImGui.endTabItem()
  }
  ImGui.pushStyleColor(ImGuiCol.TabActive, 172, 145, 0, 255)
  if (ImGui.beginTabItem(language.getOrDefault("$tabsDir.tab1"))) { // Assets
    ImGui.newLine()
    ImGui.pushStyleColor(ImGuiCol.Text, 172, 145, 0, 255)
    text("Assets [assets]", 50, true, true)
    ImGui.popStyleColor(); ImGui.sameLine()
    ImGui.image("hollowengine:docs/icons/folder.png".rl.toTexture().id, 58f, 58f, 0f, 0f, 1f, 1f, 172f, 145f, 0f, 255f)

    dline()

    text("$tabsDir.tab1.text0"); ImGui.newLine()
    text("$tabsDir.tab1.text1")

    dline()
    ImGui.newLine()

    button("$tabsDir.tab1.button") { openDir(HOLLOW_ENGINE.resolve("assets").pathString) }

    ImGui.endTabItem()
  }
  ImGui.pushStyleColor(ImGuiCol.TabActive, 0, 79, 196, 255)
  if (ImGui.beginTabItem(language.getOrDefault("$tabsDir.tab2"))) { // Camera
    ImGui.newLine()
    ImGui.pushStyleColor(ImGuiCol.Text, 0, 79, 196, 255)
    text("Camera [camera]", 50, true, true)
    ImGui.popStyleColor(); ImGui.sameLine()
    ImGui.image("hollowengine:docs/icons/camera.png".rl.toTexture().id, 58f, 58f, 0f, 0f, 1f, 1f, 0f, 0.32f, 0.8f, 1f)

    dline()

    text("$tabsDir.tab2.text0"); ImGui.newLine()
    text("$tabsDir.tab2.text1")

    dline()
    ImGui.newLine()

    button("$tabsDir.tab2.button") { openDir(HOLLOW_ENGINE.resolve("camera").pathString) }

    ImGui.endTabItem()
  }
  ImGui.pushStyleColor(ImGuiCol.TabActive, 0, 186, 0, 255)
  if (ImGui.beginTabItem(language.getOrDefault("$tabsDir.tab3"))) { // NPCs
    ImGui.newLine()
    ImGui.pushStyleColor(ImGuiCol.Text, 0, 186, 0, 255)
    text("NPCs [npcs]", 50, true, true)
    ImGui.popStyleColor(); ImGui.sameLine()
    ImGui.image("hollowengine:docs/icons/npc.png".rl.toTexture().id, 58f, 58f, 0f, 0f, 1f, 1f, 0f, 0.65f, 0f, 1f)

    dline()

    text("$tabsDir.tab3.text0"); ImGui.newLine()
    text("$tabsDir.tab3.text1")

    dline()
    ImGui.newLine()

    button("$tabsDir.tab3.button") { openDir(HOLLOW_ENGINE.resolve("npcs").pathString) }

    ImGui.endTabItem()
  }
  ImGui.pushStyleColor(ImGuiCol.TabActive, 255, 102, 0, 255)
  if (ImGui.beginTabItem(language.getOrDefault("$tabsDir.tab4"))) { // Replays
    ImGui.newLine()
    ImGui.pushStyleColor(ImGuiCol.Text, 255, 102, 0, 255)
    text("Replays [replays]", 50, true, true)
    ImGui.popStyleColor(); ImGui.sameLine()
    ImGui.image("hollowengine:docs/icons/replay.png".rl.toTexture().id, 58f, 58f, 0f, 0f, 1f, 1f, 1f, 0.51f, 0f, 1f)

    dline()

    text("$tabsDir.tab4.text0"); ImGui.newLine()
    text("$tabsDir.tab4.text1")

    dline()
    ImGui.newLine()

    button("$tabsDir.tab4.button") { openDir(HOLLOW_ENGINE.resolve("replays").pathString) }

    ImGui.endTabItem()
  }
  ImGui.pushStyleColor(ImGuiCol.TabActive, 135, 0, 255, 255)
  if (ImGui.beginTabItem(language.getOrDefault("$tabsDir.tab5"))) { // Scripts
    ImGui.newLine()
    ImGui.pushStyleColor(ImGuiCol.Text, 135, 0, 255, 255)
    text("Scripts [scripts]", 50, true, true)
    ImGui.popStyleColor(); ImGui.sameLine()
    ImGui.image("hollowengine:docs/icons/scripts.png".rl.toTexture().id, 58f, 58f, 0f, 0f, 1f, 1f, 0.51f, 0f, 1f, 1f)

    dline()

    text("$tabsDir.tab5.text0"); ImGui.newLine()
    text("$tabsDir.tab5.text1")

    dline()
    ImGui.newLine()

    button("$tabsDir.tab5.button") { openDir(HOLLOW_ENGINE.resolve("scripts").pathString) }

    ImGui.endTabItem()
  }
  ImGui.pushStyleColor(ImGuiCol.TabActive, 117, 117, 117, 255)
  if (ImGui.beginTabItem(language.getOrDefault("$tabsDir.tab6"))) { // Storyteller World
    ImGui.newLine()
    ImGui.pushStyleColor(ImGuiCol.Text, 117, 117, 117, 255)
    text("Storyteller World [storyteller_dimension]", 50, true, true)
    ImGui.popStyleColor(); ImGui.sameLine()
    ImGui.image("hollowengine:docs/icons/world.png".rl.toTexture().id, 58f, 58f, 0f, 0f, 1f, 1f, 0.49f, 0.49f, 0.49f, 1f)

    dline()

    text("$tabsDir.tab6.text0"); ImGui.newLine()
    text("$tabsDir.tab6.text1")

    dline()
    ImGui.newLine()

    button("$tabsDir.tab6.button") { openDir(HOLLOW_ENGINE.resolve("storyteller_dimension").pathString) }

    ImGui.endTabItem()
  }
  ImGui.popStyleColor(7)

  ImGui.endTabBar()

  ImGui.endChild()
  ImGui.setCursorPosX(oldCursorPosX)

  ImGui.popStyleVar(2)

  ImGui.newLine()
}