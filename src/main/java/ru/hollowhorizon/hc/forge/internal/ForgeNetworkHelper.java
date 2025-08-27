package ru.hollowhorizon.hc.forge.internal;//? if forge {
/*package ru.hollowhorizon.hc.forge.internal;

import kotlin.Unit;

//? if >=1.21 {
import net.minecraftforge.network.SimpleChannel;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
//?} else {
/^import net.minecraftforge.network.NetworkRegistry.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
^///?}
import ru.hollowhorizon.hc.common.utils.ForgeKotlinKt;
import ru.hollowhorizon.hc.common.utils.JavaHacks;
import ru.hollowhorizon.hc.common.network.HollowPacketKt;

public class ForgeNetworkHelper {
    public static SimpleChannel hollowCoreChannel = ChannelBuilder
            //? if >=1.21 {
            .named("hollowcore:hollow_packets")
            .networkProtocolVersion(4)
            .clientAcceptedVersions(Channel.VersionTest.exact(4))
            .serverAcceptedVersions(Channel.VersionTest.exact(4))
            
            //?} else {
            /^.named(ForgeKotlinKt.getRl("hollowcore:hollow_packets"))
            .networkProtocolVersion(() -> "4")
            .clientAcceptedVersions(v->v.equals("4"))
            .serverAcceptedVersions(v->v.equals("4"))
            ^///?}
            .simpleChannel();

    public static void register() {

        HollowPacketKt.registerPacket = (type) -> {
            ForgeNetworkKt.registerPacket(JavaHacks.forceCast(type));
            return Unit.INSTANCE;
        };
        HollowPacketKt.sendPacketToClient = (player, hollowPacketV3) -> {
            //? if >=1.21 {
            hollowCoreChannel.send(hollowPacketV3, PacketDistributor.PLAYER.with(player));
            //?} else {
            /^hollowCoreChannel.send(PacketDistributor.PLAYER.with(() -> player), hollowPacketV3);
            ^///?}
            return Unit.INSTANCE;
        };
        HollowPacketKt.sendPacketToServer = (hollowPacketV3) -> {
            //? if >=1.21 {
            hollowCoreChannel.send(hollowPacketV3, PacketDistributor.SERVER.noArg());
            //?} else {
            /^hollowCoreChannel.send(PacketDistributor.SERVER.noArg(), hollowPacketV3);
            ^///?}
            return Unit.INSTANCE;
        };
        HollowPacketKt.registerPackets.invoke();
    }
}
*///?}