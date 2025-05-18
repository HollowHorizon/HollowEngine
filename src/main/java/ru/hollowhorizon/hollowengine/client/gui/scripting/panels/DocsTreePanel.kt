package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.docking.Dock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterReloadListenersEvent
import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hollowengine.client.gui.docs.DocsNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.FileNode
import ru.hollowhorizon.hollowengine.ksp.file.DocFile

class DocsTreePanel(dock: Dock) : DockPanel("hollowengine.gui.ide.docs", dock) {
    override val icon = "hollowengine:textures/gui/icons/docs.svg"

    val tree = PagesReloadManager.tree().resize()

    override fun UiScope.compose() {

        tree()
    }

    private fun FileNode.resize(depth: Int = 0): FileNode {
        this.depth = depth
        children.forEach { it.resize(depth + 1) }
        return this
    }
}

object PagesReloadManager : ResourceManagerReloadListener {
    private val PAGES = HashMap<ResourceLocation, DocFile>()

    @OptIn(ExperimentalSerializationApi::class)
    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        resourceManager.listResources("docs") { it.path.endsWith(".json") }.forEach { (location, content) ->
            PAGES[location] = Json.decodeFromStream<DocFile>(content.open())
        }
    }

    fun tree(): DocsNode {
        val root = DocsNode("Документация", "")
        root.isFolder = true
        for (path in PAGES.keys.map { it.namespace + '/' + it.path }) {
            var current = root
            val parts = path.split("/")

            var nodePath = ""
            for (part in parts) {
                nodePath += ".$part"
                current = current.children.find { it.treeName == part } as? DocsNode ?: let {
                    DocsNode(
                        part,
                        path,
                        if (path.replace('/', '.') == nodePath.substring(1)) PAGES[path.replaceFirst('/', ':').rl] else null
                    ).apply {
                        current.children.add(this)
                        current.isFolder = true
                    }
                }
            }
        }

        return root
    }
}

@SubscribeEvent
fun clientPages(event: RegisterReloadListenersEvent.Client) {
    event.register(PagesReloadManager)
}