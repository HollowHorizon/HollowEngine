package ru.hollowhorizon.hollowengine.client.models.internal.v2

import ru.hollowhorizon.hollowengine.client.models.internal.Material
import ru.hollowhorizon.hollowengine.client.models.internal.Model
import ru.hollowhorizon.hollowengine.common.utils.Color
import java.util.IdentityHashMap

class ModelInstanceMaterials(model: Model) {
    private val instancesBySource = IdentityHashMap<Material, Material>()

    val values: List<Material>

    init {
        val sources = ArrayList<Material>()

        fun add(source: Material) {
            if (instancesBySource.containsKey(source)) return
            instancesBySource[source] = source.copyForInstance()
            sources += source
        }

        model.materials.forEach(::add)
        model.walkNodes()
            .mapNotNull { it.mesh }
            .flatMap { it.primitives }
            .map { it.material }
            .forEach(::add)

        values = sources.map { source -> instancesBySource.getValue(source) }
    }

    fun resolve(source: Material): Material =
        instancesBySource[source] ?: source.copyForInstance().also { instancesBySource[source] = it }

    private fun Material.copyForInstance(): Material = copy(color = Color(color))
}
