package ru.hollowhorizon.hollowengine.mixins.components;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
import ru.hollowhorizon.hollowengine.common.components.Component;
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.blocks.BlockEvent;

import java.util.EnumSet;
import java.util.Map;

@Mixin(Level.class)
public abstract class LevelMixin implements ComponentDispatcher {
    @Unique
    private final Map<ResourceLocation, Component<?>> hollowCore$components = new Object2ObjectOpenHashMap<>();
    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    @Inject(method = "updateNeighborsAt", at = @At("HEAD"))
    private void onUpdateNeighbors(BlockPos pos, Block block, CallbackInfo ci) {
        EventBus.post(new BlockEvent.NeighborNotify(getBlockState(pos), pos, EnumSet.allOf(Direction.class)));
    }

    @Override
    public @NotNull Map<@NotNull ResourceLocation, @NotNull Component<?>> getHollowcore$components() {
        return hollowCore$components;
    }
}
