package ru.hollowhorizon.hollowengine.mixins.components;

import com.mineinabyss.geary.modules.Geary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.blocks.BlockEvent;
import ru.hollowhorizon.hollowengine.common.geary.GearyPlatform;
import ru.hollowhorizon.hollowengine.common.geary.api.GearyProvider;

import java.util.EnumSet;
import java.util.function.Supplier;

@Mixin(Level.class)
public abstract class LevelMixin implements GearyProvider {
    @Unique
    private Geary hollowengine$geary;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(WritableLevelData levelData, ResourceKey<?> dimension, RegistryAccess registryAccess, Holder<?> dimensionTypeRegistration, Supplier<?> profiler, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates, CallbackInfo ci) {
        hollowengine$geary = GearyPlatform.create((Level) (Object) this);
    }

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    @Shadow public abstract ProfilerFiller getProfiler();

    @Inject(method = "updateNeighborsAt", at = @At("HEAD"))
    private void onUpdateNeighbors(BlockPos pos, Block block, CallbackInfo ci) {
        EventBus.post(new BlockEvent.NeighborNotify(getBlockState(pos), pos, EnumSet.allOf(Direction.class)));
    }

    @Inject(method = "tickBlockEntities", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ProfilerFiller profiler = getProfiler();
        profiler.push("HollowEngine ECS");
        hollowengine$geary.tick();
        profiler.pop();
    }

    @Inject(method = "close", at = @At("TAIL"), remap = false)
    private void onClose(CallbackInfo ci) {
        hollowengine$geary.getApplication().close();
    }

    @Override
    public @NotNull Geary getHollowengine$geary() {
        return hollowengine$geary;
    }
}
