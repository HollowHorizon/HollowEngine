package ru.hollowhorizon.hollowengine.mixins.scripting;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import kotlin.text.StringsKt;
import org.jetbrains.kotlin.backend.common.output.OutputFile;
import org.jetbrains.kotlin.codegen.state.GenerationState;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.JvmCompilationUtilKt;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmCompiledModuleInMemoryImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hc.common.utils.ForgeKotlinKt;
import ru.hollowhorizon.hollowengine.client.gui.overlay.CompilationStatus;
import ru.hollowhorizon.hollowengine.client.gui.overlay.UpdateStatusPacket;
import ru.hollowhorizon.hollowengine.common.scripting.core.mappings.Remapping;

import java.util.Map;

@Mixin(value = JvmCompilationUtilKt.class, remap = false)
public class JvmCompilationUtilMixin {

    @Unique
    private static final Map<String, byte[]> hollowEngine$classpath = new Object2ObjectOpenHashMap<>();

    @Inject(method = "makeCompiledModule", at = @At("HEAD"))
    private static void makeCacheForClassLoading(GenerationState generationState, CallbackInfoReturnable<KJvmCompiledModuleInMemoryImpl> cir) {
        if (!ForgeKotlinKt.isProduction()) return;
        generationState.getFactory().asList().forEach(file -> hollowEngine$classpath.put(file.getRelativePath(), file.asByteArray()));
    }

    @Inject(method = "makeCompiledModule", at = @At("TAIL"))
    private static void clearCacheForClassLoading(GenerationState generationState, CallbackInfoReturnable<KJvmCompiledModuleInMemoryImpl> cir) {
        if (!ForgeKotlinKt.isProduction()) return;
        hollowEngine$classpath.clear();
    }

    @Redirect(method = "makeCompiledModule", at = @At(value = "INVOKE", target = "Lorg/jetbrains/kotlin/backend/common/output/OutputFile;asByteArray()[B"))
    private static byte[] makeCompiledModule(OutputFile instance) {
        if (!instance.getRelativePath().endsWith(".class") || !ForgeKotlinKt.isProduction()) return instance.asByteArray();
        var source = instance.getSourceFiles().get(0).getName();
        new UpdateStatusPacket(source, CompilationStatus.Status.OBFUSCATION).sendToOperators();
        return Remapping.remapClass(instance.asByteArray(), hollowEngine$classpath::get);
    }
}
