package ru.hollowhorizon.hollowengine.client.gui

import de.fabmax.kool.scene.Scene
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

class NPCToolGui(val npc: NPCEntity) : KoolGui {
    val npcOptions = ArrayList<NpcOption>()

    override fun Scene.setup() {
        TODO("Not yet implemented")
    }

}

@SubscribeEvent(100)
fun registerNpcOptions(event: NpcOptionsEvent) {
    //event.register(NpcOption("options") { NPCCreatorGui(event.npc, event.npc.id).open() })
    //event.register(NpcOption("trades") { TradeMenuGui(event.npc, true).open() })
}

class NpcOption(val name: String, val onClick: () -> Unit)

class NpcOptionsEvent(private val generator: (NpcOption) -> Unit, val npc: NPCEntity) : Event {
    fun register(npc: NpcOption) {
        generator(npc)
    }
}