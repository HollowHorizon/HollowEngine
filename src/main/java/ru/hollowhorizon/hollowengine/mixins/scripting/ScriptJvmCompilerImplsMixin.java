package ru.hollowhorizon.hollowengine.mixins.scripting;

import kotlin.jvm.functions.Function1;
import kotlin.script.experimental.api.ResultWithDiagnostics;
import kotlin.script.experimental.api.ScriptCompilationConfiguration;
import kotlin.script.experimental.api.SourceCode;
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.scripting.compiler.plugin.dependencies.ScriptsCompilationDependencies;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptDiagnosticsMessageCollector;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptJvmCompilerImplsKt;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.SharedScriptCompilationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.common.events.api.AfterCodeAnalysisEvent;

import java.util.List;

@Mixin(value = ScriptJvmCompilerImplsKt.class, remap = false)
public class ScriptJvmCompilerImplsMixin {
    @Inject(method = "doCompile", at = @At("RETURN"))
    private static void afterAnalyze(SharedScriptCompilationContext context, SourceCode script, List<? extends KtFile> sourceFiles, List<ScriptsCompilationDependencies.SourceDependencies> sourceDependencies, ScriptDiagnosticsMessageCollector messageCollector, Function1<? super KtFile, ? extends ScriptCompilationConfiguration> getScriptConfiguration, CallbackInfoReturnable<ResultWithDiagnostics<KJvmCompiledScript>> cir) {
        final var event = new AfterCodeAnalysisEvent(context, script, sourceFiles);
        MinecraftForge.EVENT_BUS.post(event);
    }
}
