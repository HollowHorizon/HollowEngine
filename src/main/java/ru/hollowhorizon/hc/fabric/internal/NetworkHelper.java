//? if fabric {
package ru.hollowhorizon.hc.fabric.internal;

import kotlin.Unit;
import net.minecraft.client.Minecraft;
import ru.hollowhorizon.hc.common.utils.JavaHacks;
import ru.hollowhorizon.hc.common.network.HollowPacketKt;

public class NetworkHelper {
    public static void register() {
        HollowPacketKt.registerPacket = (type) -> {
            FabricNetworkKt.registerPacket(JavaHacks.forceCast(type));
            return Unit.INSTANCE;
        };
        HollowPacketKt.sendPacketToClient = (player, hollowPacketV3) -> {
            player.connection.send(HollowPacketKt.asVanillaPacket(hollowPacketV3, true));
            return Unit.INSTANCE;
        };
        HollowPacketKt.sendPacketToServer = (hollowPacketV3) -> {
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) connection.send(HollowPacketKt.asVanillaPacket(hollowPacketV3, false));
            return Unit.INSTANCE;
        };
        HollowPacketKt.registerPackets.invoke();
    }
}
//?}