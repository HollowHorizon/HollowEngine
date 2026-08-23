package ru.hollowhorizon.hollowengine.client.models.internal.v2

import ru.hollowhorizon.hollowengine.client.models.internal.Material
import ru.hollowhorizon.hollowengine.client.models.internal.Model
import ru.hollowhorizon.hollowengine.client.models.internal.allMaterials
import ru.hollowhorizon.hollowengine.client.models.internal.manager.MaterialSources
import ru.hollowhorizon.hollowengine.common.models.MaterialSource
import ru.hollowhorizon.hollowengine.common.utils.Color
import java.util.IdentityHashMap

class ModelInstanceMaterials(model: Model) {
    private val instancesBySource = IdentityHashMap<Material, Material>()

    val values: List<Material>

    /** This instance's materials by name; a name the model uses twice keeps the first. */
    val byName: Map<String, Material>

    init {
        val sources = model.allMaterials()
        sources.forEach { source -> instancesBySource[source] = source.copyForInstance() }

        values = sources.map { source -> instancesBySource.getValue(source) }
        byName = values
            .filter { it.name.isNotEmpty() }
            .associateByTo(LinkedHashMap(), Material::name)
    }

    fun resolve(source: Material): Material =
        instancesBySource[source] ?: source.copyForInstance().also { instancesBySource[source] = it }

    /**
     * Dresses this instance in [overrides], and puts everything else back the way the model authored it.
     */
    fun apply(overrides: Map<String, MaterialSource>) {
        instancesBySource.forEach { (source, instance) -> instance.restoreFrom(source) }
        if (overrides.isEmpty()) return

        overrides.forEach { (name, source) ->
            val material = byName[name] ?: return@forEach
            val resolved = MaterialSources.resolve(source)
            resolved.texture?.let { material.texture = it }
            resolved.normal?.let { material.normalTexture = it }
            resolved.specular?.let { material.specularTexture = it }
            resolved.color?.let { hex -> Color.fromHexOrNull(hex)?.let { material.color = it } }
        }
    }

    private fun Material.restoreFrom(source: Material) {
        color = Color(source.color)
        texture = source.texture
        normalTexture = source.normalTexture
        specularTexture = source.specularTexture
        doubleSided = source.doubleSided
        blend = source.blend
    }

    private fun Material.copyForInstance(): Material = copy(color = Color(color))
}
