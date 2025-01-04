package ru.hollowhorizon.hollowengine.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.MsdfFont
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hc.client.imgui.Component
import ru.hollowhorizon.hc.client.kool.KoolManager
import ru.hollowhorizon.hc.client.kool.KoolManager.MONOCRAFT_DATA
import ru.hollowhorizon.hc.client.kool.KoolScreen
import ru.hollowhorizon.hc.client.utils.literal
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hc.common.network.request
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.RequestTreePacket
import ru.hollowhorizon.hollowengine.client.kool.Grid
import ru.hollowhorizon.hollowengine.client.kool.GridLayout
import ru.hollowhorizon.hollowengine.docs.closeDocs
import ru.hollowhorizon.hollowengine.docs.launchDocs
import kotlin.random.Random

fun interface KoolGui {
    fun Scene.setup()
}

class DashBoardScreen : KoolScreen({
    val modTabs = ArrayList<Tab>()
    TabEvent(modTabs::add).post()

    setupUiScene()

    addPanelSurface {
        modifier.align(AlignmentX.Center, AlignmentY.Center).border(RectBorder(colors.primaryVariant, 3.dp))

        Text("HollowEngine Меню") {
            modifier.alignX(AlignmentX.Center).font(MsdfFont(MONOCRAFT_DATA, 30f)).margin(10.dp)
        }

        Column {
            modifier.margin(10.dp)

            modTabs.forEach { tab ->
                Button(tab.name) {
                    modifier.onClick { tab.onClick() }.font(MsdfFont(MONOCRAFT_DATA, 30f))
                }
            }
        }
    }
}) {

    class Tab(val name: String, val onClick: () -> Unit)
    class TabEvent(private val generator: (Tab) -> Unit) : Event {
        fun register(tab: Tab) {
            generator(tab)
        }
    }
}

@SubscribeEvent
fun onAddTab(event: DashBoardScreen.TabEvent) {
    event.register(DashBoardScreen.Tab("code_editor") {
        scopeSync {
            val newTree = RequestTreePacket().request().tree
            RenderSystem.recordRenderCall {
                IDEGuiV2.fileTree = newTree
                IDEGuiV2.open()
            }
        }
    })
    event.register(DashBoardScreen.Tab("docs") {
        DocsScreen.open()
    })
}

object DocsScreen: Screen("".literal) {
    override fun added() = launchDocs(KoolManager.context)
    override fun removed() = closeDocs(KoolManager.context)
}