package ru.hollowhorizon.hollowengine.common.compiler.configuration

import ru.hollowhorizon.hollowengine.common.compiler.Import
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.utils.isProduction
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath

open class HollowScriptConfiguration(body: Builder.() -> Unit = {}) : ScriptCompilationConfiguration({
    body()

    jvm {
        compilerOptions(
            "-opt-in=kotlin.time.ExperimentalTime,kotlin.ExperimentalStdlibApi",
            "-jvm-target=17",
            "-Xadd-modules=ALL-MODULE-PATH" // Loading kotlin from shadowed jar
        )

        updateClasspath(ScriptingEnvironment.INSTANCE.classpath)
        if(!isProduction) dependenciesFromCurrentContext(wholeClasspath = true)
    }

    defaultImports(Import::class)

    refineConfiguration {
        onAnnotations(Import::class, handler = HollowScriptConfigurator())
    }

    ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }

})