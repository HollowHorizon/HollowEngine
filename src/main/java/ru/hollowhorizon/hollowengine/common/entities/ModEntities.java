package ru.hollowhorizon.hollowengine.common.entities;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import ru.hollowhorizon.hc.api.registy.HollowRegister;
import ru.hollowhorizon.hollowengine.HollowEngine;
import ru.hollowhorizon.hollowengine.client.render.enitites.SindyRenderer;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, HollowEngine.MODID);
    public static final RegistryObject<EntityType<NPCEntity>> NPC_ENTITY = ENTITIES.register("npc_entity", () -> EntityType.Builder.of(
            (EntityType.IFactory<NPCEntity>) NPCEntity::new,
            EntityClassification.CREATURE).sized(0.6F, 1.8F).build("hollowengine:npc_entity"));
    public static final RegistryObject<EntityType<ThomasNPC>> THOMAS = ENTITIES.register("thomas", () -> EntityType.Builder.of(
            ThomasNPC::new,
            EntityClassification.CREATURE).sized(0.6F, 1.8F).build("hollowengine:thomas"));

    @HollowRegister(renderer = "ru.hollowhorizon.hollowengine.client.render.enitites.SindyRenderer")
    public static final EntityType<SindyNPC> SINDY = EntityType.Builder.of(SindyNPC::new, EntityClassification.CREATURE).sized(0.6F, 1.8F).build("hollowstory:sindy");

    public static void register() {

    }
}
