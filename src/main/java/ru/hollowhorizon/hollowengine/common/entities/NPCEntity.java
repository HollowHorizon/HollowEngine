package ru.hollowhorizon.hollowengine.common.entities;

import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.NotNull;
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2;
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2Kt;
import ru.hollowhorizon.hc.common.capabilities.ICapabilitySyncer;
import ru.hollowhorizon.hollowengine.common.capabilities.NPCEntityCapability;
import ru.hollowhorizon.hollowengine.common.npcs.IHollowNPC;
import ru.hollowhorizon.hollowengine.common.npcs.IconType;
import ru.hollowhorizon.hollowengine.dialogues.HDCharacterKt;

public class NPCEntity extends CreatureEntity implements IHollowNPC, ICapabilitySyncer {
    private Entity puppet;
    public final Object interactionWaiter = new Object();

    public NPCEntity(World level) {
        super(ModEntities.NPC_ENTITY.get(), level);
    }

    @Override
    protected ActionResultType mobInteract(PlayerEntity pPlayer, Hand pHand) {
        synchronized (interactionWaiter) {
            interactionWaiter.notifyAll();
        }
        return super.mobInteract(pPlayer, pHand);
    }

    public void setIcon(@NotNull IconType type) {
        this.getCapability(HollowCapabilityV2.Companion.get(NPCEntityCapability.class)).ifPresent((cap) -> {
            cap.setIconType(type);

            HollowCapabilityV2Kt.syncEntity(cap, this);
        });
    }

    public IconType getIcon() {
        return this.getCapability(HollowCapabilityV2.Companion.get(NPCEntityCapability.class))
                .orElseThrow(() -> new IllegalStateException("NPCEntity Capability not found!")).getIconType();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
    }

    public NPCEntity(EntityType<NPCEntity> type, World world) {
        super(type, world);

        this.getCapability(HollowCapabilityV2.Companion.get(NPCEntityCapability.class)).ifPresent((cap) -> {
            this.setCustomName(new StringTextComponent(cap.getSettings().getName()));
            this.setCustomNameVisible(true);
        });
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(9999, new NPCArtificialIntelligence(this));
    }

    @Override
    public boolean isInvulnerable() {
        return this.getCapability(HollowCapabilityV2.Companion.get(NPCEntityCapability.class)).orElseThrow(() -> new IllegalStateException("NPCEntity Capability not found!")).getSettings().getData().isUndead();
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
    public void onCapabilitySync(@NotNull Capability<?> capability) {
        if (level.isClientSide) {
            this.getCapability(HollowCapabilityV2.Companion.get(NPCEntityCapability.class)).ifPresent((cap) -> {
                String[] entity = cap.getSettings().getPuppetEntity().split("@");
                String nbt = entity.length > 1 ? entity[1] : "";
                if (puppet != null) {
                    if (puppet.getType().getRegistryName().equals(new ResourceLocation(entity[0]))) return;
                }
                puppet = EntityType.loadEntityRecursive(HDCharacterKt.generateEntityNBT(entity[0], nbt), level, e -> e);

                this.setCustomName(new StringTextComponent(cap.getSettings().getName()));
                this.setCustomNameVisible(true);
            });
        }
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
