package ru.hollowhorizon.hollowengine.common.compiler.isolated;

import com.intellij.openapi.project.Project;
import kotlin.Pair;
import kotlin.script.experimental.api.ResultWithDiagnostics;
import kotlin.script.experimental.api.ScriptCompilationConfiguration;
import kotlin.script.experimental.api.SourceCode;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.scripting.compiler.plugin.dependencies.ScriptsCompilationDependencies;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.CompilationContextKt;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.JvmCompilationUtilKt;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptDiagnosticsMessageCollector;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.SharedScriptCompilationContext;

import java.util.List;

public class Hooks {
    public static SharedScriptCompilationContext createCompilationContextFromEnvironment(
            ScriptCompilationConfiguration baseScriptCompilationConfiguration,
            KotlinCoreEnvironment environment,
            ScriptDiagnosticsMessageCollector messageCollector
    ) {
        return CompilationContextKt.createCompilationContextFromEnvironment(baseScriptCompilationConfiguration, environment, messageCollector);
    }

    public static ResultWithDiagnostics<KtFile> getScriptKtFile(
            SourceCode script,
            ScriptCompilationConfiguration scriptCompilationConfiguration,
            Project project,
            ScriptDiagnosticsMessageCollector messageCollector
    ) {
        return JvmCompilationUtilKt.getScriptKtFile(script, scriptCompilationConfiguration, project, messageCollector);
    }

    public static Pair<List<KtFile>, List<ScriptsCompilationDependencies.SourceDependencies>> collectRefinedSourcesAndUpdateEnvironment(
            SharedScriptCompilationContext context,
            KtFile mainKtFile,
            ScriptCompilationConfiguration initialConfiguration,
            ScriptDiagnosticsMessageCollector messageCollector
    ) {
        return CompilationContextKt.collectRefinedSourcesAndUpdateEnvironment(context, mainKtFile, initialConfiguration, messageCollector);
    }
}
