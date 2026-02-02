package ru.hollowhorizon.hollowengine.common.geary.components

import com.mineinabyss.geary.modules.observe
import com.mineinabyss.geary.observers.events.OnRemove
import com.mineinabyss.geary.observers.events.OnSet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import ru.hollowhorizon.hollowengine.api.Register
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.geary.GearyInitializeEvent

@Register
@Serializable
@SerialName("hollowengine:no_ai")
class NoAi

@SubscribeEvent
fun GearyInitializeEvent.initAiObservers(): Unit = with(geary) {
    observe<OnSet>().involving<NoAi>().exec {
        val living = entity.get<Entity>() as? Mob
        living?.isNoAi = true
    }

    observe<OnRemove>().involving<NoAi>().exec {
        val living = entity.get<Entity>() as? Mob
        living?.isNoAi = false
    }
}