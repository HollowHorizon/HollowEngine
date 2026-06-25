package ru.hollowhorizon.hollowengine.common.compiler.configuration

import ru.hollowhorizon.hollowengine.common.scripting.Import
import ru.hollowhorizon.hollowengine.common.utils.isProduction
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath

open class HollowScriptConfiguration(classpath: List<File>, body: Builder.() -> Unit = {}) : ScriptCompilationConfiguration({
    body()

    jvm {
        compilerOptions(
            "-opt-in=kotlin.time.ExperimentalTime,kotlin.ExperimentalStdlibApi",
            "-opt-in=org.jetbrains.kotlin.analysis.api.KaPlatformInterface",
            "-opt-in=org.jetbrains.kotlin.analysis.api.KaImplementationDetail",
            "-opt-in=org.jetbrains.kotlin.analysis.api.KaExperimentalApi",
            "-opt-in=org.jetbrains.kotlin.analysis.api.KaIdeApi",
            "-opt-in=org.jetbrains.kotlin.analysis.api.KaContextParameterApi",
            "-Xcollection-literals",
            "-jvm-target=17",
            "-Xadd-modules=ALL-MODULE-PATH" // Loading kotlin from shadowed jar
        )

        updateClasspath(classpath)
        if(!isProduction) dependenciesFromCurrentContext(wholeClasspath = true)
    }

    defaultImports(Import::class)

    refineConfiguration {
        onAnnotations(Import::class, handler = HollowScriptConfigurator())
    }

    ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }

})