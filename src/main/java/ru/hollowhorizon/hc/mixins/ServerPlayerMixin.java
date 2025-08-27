package ru.hollowhorizon.hc.mixins;

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
import ru.hollowhorizon.hc.common.utils.JavaHacks;
import ru.hollowhorizon.hc.common.events.EventBus;
import ru.hollowhorizon.hc.common.events.entity.player.PlayerEvent;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void onClone(ServerPlayer player, boolean isClone, CallbackInfo ci) {
        EventBus.post(new PlayerEvent.Clone(JavaHacks.forceCast(this), player, !isClone));
    }

    @Inject(method = "startSleepInBed", at = @At("HEAD"), cancellable = true)
    private void onSleep(BlockPos bedPos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
        var event = new PlayerEvent.SleepInBed(JavaHacks.forceCast(this), null, bedPos);
        EventBus.post(event);
        if(event.getProblem() != null) {
            cir.setReturnValue(Either.left(event.getProblem()));
        }
    }

    @Inject(method = "stopSleepInBed", at = @At("HEAD"))
    private void onWakeup(boolean wakeImmediately, boolean updateLevelForSleepingPlayers, CallbackInfo ci) {
        EventBus.post(new PlayerEvent.Wakeup(JavaHacks.forceCast(this), wakeImmediately, updateLevelForSleepingPlayers));
    }
}
