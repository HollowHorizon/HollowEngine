package ru.hollowhorizon.hollowengine.common.ide.structure

import com.intellij.mock.MockProject
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

class EngineScriptDefinitionProvider(private val project: MockProject) : ScriptDefinitionProvider {
    override fun findDefinition(script: SourceCode): ScriptDefinition {
        val ktFile = (script as KtFileScriptSource).ktFile
        val kaCellScriptModule =
            KotlinProjectStructureProvider.getModule(project, ktFile, useSiteModule = null) as KaEngineScriptModule
        return ScriptDefinition.getDefault(defaultJvmScriptingHostConfiguration)
    }

    override fun getDefaultDefinition(): ScriptDefinition = error("Should not be called")

    override fun getKnownFilenameExtensions(): Sequence<String> = sequenceOf("kts")
    override val currentDefinitions: Sequence<ScriptDefinition>
        get() = sequenceOf(ScriptDefinition.getDefault(defaultJvmScriptingHostConfiguration))

    override fun isScript(script: SourceCode): Boolean = true
}