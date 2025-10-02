package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.blocks.BlockEvent;
import ru.hollowhorizon.hollowengine.common.events.level.LevelEvent;

import java.util.EnumSet;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Inject(method = "save", at = @At("TAIL"))
    private void onSave(ProgressListener $$0, boolean $$1, boolean $$2, CallbackInfo ci) {
        EventBus.post(new LevelEvent.Save(JavaHacks.forceCast(this)));
    }

    @Inject(method = "updateNeighborsAtExceptFromFacing", at = @At("HEAD"), cancellable = true)
    private void onUpdateNeighbors(BlockPos pos, Block blockType, Direction skipSide, CallbackInfo ci) {
        var sides = EnumSet.allOf(Direction.class);
        sides.remove(skipSide);
        var level = (ServerLevel) (Object) this;
        var event = new BlockEvent.NeighborNotify(level.getBlockState(pos), pos, sides);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }
}
