package ru.hollowhorizon.hollowengine.mixins.extra;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.VarInt;
import net.minecraft.network.Varint21FrameDecoder;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import ru.hollowhorizon.hollowengine.PacketConfig;

import java.util.List;

@Mixin(Varint21FrameDecoder.class)
public abstract class Varint21FrameDecoderMixin {
    @Shadow @Final @Nullable private BandwidthDebugMonitor monitor;

    @Shadow private static boolean copyVarint(ByteBuf byteBuf, ByteBuf byteBuf2) {
        return false;
    }

    @Unique private final ByteBuf packetFixer$helperBuf = Unpooled.directBuffer(PacketConfig.var21Size);

    /**
     * @author HollowHorizon
     * @reason Max packet size changes
     */
    @Overwrite
    protected void handlerRemoved0(ChannelHandlerContext channelHandlerContext) {
        this.packetFixer$helperBuf.release();
    }

    @ModifyConstant(method = "copyVarint", constant = @Constant(intValue = 3))
    private static int newSize(int value) {
        return PacketConfig.var21Size;
    }

    /**
     * @author HollowHorizon
     * @reason Max packet size changes
     */
    @Overwrite
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) {
        byteBuf.markReaderIndex();
        this.packetFixer$helperBuf.clear();
        if (!copyVarint(byteBuf, this.packetFixer$helperBuf)) {
            byteBuf.resetReaderIndex();
        } else {
            int i = VarInt.read(this.packetFixer$helperBuf);
            if (byteBuf.readableBytes() < i) {
                byteBuf.resetReaderIndex();
            } else {
                if (this.monitor != null) {
                    this.monitor.onReceive(i + VarInt.getByteSize(i));
                }

                list.add(byteBuf.readBytes(i));
            }
        }
    }
}
