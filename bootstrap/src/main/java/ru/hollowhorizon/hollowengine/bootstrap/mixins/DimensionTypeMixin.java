package ru.hollowhorizon.hollowengine.bootstrap.mixins;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

import java.nio.file.Path;

@Mixin(DimensionType.class)
public class DimensionTypeMixin {
    @Inject(method = "getStorageFolder", at = @At("RETURN"), cancellable = true)
    private static void hollowengine$onGetWorldFolder(ResourceKey<Level> dimensionKey, Path levelFolder, CallbackInfoReturnable<Path> cir) {
        var override = BootstrapRuntimeManager.bridge().getStorageFolderOverride(dimensionKey, levelFolder);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
