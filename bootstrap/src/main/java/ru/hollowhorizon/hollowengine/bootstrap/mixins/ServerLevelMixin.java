package ru.hollowhorizon.hollowengine.bootstrap.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

import java.util.EnumSet;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Inject(method = "save", at = @At("TAIL"))
    private void hollowengine$onSave(ProgressListener listener, boolean flush, boolean skipSave, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onServerLevelSave((ServerLevel) (Object) this);
    }

    @Inject(method = "updateNeighborsAtExceptFromFacing", at = @At("HEAD"), cancellable = true)
    private void hollowengine$onUpdateNeighbors(BlockPos pos, Block blockType, Direction skipSide, CallbackInfo ci) {
        EnumSet<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(skipSide);
        if (BootstrapRuntimeManager.bridge().onServerLevelNeighborNotify((ServerLevel) (Object) this, pos, sides)) {
            ci.cancel();
        }
    }
}
