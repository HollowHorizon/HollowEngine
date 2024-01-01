package ru.hollowhorizon.hollowengine.common.network;

import static ru.hollowhorizon.hollowengine.common.network.NetworkHandler.HollowEngineChannel;

public class PacketRegisterer {
    public static void register() {
        HollowEngineChannel.registerMessage(
                0, SpawnParticlesPacket.class,
                SpawnParticlesPacket::write, SpawnParticlesPacket::read, SpawnParticlesPacket::handle
        );
    }
}
