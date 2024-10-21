package ru.hollowhorizon.hollowengine.common.scripting.core.example
//? if forge {
/*import net.minecraftforge.fml.loading.FMLEnvironment
*///?} elif neoforge {
/*import net.neoforged.fml.loading.FMLEnvironment
*///?}
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hollowengine.common.scripting.core.Import
import ru.hollowhorizon.hollowengine.common.scripting.core.deobfClasspath
import ru.hollowhorizon.hollowengine.common.scripting.core.scriptingClasspath
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.FileBasedScriptSource
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.jvm.JvmGetScriptingClass
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.classpathFromClassloader

class HollowScriptConfiguration : AbstractHollowScriptConfiguration({})

@KotlinScript(
    "HollowScript", "ks.kts", compilationConfiguration = HollowScriptConfiguration::class
)
abstract class HollowScript {
    var name = "HelloWorld"
}

abstract class AbstractHollowScriptConfiguration(body: Builder.() -> Unit) : ScriptCompilationConfiguration({
    body()

    jvm {
        compilerOptions(
            "-opt-in=kotlin.time.ExperimentalTime,kotlin.ExperimentalStdlibApi",
            "-jvm-target=17",
            "-Xadd-modules=ALL-MODULE-PATH" // Loading kotlin from shadowed jar
        )

        val jars = scriptingClasspath + deobfClasspath

        //? if forge || neoforge {
        /*if (!FMLEnvironment.production) {
            updateClasspath(System.getProperty("java.class.path").split(";").map { File(it) }.toMutableSet())
            dependenciesFromCurrentContext(wholeClasspath = true)
            return@jvm
        }
        System.setProperty("kotlin.java.stdlib.jar", jars.first { it.name == "kotlin-stdlib-2.0.0.jar" }.absolutePath)
        *///?}

        updateClasspath(jars)
        classpathFromClassloader(HollowCore::class.java.classLoader)
    }

    defaultImports(Import::class)

    refineConfiguration {
        onAnnotations(Import::class, handler = HollowScriptConfigurator())
    }

    ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }
})

class HollowScriptConfigurator : RefineScriptCompilationConfigurationHandler {
    override operator fun invoke(context: ScriptConfigurationRefinementContext) = processAnnotations(context)

    private fun processAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations = context.collectedData?.get(ScriptCollectedData.foundAnnotations)?.takeIf { it.isNotEmpty() }
            ?: return context.compilationConfiguration.asSuccess()

        val scriptBaseDir = (context.script as? FileBasedScriptSource)?.file?.parentFile

        val importedSources = annotations.flatMap {
            (it as? Import)?.files?.map { sourceName ->
                FileScriptSource(scriptBaseDir?.resolve(sourceName) ?: File(sourceName))
            } ?: emptyList()
        }

        return ScriptCompilationConfiguration(context.compilationConfiguration) {
            if (importedSources.isNotEmpty()) importScripts.append(importedSources)
        }.asSuccess()
    }
}