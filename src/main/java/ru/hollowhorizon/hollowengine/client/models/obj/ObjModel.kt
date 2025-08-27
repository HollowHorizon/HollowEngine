package ru.hollowhorizon.hollowengine.client.models.obj

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.Model
import ru.hollowhorizon.hollowengine.client.models.internal.manager.ModelLoader

object ObjModelLoader: ModelLoader {
    override val supportedFormats: Set<String>
        get() = setOf("obj")

    override suspend fun load(location: ResourceLocation): AnimatedModel {
        return AnimatedModel(OBJModel(location).toInternalModel())
    }

}