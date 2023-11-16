package ru.hollowhorizon.hollowengine.client.render.effects

import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.client.models.gltf.manager.IAnimated
import ru.hollowhorizon.hc.client.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2

@Serializable
class ParticleEffect(var location: @Serializable(ForResourceLocation::class) ResourceLocation, var node: String)

@HollowCapabilityV2(IAnimated::class)
class EffectsCapability: CapabilityInstance() {
    val effects by syncableList<ParticleEffect>()
}