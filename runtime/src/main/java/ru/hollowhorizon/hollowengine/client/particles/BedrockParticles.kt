package ru.hollowhorizon.hollowengine.client.particles

import kotlinx.serialization.ExperimentalSerializationApi
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.models.bedrock.BedrockExpressions
import ru.hollowhorizon.hollowengine.client.particles.file.BedrockParticleFile
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat

object BedrockParticles : ResourceManagerReloadListener {
    val PARTICLES = hashMapOf<ResourceLocation, BedrockParticleFile>()

    @OptIn(ExperimentalSerializationApi::class)
    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        PARTICLES.clear()
        resourceManager.listResources("particles") { it.path.endsWith(".bedrock.json") }
            .forEach { (location, resource) ->
                try {
                    PARTICLES[location] = BedrockExpressions.batch { JsonFormat.decodeFromStream(resource.open()) }
                } catch (e: Exception) {
                    HollowEngine.LOGGER.warn("Error while loading $location", e)
                }
            }
    }
}