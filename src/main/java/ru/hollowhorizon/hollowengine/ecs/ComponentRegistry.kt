package ru.hollowhorizon.hollowengine.ecs

import ru.hollowhorizon.hc.common.events.AnnotationProcessorEvent
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.ecs.npc.NpcComponent

object ComponentRegistry {
    val NPC_COMPONENTS = HashMap<Class<out NpcComponent>, (NpcEntity) -> NpcComponent>()

}

@SubscribeEvent
fun onAnnotationProcessing(event: AnnotationProcessorEvent) {
    event.registerClassHandler<RegisterComponent> { target, annotation ->
        when {
            NpcComponent::class.java.isAssignableFrom(target) -> ComponentRegistry.NPC_COMPONENTS[target as Class<out NpcComponent>] =
                { (target.getConstructor().newInstance() as NpcComponent).apply { npc = it } }
        }
    }
}

annotation class RegisterComponent(val path: String)