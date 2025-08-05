package ru.hollowhorizon.hollowengine.common.project.kt.compiler

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.JvmPackagePartProvider
import org.jetbrains.kotlin.cli.jvm.compiler.tryLoadModuleMapping
import org.jetbrains.kotlin.cli.jvm.index.JavaRoot
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.com.intellij.util.SmartList
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.load.kotlin.JvmPackagePartProviderBase
import org.jetbrains.kotlin.metadata.jvm.deserialization.ModuleMapping
import org.jetbrains.kotlin.resolve.JvmCompilerDeserializationConfiguration

class MutableJvmPackagePartProvider(
    languageVersionSettings: LanguageVersionSettings,
    private val scope: GlobalSearchScope
): JvmPackagePartProviderBase<VirtualFile>() {
    override val deserializationConfiguration = JvmCompilerDeserializationConfiguration(languageVersionSettings)

    override val loadedModules: MutableList<ModuleMappingInfo<VirtualFile>> = SmartList()

    private var allPackageNamesCache: Set<String>? = null

    override val allPackageNames: Set<String>
        // assuming that the modifications of loadedModules happen in predictable moments now, so no synchronization is used
        get() = allPackageNamesCache
            ?: loadedModules.flatMapTo(mutableSetOf()) { it.mapping.packageFqName2Parts.keys }
                .also { allPackageNamesCache = it }

    // TODO: redesign to avoid cache-unfriendly usages, see KT-76516
    fun addRoots(roots: List<JavaRoot>, messageCollector: MessageCollector) {
        for ((root, type) in roots) {
            if (type != JavaRoot.RootType.BINARY) continue
            if (root !in scope) continue

            val metaInf = root.findChild("META-INF") ?: continue
            for (moduleFile in metaInf.children) {
                if (!moduleFile.name.endsWith(ModuleMapping.MAPPING_FILE_EXT)) continue

                tryLoadModuleMapping(
                    { moduleFile.contentsToByteArray() }, moduleFile.toString(), moduleFile.path,
                    deserializationConfiguration, messageCollector
                )?.let {
                    loadedModules.add(ModuleMappingInfo(root, it, moduleFile.nameWithoutExtension))
                    allPackageNamesCache = null
                }
            }
        }
    }
}