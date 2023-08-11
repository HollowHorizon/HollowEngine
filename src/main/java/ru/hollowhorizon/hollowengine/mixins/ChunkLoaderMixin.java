package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraftforge.fml.loading.FMLPaths;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ru.hollowhorizon.hc.HollowCore;

import java.nio.file.Path;

@Mixin(ChunkStorage.class)
public class ChunkLoaderMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/storage/IOWorker;<init>(Ljava/nio/file/Path;ZLjava/lang/String;)V"))
    private Path constructor(Path pFolder) {
        String dim = pFolder.getParent().toFile().getName();

        HollowCore.LOGGER.info("Redirect chunk loader: {}", dim);

        if(dim.equals("storyteller_dimension")) return FMLPaths.GAMEDIR.get().resolve("hollowengine").resolve("storyteller_world");
        else return pFolder;
    }
}
