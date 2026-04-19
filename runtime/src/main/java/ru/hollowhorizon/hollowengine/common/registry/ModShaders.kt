package ru.hollowhorizon.hollowengine.common.registry

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.ShaderInstance
import ru.hollowhorizon.hollowengine.HollowCore.MODID
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterShadersEvent
import ru.hollowhorizon.hollowengine.common.utils.rl

@ClientOnly
object ModShaders {
    lateinit var GLTF_ENTITY: ShaderInstance
    lateinit var GLTF_ENTITY_INSTANCED: ShaderInstance

    @SubscribeEvent
    fun onShaderRegistry(event: RegisterShadersEvent) {
        event.register(
            "$MODID:gltf_entity-1.21.1".rl,
            DefaultVertexFormat.NEW_ENTITY
        ) {
            GLTF_ENTITY = it
        }
        event.register(
            "$MODID:gltf_entity_instanced-1.21.1".rl,
            DefaultVertexFormat.NEW_ENTITY
        ) {
            GLTF_ENTITY_INSTANCED = it
        }
    }
}
