package ru.hollowhorizon.hollowengine.mixins.components;

import com.github.quillraven.fleks.World;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.profiling.ProfilerFiller;
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
import ru.hollowhorizon.hollowengine.common.fleks.FleksPlatform;
import ru.hollowhorizon.hollowengine.common.fleks.FleksWorld;

import java.util.EnumSet;

@Mixin(Level.class)
public abstract class LevelMixin implements ComponentDispatcher, FleksWorld {
    @Unique
    private final ComponentContainer hollowengine$container = new ComponentContainer(this);
    @Unique
    private World hollowengine$fleks;

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    @Shadow public abstract ProfilerFiller getProfiler();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstruct(CallbackInfo ci) {
        hollowengine$fleks = FleksPlatform.create$HollowEngine_fabric_1_20_1((Level) (Object) this);
    }

    @Inject(method = "updateNeighborsAt", at = @At("HEAD"))
    private void onUpdateNeighbors(BlockPos pos, Block block, CallbackInfo ci) {
        EventBus.post(new BlockEvent.NeighborNotify(getBlockState(pos), pos, EnumSet.allOf(Direction.class)));
    }

    @Override
    public @NotNull ComponentContainer getContainer() {
        return hollowengine$container;
    }

    @Inject(method = "tickBlockEntities", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ProfilerFiller profiler = getProfiler();
        profiler.push("HollowEngine ECS");
        hollowengine$fleks.update(1f); // 1 tick
        profiler.pop();
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void onClose(CallbackInfo ci) {
        hollowengine$fleks.dispose();
    }

    @Override
    public @NotNull World getHollowengine$fleksWorld() {
        return hollowengine$fleks;
    }
}
