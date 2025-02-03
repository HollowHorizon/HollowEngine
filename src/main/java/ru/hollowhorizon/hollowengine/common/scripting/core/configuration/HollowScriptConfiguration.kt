package ru.hollowhorizon.hollowengine.common.scripting.core.configuration

//? if forge
/*import net.minecraftforge.fml.loading.FMLEnvironment*/
import ru.hollowhorizon.hc.client.utils.isProduction
import ru.hollowhorizon.hollowengine.common.scripting.core.Import
import ru.hollowhorizon.hollowengine.common.scripting.core.deobfClasspath
import ru.hollowhorizon.hollowengine.common.scripting.core.scriptingClasspath
import java.io.File
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

        val jars = scriptingClasspath + deobfClasspath
        val deobfNames = jars.map { it.name }
        val originalClasspath = System.getProperty("java.class.path").split(";")
            .map { File(it) }
            .toMutableSet()
        val filteredClasspath = originalClasspath.filter { it.name !in deobfNames }


        //? if forge || neoforge {
        /*if (!FMLEnvironment.production) {
            updateClasspath(System.getProperty("java.class.path").split(";").map { File(it) }.toMutableSet())
            dependenciesFromCurrentContext(wholeClasspath = true)
            return@jvm
        }
        *///?}

        updateClasspath(jars + filteredClasspath)
        if(true || !isProduction) dependenciesFromCurrentContext(wholeClasspath = true)
    }

    defaultImports(Import::class)

    refineConfiguration {
        onAnnotations(Import::class, handler = HollowScriptConfigurator())
    }

    ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }
})