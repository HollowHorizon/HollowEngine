package ru.hollowhorizon.hollowstory.common.entities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.world.World;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, "hollowstory");
    public static final RegistryObject<EntityType<NPCEntity>> NPC_ENTITY = ENTITIES.register("npc_entity", () -> EntityType.Builder.of(
            (EntityType.IFactory<NPCEntity>) NPCEntity::new,
            EntityClassification.CREATURE).sized(1.0F, 2.0F).build("hollowstory:npc_entity"));

    public static void register() {

    }
}
