package ru.hollowhorizon.hollowengine.mixins.client;

import net.minecraft.Util;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.ConsoleAppender;
import ru.hollowhorizon.hollowengine.HollowLoggerKt;
import ru.hollowhorizon.hollowengine.client.utils.HollowCoreLoader;
import ru.hollowhorizon.hollowengine.common.utils.ForgeKotlinKt;

import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(Main.class)
public class DebugMainMixin {
    @Inject(method = "main", at = @At("HEAD"), remap = false)
    private static void preMain(CallbackInfo ci) {
        ConsoleAppender.attach();
        HollowLoggerKt.getLOGGER().info("Production: {}, Can attach renderdoc: {}, Platform: {}", ForgeKotlinKt.isProduction(), HollowCoreLoader.canAttachRenderdoc(), Util.getPlatform().name());
        if (!HollowCoreLoader.canAttachRenderdoc() || Util.getPlatform() != Util.OS.WINDOWS) {
            return;
        }

        String path = System.getProperty("java.library.path");
        String name = System.mapLibraryName("renderdoc");
        boolean detected = false;
        for (String folder : path.split(";")) {
            if (Files.exists(Path.of(folder + "/" + name))) {
                detected = true;
                break;
            }
        }

        HollowLoggerKt.getLOGGER().info("Is detected renderdoc: {}", detected);

        if (!detected) {
            var renderDoc = Path.of("C:/Program Files/RenderDoc/renderdoc.dll");
            if(Files.exists(renderDoc)) {
                try {
                    HollowLoggerKt.getLOGGER().info("Trying load system renderdoc");
                    System.load("C:/Program Files/RenderDoc/renderdoc.dll");
                    HollowLoggerKt.getLOGGER().info("Renderdoc Loaded");
                } catch (Throwable e) {
                    HollowLoggerKt.getLOGGER().info("Failed to load Renderdoc", e);
                }
            }
            return;
        }

        try {
            System.loadLibrary("renderdoc");
            HollowLoggerKt.getLOGGER().info("Renderdoc Loaded");
        } catch (Throwable e) {
            HollowLoggerKt.getLOGGER().info("Failed to load Renderdoc", e);
        }
    }
}
