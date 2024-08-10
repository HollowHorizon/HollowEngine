package ru.hollowhorizon.hollowengine.client.docs.pages.begin

import imgui.ImGui
import net.minecraft.Util
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.docs.DocsPage
import ru.hollowhorizon.hollowengine.client.docs.DocsRenderer
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.button
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.dline
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.text
import ru.hollowhorizon.hollowengine.client.docs.DocsUtils.titleImg
import kotlin.io.path.Path

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

  button("$a.text2") {
    Util.getPlatform().openPath(Path(Minecraft.getInstance().gameDirectory.resolve("hollowengine").path))
  }

  dline()

  text("$a.text3")

  dline()


}