package ru.hollowhorizon.hollowengine.common.scripting.core.configuration

//? if forge
/*import net.minecraftforge.fml.loading.FMLEnvironment*/
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.common.scripting.codegen.AssetCodeGenerator
import ru.hollowhorizon.hollowengine.common.utils.isProduction
import ru.hollowhorizon.hollowengine.common.scripting.core.Import
import ru.hollowhorizon.hollowengine.common.scripting.core.deobfClasspath
import ru.hollowhorizon.hollowengine.common.scripting.core.scriptingClasspath
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext

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
        if(!isProduction) dependenciesFromCurrentContext(wholeClasspath = true)
    }

    defaultImports(Import::class)

    refineConfiguration {
        onAnnotations(Import::class, handler = HollowScriptConfigurator())
    }

    ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }

    importScripts(ImportAssets)

})

object ImportAssets: SourceCode {
    val sources by lazy { AssetCodeGenerator.generateAssetsClass(Minecraft.getInstance().resourceManager) }
    override val text: String
        get() {
            return if(Minecraft.getInstance().level == null) "" else sources
        }
    override val name = "ImportModels.kts"
    override val locationId: String? = name
}

fun classpath(): List<File> {
    val files = ArrayList<File>()

    if(!isProduction) files += scriptCompilationClasspathFromContext(
        classLoader = Thread.currentThread().contextClassLoader, wholeClasspath = true, unpackJarCollections = false
    )

    val jars = scriptingClasspath + deobfClasspath
    val deobfNames = jars.map { it.name.replace("-deobf", "") }
    val regex = Regex("""[/\\]versions[/\\][^/\\]+[/\\][^/\\]+\.jar$""")
    val classPath = System.getProperty("java.class.path")
        .split(File.pathSeparator)
        .map(::File)
        .filterNot { file -> regex.containsMatchIn(file.absolutePath) }
        .toSet()
    val filteredClasspath = classPath.filter { it.name !in deobfNames }
    files += (jars + filteredClasspath)
    return files
}