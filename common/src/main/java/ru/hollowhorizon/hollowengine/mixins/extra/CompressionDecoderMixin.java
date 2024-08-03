package ru.hollowhorizon.hollowengine.mixins.extra;

import net.minecraft.network.CompressionDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import ru.hollowhorizon.hollowengine.PacketConfig;

@Mixin(value = CompressionDecoder.class, priority = 9999)
public class CompressionDecoderMixin {
    @ModifyConstant(method = "decode", constant = @Constant(intValue = 8388608))
    private int newSize(int value) {
        return PacketConfig.packetSize;
    }
}