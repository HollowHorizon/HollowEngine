package ru.hollowhorizon.hollowengine.fabric.internal;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import ru.hollowhorizon.hollowengine.api.NetworkManager;

import java.util.function.BiConsumer;

public class FabricNetworkManager implements NetworkManager {
    @Override
    public <T extends CustomPacketPayload> void registerClient(CustomPacketPayload.@NotNull Type<T> type, @NotNull StreamCodec<RegistryFriendlyByteBuf, T> codec, @NotNull BiConsumer<T, LocalPlayer> consumer) {
        PayloadTypeRegistry.playS2C().register(type, codec);
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> consumer.accept(payload, context.player()));
    }

    @Override
    public <T extends CustomPacketPayload> void registerServer(CustomPacketPayload.@NotNull Type<T> type, @NotNull StreamCodec<RegistryFriendlyByteBuf, T> codec, @NotNull BiConsumer<T, ServerPlayer> consumer) {
        PayloadTypeRegistry.playC2S().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> consumer.accept(payload, context.player()));
    }
}
