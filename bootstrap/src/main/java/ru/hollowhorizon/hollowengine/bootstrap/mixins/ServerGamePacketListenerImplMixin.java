package ru.hollowhorizon.hollowengine.bootstrap.mixins;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimeBridge;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow public ServerPlayer player;
    @Shadow protected abstract void detectRateSpam();

    @Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true)
    private void hollowengine$onHandleChat(PlayerChatMessage message, CallbackInfo ci) {
        Component content = message.decoratedContent();
        RuntimeBridge.ChatResult result = BootstrapRuntimeManager.bridge().onServerChat(player, content);
        if (result.getMessage() != content) {
            player.server.getPlayerList().getPlayers().forEach(target -> target.sendSystemMessage(result.getMessage()));
            detectRateSpam();
            ci.cancel();
            return;
        }
        if (result.isCancelled()) ci.cancel();
    }
}
