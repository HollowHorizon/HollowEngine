package ru.hollowhorizon.hollowengine.client.gui

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.kool.KoolManager.MONOCRAFT
import ru.hollowhorizon.hollowengine.client.kool.KoolScreen
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.post

fun interface KoolGui {
    fun Scene.setup()
}

class DashBoardScreen : KoolScreen() {

    override fun Scene.setup() {
        val modTabs = ArrayList<Tab>()
        TabEvent(modTabs::add).post()

        setupUiScene()

        addPanelSurface {
            modifier.align(AlignmentX.Center, AlignmentY.Center).border(RectBorder(colors.primaryVariant, 3.dp))

            Text("HollowEngine Меню") {
                modifier.alignX(AlignmentX.Center).font(MsdfFont(MONOCRAFT, 30f)).margin(10.dp)
            }

            Column {
                modifier.margin(10.dp).alignX(AlignmentX.Center)

                modTabs.forEach { tab ->
                    Button(tab.name) {
                        modifier.onClick { tab.onClick() }.font(MsdfFont(MONOCRAFT, 30f)).margin(10.dp)
                    }
                }
            }
        }
    }

    class Tab(val name: String, val onClick: () -> Unit)
    class TabEvent(private val generator: (Tab) -> Unit) : Event, ClientEvent {
        fun register(tab: Tab) {
            generator(tab)
        }
    }
}