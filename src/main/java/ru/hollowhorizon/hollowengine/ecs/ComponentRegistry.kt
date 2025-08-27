package ru.hollowhorizon.hollowengine.ecs

import ru.hollowhorizon.hollowengine.common.events.AnnotationProcessorEvent
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBT_TAGS
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.ecs.npc.NpcComponent

object ComponentRegistry {
    val NPC_COMPONENTS = HashMap<String, (NpcEntity) -> NpcComponent>()

}

@SubscribeEvent
fun onAnnotationProcessing(event: AnnotationProcessorEvent) {
    event.registerClassHandler<RegisterComponent> { target, annotation ->
        when {
            NpcComponent::class.java.isAssignableFrom(target) -> ComponentRegistry.NPC_COMPONENTS[annotation.location] =
                { (target.getConstructor().newInstance() as NpcComponent).apply { npc = it } }
        }
        NBT_TAGS.getOrPut(NpcComponent::class) { ArrayList() }.add(target.kotlin)
    }
}

annotation class RegisterComponent(val location: String)