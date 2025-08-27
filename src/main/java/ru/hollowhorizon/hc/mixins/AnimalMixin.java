package ru.hollowhorizon.hc.mixins;


import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
//? if fabric {
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hc.common.utils.JavaHacks;
import ru.hollowhorizon.hc.common.events.EventBus;
import ru.hollowhorizon.hc.common.events.entity.BabySpawnEvent;
//?}

@Mixin(Animal.class)
public abstract class AnimalMixin extends AgeableMob{
    protected AnimalMixin(EntityType<? extends AgeableMob> entityType, Level level) {
        super(entityType, level);
    }

    //? if fabric {
    @Shadow public abstract void resetLove();

    @Shadow public abstract void finalizeSpawnChildFromBreeding(ServerLevel level, Animal animal, @Nullable AgeableMob baby);

    @Inject(
            method = "spawnChildFromBreeding",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/Animal;getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/AgeableMob;",
                    shift = At.Shift.AFTER
            ),
            cancellable = true)
    public void spawnChildFromBreeding(ServerLevel level, Animal mate, CallbackInfo ci) {
        var ageableMob = this.getBreedOffspring(level, mate);
        var e = new BabySpawnEvent(JavaHacks.forceCast(this), mate, ageableMob);
        EventBus.post(e);
        ageableMob = e.getChild();
        if (e.isCanceled()) {
            this.setAge(6000);
            mate.setAge(6000);
            this.resetLove();
            mate.resetLove();
            ci.cancel();
        }

        if (ageableMob != null) {
            ageableMob.setBaby(true);
            ageableMob.moveTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F);
            this.finalizeSpawnChildFromBreeding(level, mate, ageableMob);
            level.addFreshEntityWithPassengers(ageableMob);
            ci.cancel();
        }
    }
    //?}
}
