package ru.hollowhorizon.hollowengine.mixins.scripting;

import kotlin.Unit;
import kotlin.script.experimental.api.ScriptCompilationConfiguration;
import kotlin.script.experimental.host.ScriptingHostConfiguration;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.CompilationContextKt;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.IgnoredOptionsReportingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hc.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompilerPluginEvent;

@Mixin(value = CompilationContextKt.class, remap = false)
public class CompilationContextMixin {
    @Inject(method = "createInitialCompilerConfiguration", at = @At("RETURN"))
    private static void modifyReturn(ScriptCompilationConfiguration scriptCompilationConfiguration, ScriptingHostConfiguration hostConfiguration, MessageCollector messageCollector, IgnoredOptionsReportingState reportingState, CallbackInfoReturnable<CompilerConfiguration> cir) {
        var configuration = cir.getReturnValue();

        var event = new ScriptingCompilerPluginEvent(plugin -> {
            configuration.add(CompilerPluginRegistrar.Companion.getCOMPILER_PLUGIN_REGISTRARS(), plugin);
            return Unit.INSTANCE;
        });
        EventBus.post(event);
    }
}
