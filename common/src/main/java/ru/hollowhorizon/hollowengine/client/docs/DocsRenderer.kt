package ru.hollowhorizon.hollowengine.client.docs

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiTreeNodeFlags
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hc.client.imgui.DockingHelper
import ru.hollowhorizon.hc.client.imgui.FontAwesomeIcons
import ru.hollowhorizon.hc.client.imgui.ImGuiHandler

class DocsRenderer : Screen(Component.empty()) {
  private val tree = convertListToTree(PAGES)
  private var renderer: PageRenderer? = null
  val language = DocsLanguage.getInstance()

  override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
    super.render(guiGraphics, mouseX, mouseY, partialTick)

    ImGuiHandler.drawFrame {
      ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 16f)
      ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8f, 8f)
      ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.64f, 0.64f, 0.64f, 0.5f)

      DockingHelper.splitHorizontally({
        // Root элемент нам не нужен
        tree.children.forEach {
          it.draw(language)?.let {
            renderer = it
          }
        }
      }, {
        with(this@DocsRenderer) {
          renderer?.apply { render() }
        }
      })

      ImGui.popStyleColor()
      ImGui.popStyleVar(2)
    }
  }

  companion object {
    val PAGES = mutableMapOf<String, PageRenderer>()
  }
}

data class DocTreeNode(val name: String, val path: String, val children: MutableList<DocTreeNode> = mutableListOf()) {
  var renderer: PageRenderer? = null

  fun draw(language: DocsLanguage): PageRenderer? {
    var page: PageRenderer? = null
    val drawArrow = children.isNotEmpty()
    val flags =
      if (drawArrow) ImGuiTreeNodeFlags.SpanFullWidth
      else ImGuiTreeNodeFlags.NoTreePushOnOpen or ImGuiTreeNodeFlags.Leaf or ImGuiTreeNodeFlags.SpanFullWidth

    var hovered = false
    var isDrawn = false
    if (ImGui.treeNodeEx(
        (if (drawArrow) FontAwesomeIcons.Folder else FontAwesomeIcons.File)
            + " " + language.getOrDefault(path),
        flags
      )
    ) {
      hovered = ImGui.isItemHovered()
      children.forEach {
        it.draw(language)?.let { page = it }
      }

      isDrawn = true
      if (drawArrow) ImGui.treePop()
    }
    hovered = hovered || (ImGui.isItemHovered() && !isDrawn)
    if (hovered && ImGui.isMouseClicked(0)) {
      page = renderer
    }
    return page
  }

  fun sort() {
    children.sortBy { it.name }
    children.forEach { it.sort() }
  }
}


fun convertListToTree(paths: Map<String, PageRenderer>): DocTreeNode {
  val root = DocTreeNode("root", "root")
  for (path in paths.keys) {
    var currentNode = root
    val parts = path.split(".")
    for (part in parts) {
      val existingNode = currentNode.children.find { it.name == part }
      if (existingNode != null) {
        currentNode = existingNode
        currentNode.renderer = paths[path]
      } else {
        val newNode = DocTreeNode(part, path)
        if (part == parts.last()) {
          newNode.renderer = paths[path]
        }
        currentNode.children.add(newNode)
        currentNode = newNode
      }
    }
  }
  root.sort()
  return root
}