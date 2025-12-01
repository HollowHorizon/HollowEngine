package ru.hollowhorizon.hollowengine.mixins.components;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.components.ComponentContainer;
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.blocks.BlockEvent;

import java.util.EnumSet;

@Mixin(Level.class)
public abstract class LevelMixin implements ComponentDispatcher {
    @Unique
    private final ComponentContainer hollowengine$container = new ComponentContainer(this);
    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    @Inject(method = "updateNeighborsAt", at = @At("HEAD"))
    private void onUpdateNeighbors(BlockPos pos, Block block, CallbackInfo ci) {
        EventBus.post(new BlockEvent.NeighborNotify(getBlockState(pos), pos, EnumSet.allOf(Direction.class)));
    }

    @Override
    public @NotNull ComponentContainer getContainer() {
        return hollowengine$container;
    }

}
