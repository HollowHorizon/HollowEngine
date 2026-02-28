package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.server.ServerChatEvent;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    // Damn Mohist/Tenet, why did you remove the obfuscation from this certain method?!
    @Shadow(aliases = {"detectRateSpam", "m_215251_"})
    protected abstract void detectRateSpam();

    @Inject(method = "broadcastChatMessage", at = @At(value = "HEAD"), cancellable = true)
    private void onHandleChat(PlayerChatMessage message, CallbackInfo ci) {
        var content = message.decoratedContent();
        var event = new ServerChatEvent(player, content);
        EventBus.post(event);
        if (event.getMessage() != content) {
            player.server.getPlayerList().getPlayers().forEach(player -> player.sendSystemMessage(event.getMessage()));
            detectRateSpam();
            event.setCanceled(true);
        }
        if (event.isCanceled()) ci.cancel();
    }
}
