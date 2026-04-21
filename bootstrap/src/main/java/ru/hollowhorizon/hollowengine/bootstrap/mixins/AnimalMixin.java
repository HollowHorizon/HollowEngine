package ru.hollowhorizon.hollowengine.bootstrap.mixins;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimeBridge;

@Mixin(Animal.class)
public abstract class AnimalMixin extends net.minecraft.world.entity.AgeableMob {
    protected AnimalMixin(EntityType<? extends AgeableMob> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow public abstract void resetLove();
    @Shadow public abstract void finalizeSpawnChildFromBreeding(ServerLevel level, Animal animal, @Nullable AgeableMob baby);

    @Inject(
            method = "spawnChildFromBreeding",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/Animal;getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/AgeableMob;",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void hollowengine$spawnChildFromBreeding(ServerLevel level, Animal mate, CallbackInfo ci) {
        AgeableMob child = this.getBreedOffspring(level, mate);
        RuntimeBridge.BreedResult result = BootstrapRuntimeManager.bridge().onAnimalBreed((Animal) (Object) this, mate, child);
        child = result.getChild();
        if (result.isCancelled()) {
            this.setAge(6000);
            mate.setAge(6000);
            this.resetLove();
            mate.resetLove();
            ci.cancel();
        }

        if (child != null) {
            child.setBaby(true);
            child.moveTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F);
            this.finalizeSpawnChildFromBreeding(level, mate, child);
            level.addFreshEntityWithPassengers(child);
            ci.cancel();
        }
    }
}
