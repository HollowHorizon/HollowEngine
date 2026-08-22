package ru.hollowhorizon.hollowengine.client.models.obj

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.models.internal.Model
import ru.hollowhorizon.hollowengine.client.models.internal.manager.ModelLoader
import ru.hollowhorizon.hollowengine.client.models.internal.manager.ModelSide

object ObjModelLoader: ModelLoader {
    override val supportedFormats: Set<String>
        get() = setOf("obj")

    override suspend fun load(location: ResourceLocation, side: ModelSide): Model {
        return OBJModel(location, null, side).toInternalModel()
    }

}
