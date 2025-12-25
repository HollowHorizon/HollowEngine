package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(method = "respawn", at = @At("RETURN"))
    private void hollowengine$firePlayerRespawnEvent(
            ServerPlayer original,
            boolean returnFromEnd,
            CallbackInfoReturnable<ServerPlayer> cir
    ) {
        EventBus.post(new PlayerEvent.Respawn(original, returnFromEnd));
    }
}