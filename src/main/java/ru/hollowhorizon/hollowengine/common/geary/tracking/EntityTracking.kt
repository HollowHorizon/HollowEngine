package ru.hollowhorizon.hollowengine.common.geary.tracking

import com.mineinabyss.geary.addons.dsl.Addon
import com.mineinabyss.geary.addons.dsl.createAddon
import com.mineinabyss.geary.components.relations.NoInherit
import com.mineinabyss.geary.datatypes.ComponentId
import com.mineinabyss.geary.helpers.componentId
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.observers.queries.QueryGroupedBy
import com.mineinabyss.geary.observers.queries.cacheGroupedBy
import com.mineinabyss.geary.systems.query.ShorthandQuery1
import com.mineinabyss.geary.systems.query.query
import ru.hollowhorizon.hollowengine.common.geary.onPluginEnable
import ru.hollowhorizon.hollowengine.common.geary.tracking.components.BindToEntityType
import ru.hollowhorizon.hollowengine.common.geary.tracking.helpers.GearyMobPrefabQuery
import ru.hollowhorizon.hollowengine.common.geary.tracking.systems.createEntityRemoveListener
import ru.hollowhorizon.hollowengine.common.geary.tracking.systems.createEntitySetListener

data class EntityTrackingModule(
    val mcEntityComponent: ComponentId,
    val mc2Geary: MCEntity2Geary = MCEntity2Geary(true),
    val query: GearyMobPrefabQuery,
    val entityTypeBinds: QueryGroupedBy<String, ShorthandQuery1<BindToEntityType>>,
) {
    data class Builder(
        var bindsQuery: Geary.() -> QueryGroupedBy<String, ShorthandQuery1<BindToEntityType>> = {
            cacheGroupedBy(query<BindToEntityType>()) { (type) ->
                entity.addRelation<NoInherit, BindToEntityType>()
                type.key
            }
        },
        var build: Geary.() -> EntityTrackingModule = {
            EntityTrackingModule(
                mcEntityComponent = componentId<MCEntity>(),
                query = GearyMobPrefabQuery(this),
                entityTypeBinds = bindsQuery()
            )
        },
    )

}

val EntityTracking: Addon<EntityTrackingModule.Builder, EntityTrackingModule> =
    createAddon<EntityTrackingModule.Builder, EntityTrackingModule>(
        "Entity Tracking",
        { EntityTrackingModule.Builder() }) {
        val module = configuration.build(geary)

        onPluginEnable {
            createEntityRemoveListener()
            createEntitySetListener()
        }

        module
    }