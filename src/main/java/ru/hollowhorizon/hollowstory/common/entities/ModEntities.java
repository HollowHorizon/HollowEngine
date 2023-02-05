package ru.hollowhorizon.hollowstory.common.entities;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, "hollowstory");
    public static final RegistryObject<EntityType<NPCEntity>> NPC_ENTITY = ENTITIES.register("npc_entity", () -> EntityType.Builder.of(
            (EntityType.IFactory<NPCEntity>) NPCEntity::new,
            EntityClassification.CREATURE).sized(0.6F, 1.8F).build("hollowstory:npc_entity"));
    public static final RegistryObject<EntityType<ThomasNPC>> THOMAS = ENTITIES.register("thomas", () -> EntityType.Builder.of(
            ThomasNPC::new,
            EntityClassification.CREATURE).sized(0.6F, 1.8F).build("hollowstory:thomas"));

    public static void register() {

    }
}
