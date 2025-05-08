package ru.hollowhorizon.hollowengine.client.gui

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hc.client.kool.KoolManager.MONOCRAFT
import ru.hollowhorizon.hc.client.kool.KoolScreen
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hollowengine.client.kool.Grid
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

class NPCToolGui(val npc: NPCEntity) : KoolScreen() {
    override fun Scene.setup() {
        val options = HashSet<NpcOption>()
        NpcOptionsEvent(options::add, npc).post()

        setupUiScene()

        val sizes = Sizes.medium

        addPanelSurface(sizes = sizes.copy(normalText = MsdfFont(MONOCRAFT, 30f))) {
            modifier.background(null).size(Grow.Std, Grow.Std)
                .margin(sizes.largeGap)

            Column {
                modifier
                    .size(Grow(0.85f), Grow(0.85f))
                    .align(AlignmentX.Center, AlignmentY.Center)
                    .border(RectBorder(Color.WHITE, sizes.borderWidth))
                    .background(RectBackground(Color("00000066")))
                    .padding(sizes.gap)

                Text("Меню персонажа") {
                    modifier.align(AlignmentX.Center).textAlignX(AlignmentX.Center)
                }

                divider()

                Grid {
                    //modifier.size(Grow.Std, Grow(0.85f))
                }
            }
        }
    }
}

@SubscribeEvent(100)
fun registerNpcOptions(event: NpcOptionsEvent) {
    //event.register(NpcOption("options") { NPCCreatorGui(event.npc, event.npc.id).open() })
    //event.register(NpcOption("trades") { TradeMenuGui(event.npc, true).open() })
}

data class NpcOption(val name: String, val onClick: () -> Unit)

class NpcOptionsEvent(private val generator: (NpcOption) -> Unit, val npc: NPCEntity) : Event {
    fun register(npc: NpcOption) {
        generator(npc)
    }
}