package ru.hollowhorizon.hollowengine.mixins;


import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hc.HollowCore;
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager;
import ru.hollowhorizon.hollowengine.common.registry.ModDimensions;

import java.nio.file.Path;

@Mixin(DimensionType.class)
public class DimensionTypeMixin {
    @Inject(method = "getStorageFolder", at = @At("RETURN"), cancellable = true)
    private static void onGetWorldFolder(ResourceKey<Level> pDimensionKey, Path pLevelFolder, CallbackInfoReturnable<Path> cir) {
        if (pDimensionKey.location().equals(ModDimensions.INSTANCE.getSTORYTELLER_DIMENSION().location())) {
            HollowCore.LOGGER.info("Redirect StoryTeller dimension folder!");
            cir.setReturnValue(DirectoryManager.INSTANCE.getHOLLOW_ENGINE().resolve("storyteller_dimension"));
        }
    }
}
