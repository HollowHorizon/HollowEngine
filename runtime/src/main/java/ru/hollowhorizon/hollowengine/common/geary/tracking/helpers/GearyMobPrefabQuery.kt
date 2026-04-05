package ru.hollowhorizon.hollowengine.common.geary.tracking.helpers

import com.mineinabyss.geary.datatypes.GearyEntity
import com.mineinabyss.geary.datatypes.family.family
import com.mineinabyss.geary.helpers.contains
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.prefabs.PrefabKey
import com.mineinabyss.geary.prefabs.configuration.components.Prefab
import com.mineinabyss.geary.systems.query.CachedQuery
import com.mineinabyss.geary.systems.query.GearyQuery
import com.mineinabyss.geary.systems.query.ShorthandQuery1
import com.mineinabyss.geary.systems.query.query
import ru.hollowhorizon.hollowengine.common.geary.tracking.EntityTracking

class GearyMobPrefabQuery(world: Geary) : GearyQuery(world) {
    val mobQuery = family {
        has<Prefab>()
    }

    val prefabs = cache(query<PrefabKey> { add(mobQuery) })

    companion object {
        fun isMob(entity: GearyEntity): Boolean = with(entity.world) {
            return entity.prefabs.any { it.type in getAddon(EntityTracking).query.mobQuery }
        }

        fun isMobPrefab(entity: GearyEntity): Boolean = with(entity.world) {
            return entity.type in getAddon(EntityTracking).query.mobQuery
        }
    }
}


fun CachedQuery<ShorthandQuery1<PrefabKey>>.getKeys() = map { it.comp1 }
fun CachedQuery<ShorthandQuery1<PrefabKey>>.getKeyStrings() = map { it.comp1.toString() }