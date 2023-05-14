package ru.hollowhorizon.hollowengine.common.registry;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import ru.hollowhorizon.hc.api.registy.HollowRegister;
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity;

public class ModEntities {
    @HollowRegister(renderer = "ru.hollowhorizon.hc.client.render.entity.GLTFEntityRenderer")
    public static final EntityType<NPCEntity> NPC_ENTITY = EntityType.Builder.of(
            (EntityType.IFactory<NPCEntity>) NPCEntity::new,
            EntityClassification.CREATURE).sized(0.6F, 1.8F).build("hollowengine:npc_entity");

    public static void register() {
    }
}
