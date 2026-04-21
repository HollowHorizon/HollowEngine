package ru.hollowhorizon.hollowengine.bootstrap.mixins;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void hollowengine$onClone(ServerPlayer player, boolean isClone, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onPlayerClone((ServerPlayer) (Object) this, player, !isClone);
    }

    @Inject(method = "startSleepInBed", at = @At("HEAD"), cancellable = true)
    private void hollowengine$onSleep(BlockPos bedPos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
        Either<Player.BedSleepingProblem, Unit> result =
                BootstrapRuntimeManager.bridge().onPlayerSleepInBed((ServerPlayer) (Object) this, bedPos);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "stopSleepInBed", at = @At("HEAD"))
    private void hollowengine$onWakeup(boolean wakeImmediately, boolean updateLevelForSleepingPlayers, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onPlayerWakeup((ServerPlayer) (Object) this, wakeImmediately, updateLevelForSleepingPlayers);
    }
}
