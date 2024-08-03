package ru.hollowhorizon.hollowengine.mixins.extra;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import ru.hollowhorizon.hollowengine.PacketConfig;

@Mixin(value = ServerboundCustomPayloadPacket.class, priority = 9999)
public class ServerboundCustomPayloadPacketMixin {
    @ModifyConstant(method = "method_56475", constant = @Constant(intValue = 32767))
    private static int newSize(int value) {
        return PacketConfig.packetSize;
    }
}
