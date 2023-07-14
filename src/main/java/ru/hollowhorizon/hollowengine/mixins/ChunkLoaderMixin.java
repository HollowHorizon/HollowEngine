package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.world.chunk.storage.ChunkLoader;
import net.minecraft.world.chunk.storage.IOWorker;
import net.minecraftforge.fml.loading.FMLPaths;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ru.hollowhorizon.hc.HollowCore;

import java.io.File;

@Mixin(ChunkLoader.class)
public class ChunkLoaderMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/storage/IOWorker;<init>(Ljava/io/File;ZLjava/lang/String;)V"))
    private File constructor(File pFolder) {
        String dim = pFolder.getParentFile().getName();

        HollowCore.LOGGER.info("Redirect chunk loader: {}", dim);

        if(dim.equals("storyteller_dimension")) return FMLPaths.GAMEDIR.get().resolve("hollowengine").resolve("storyteller_world").toFile();
        else return pFolder;
    }
}
