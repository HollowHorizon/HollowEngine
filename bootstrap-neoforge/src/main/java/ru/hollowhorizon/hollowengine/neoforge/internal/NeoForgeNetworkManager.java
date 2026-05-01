package ru.hollowhorizon.hollowengine.neoforge.internal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.NotNull;
import ru.hollowhorizon.hollowengine.api.NetworkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class NeoForgeNetworkManager implements NetworkManager {
    private static final List<Registrable> PACKETS = new ArrayList<>();

    public static void onRegisterPackets(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        PACKETS.forEach(packet -> packet.register(registrar));
    }

    @Override
    public <T extends CustomPacketPayload> void registerClient(CustomPacketPayload.@NotNull Type<T> type, @NotNull StreamCodec<RegistryFriendlyByteBuf, T> codec, @NotNull BiConsumer<T, Player> consumer) {
        PACKETS.add(new ToClientPacket<>(type, codec, consumer));
    }

    @Override
    public <T extends CustomPacketPayload> void registerServer(CustomPacketPayload.@NotNull Type<T> type, @NotNull StreamCodec<RegistryFriendlyByteBuf, T> codec, @NotNull BiConsumer<T, Player> consumer) {
        PACKETS.add(new ToServerPacket<>(type, codec, consumer));
    }

    private interface Registrable {
        void register(PayloadRegistrar registrar);
    }

    private record ToClientPacket<T extends CustomPacketPayload>(
            CustomPacketPayload.@NotNull Type<T> type,
            @NotNull StreamCodec<RegistryFriendlyByteBuf, T> codec,
            @NotNull BiConsumer<T, Player> consumer
    ) implements Registrable {
        @Override
        public void register(PayloadRegistrar registrar) {
            registrar.playToClient(type, codec, ((packet, context) -> consumer.accept(packet, context.player())));
        }
    }

    private record ToServerPacket<T extends CustomPacketPayload>(
            CustomPacketPayload.@NotNull Type<T> type,
            @NotNull StreamCodec<RegistryFriendlyByteBuf, T> codec,
            @NotNull BiConsumer<T, Player> consumer
    ) implements Registrable {
        @Override
        public void register(PayloadRegistrar registrar) {
            registrar.playToServer(type, codec, ((packet, context) -> consumer.accept(packet, context.player())));
        }
    }
}
