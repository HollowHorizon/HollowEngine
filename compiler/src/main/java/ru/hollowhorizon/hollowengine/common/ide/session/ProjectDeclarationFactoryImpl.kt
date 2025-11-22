package ru.hollowhorizon.hollowengine.common.ide.session

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.impl.base.util.LibraryUtils
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinCompositeDeclarationProvider
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProvider
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinFileBasedDeclarationProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.StandaloneProjectFactory
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path

class SimpleDeclarationProviderFactory(
    private val projectEnvironment: KotlinCoreProjectEnvironment,
    private val builtins: Builtins,
    private val binaryRoots: List<Path>
) : KotlinDeclarationProviderFactory {

    // Храним список активных скриптов, чтобы искать в них объявления
    private val registeredScripts = mutableListOf<KtFile>()

    private val jdkHome = Path.of(System.getProperty("java.home"))
    @OptIn(KaImplementationDetail::class)
    private val jdkClasses = LibraryUtils.findClassesFromJdkHome(jdkHome, isJre = true)
        .ifEmpty { LibraryUtils.findClassesFromJdkHome(jdkHome, isJre = false) }

    // 1. Стандартная фабрика JetBrains для поиска в JAR-файлах (in-memory индекс)
    // Она просканирует переданные binaryRoots при создании
    private val binaryFactory = KotlinStandaloneDeclarationProviderFactory(
        projectEnvironment.project,
        projectEnvironment.environment,
        sourceKtFiles = emptyList(), // Сюда ничего не передаем, исходники обрабатываем вручную ниже
        binaryRoots = getVirtualFilesByRoots(jdkClasses + binaryRoots, projectEnvironment),
        shouldBuildStubsForBinaryLibraries = true, // Важно для скорости и корректности
        skipBuiltins = true
    )

    fun registerScript(file: KtFile) {
        registeredScripts.add(file)
    }

    override fun createDeclarationProvider(
        scope: GlobalSearchScope,
        contextualModule: KaModule?
    ): KotlinDeclarationProvider {
        val providers = mutableListOf<KotlinDeclarationProvider>()

        // 1. Добавляем провайдер для библиотек
        providers.add(binaryFactory.createDeclarationProvider(scope, contextualModule))
        providers.add(builtins.symbolProvider.createDeclarationProvider(scope, contextualModule))
        // 2. Добавляем провайдеры для скриптов
        // Проходимся по всем скриптам. Если скрипт попадает в область видимости (scope) — создаем для него провайдер.
        registeredScripts.forEach { ktFile ->
            if (scope.contains(ktFile.virtualFile)) {
                providers.add(KotlinFileBasedDeclarationProvider(ktFile))
            }
        }

        return KotlinCompositeDeclarationProvider.create(providers)
    }
}

private const val JAR_SEPARATOR = "!/"

fun getVirtualFilesByRoots(
    roots: List<Path>,
    kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment,
): List<VirtualFile> =
    StandaloneProjectFactory.getVirtualFilesForLibraryRoots(roots, kotlinCoreProjectEnvironment.environment).distinct().flatMap {
        LibraryUtils.getAllVirtualFilesFromRoot(it, includeRoot = true)
    }