package ru.hollowhorizon.hollowengine.common.geary.tracking.systems

import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.modules.observe
import com.mineinabyss.geary.observers.events.OnRemove
import com.mineinabyss.geary.observers.events.OnSet
import com.mineinabyss.geary.systems.query.query
import ru.hollowhorizon.hollowengine.common.geary.tracking.EntityTracking
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity

fun Geary.createEntitySetListener() = observe<OnSet>()
    .involving(query<MCEntity>())
    .exec { (mcEntity) ->
        getAddon(EntityTracking).mc2Geary[mcEntity] = entity
    }

fun Geary.createEntityRemoveListener() = observe<OnRemove>()
    .involving(query<MCEntity>())
    .exec { (bukkit) -> getAddon(EntityTracking).mc2Geary.remove(bukkit.id) }