package ru.hollowhorizon.hollowstory.common.entities;

import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import ru.hollowhorizon.hollowstory.common.npcs.IHollowNPC;
import ru.hollowhorizon.hollowstory.common.npcs.NPCSettings;
import ru.hollowhorizon.hollowstory.dialogues.HDCharacterKt;

public class NPCEntity extends CreatureEntity implements IHollowNPC {
    private NPCSettings options = new NPCSettings();
    private Entity puppet;
    public static final DataParameter<String> NAME_PARAM = EntityDataManager.defineId(NPCEntity.class, DataSerializers.STRING);


    public NPCEntity(NPCSettings options, World level) {
        super(ModEntities.NPC_ENTITY.get(), level);

        this.setCustomName(new StringTextComponent(options.getName()));
        this.setCustomNameVisible(true);

        this.options = options;
        String entity = options.getPuppetEntity();
        String nbt = "";
        if (entity.contains("@")) {
            String[] split = entity.split("@");

            entity = split[0];
            nbt = split[1];
        }
        puppet = EntityType.loadEntityRecursive(HDCharacterKt.generateEntityNBT(entity, nbt), level, e -> e);
        this.entityData.set(NAME_PARAM, options.getPuppetEntity());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(NAME_PARAM, "minecraft:skeleton");
    }

    @Override
    public void onSyncedDataUpdated(DataParameter<?> p_184206_1_) {
        super.onSyncedDataUpdated(p_184206_1_);
        if (p_184206_1_ == NAME_PARAM) {
            String name = this.entityData.get(NAME_PARAM);
            if(!name.equals("")) {
                String nbt = "";
                if (name.contains("@")) {
                    String[] split = name.split("@");

                    name = split[0];
                    nbt = split[1];
                }
                this.setPuppet(EntityType.loadEntityRecursive(HDCharacterKt.generateEntityNBT(name, nbt), this.level, e -> e));
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
    }

    public NPCEntity(EntityType<NPCEntity> type, World world) {
        super(type, world);

        this.setCustomName(new StringTextComponent(options.getName()));
        this.setCustomNameVisible(true);

        puppet = null;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(9999, new NPCArtificialIntelligence(this));
    }

    @Override
    public boolean isInvulnerable() {
        return this.options.getData().isUndead();
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override //не думаю, что NPC можно деспавниться...
    public void checkDespawn() {
    }

    @NotNull
    @Override
    public NPCEntity getNpcEntity() {
        return this;
    }

    @Override
    public void stopFollow() {

    }

    private static class NPCArtificialIntelligence extends Goal {
        private final NPCEntity npc;

        public NPCArtificialIntelligence(NPCEntity npcEntity) {
            this.npc = npcEntity;
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public void tick() {


        }
    }

    public Entity getPuppet() {
        return puppet;
    }

    public void setPuppet(Entity puppet) {
        this.puppet = puppet;
    }
}
