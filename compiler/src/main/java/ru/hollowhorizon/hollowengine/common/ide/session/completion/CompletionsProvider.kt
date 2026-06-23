package ru.hollowhorizon.hollowengine.common.ide.session.completion

import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.api.projectStructure.contextModule
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.scopes.KaTypeScope
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaDeclarationContainerSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.types.Variance
import ru.hollowhorizon.hollowengine.common.ide.session.ScriptingAnalyzerImpl
import ru.hollowhorizon.hollowengine.common.ide.session.completion.util.KeywordCompletion
import ru.hollowhorizon.hollowengine.common.ide.session.kaModule
import ru.hollowhorizon.hollowengine.common.ide.session.modules.KaScriptModule
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItemTag
import ru.hollowhorizon.hollowengine.common.scripting.ide.declarationCompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.keywordCompletionItem
import ru.hollowhorizon.hollowengine.logE

fun ScriptingAnalyzerImpl.createCompletions(file: KtFile, offset: Int): List<CompletionItem> {
    val safeOffset = offset.coerceIn(0, file.textLength)
    val file = createFileForCompletion(file, safeOffset)
    val element = file.findElementAt(safeOffset)
        ?: (if (safeOffset > 0) file.findElementAt(safeOffset - 1) else null)
        ?: return emptyList()
    val ktElement = element.parentOfType<KtElement>(withSelf = true) ?: return emptyList()

    try {
        return analyze(file) {
            createItems(file, element, ktElement) ?: emptyList()
        }
    } finally {
        cleanupFile(file)
    }
}

private fun ScriptingAnalyzerImpl.createFileForCompletion(original: KtFile, offset: Int): KtFile {
    val safeOffset = offset.coerceIn(0, original.textLength)
    val textWithInsertedFakeIdentifier = original.text.replaceRange(safeOffset, safeOffset, COMPLETION_FAKE_IDENTIFIER)
    val copyKtFile =
        KtPsiFactory(original.project, markGenerated = true, eventSystemEnabled = true).createFile(
            original.name,
            textWithInsertedFakeIdentifier
        )

    copyKtFile.contextModule = KaScriptModule(
        copyKtFile,
        project,
        buildList {
            addAll(libraries)
            add(builtins.kaModule)
        },
    )
    copyKtFile.virtualFile.kaModule = copyKtFile.contextModule
    copyKtFile.originalFile = original
    return copyKtFile
}

context(kaSession: KaSession)
private fun createItems(ktFile: KtFile, element: PsiElement, parentKtElement: KtElement): List<CompletionItem>? =
    with(kaSession) {
        val position = getPosition(parentKtElement) ?: run {
            return completeKeywords(element, position = null)
        }
        val filter =
            when (val prefix = position.prefix) {
                null -> { _: Name -> true }
                else -> { name: Name -> matchesPrefix(prefix, name.asString()) }
            }

        val applicabilityChecker =
            when (position) {
                is CompletionPosition.AfterDot ->
                    position.nameExpression?.let { createExtensionCandidateChecker(ktFile, it, position.receiver) }
                        ?: return null

                is CompletionPosition.Identifier ->
                    createExtensionCandidateChecker(
                        ktFile,
                        parentKtElement as KtSimpleNameExpression,
                        explicitReceiver = null,
                    )

                else -> null
            }
        val visibilityChecker =
            when (position) {
                is CompletionPosition.AfterDot ->
                    createUseSiteVisibilityChecker(
                        ktFile.symbol,
                        position.receiver,
                        position.nameExpression ?: position.receiver
                    )

                is CompletionPosition.Identifier -> createUseSiteVisibilityChecker(ktFile.symbol, null, parentKtElement)

                is CompletionPosition.NestedType -> createUseSiteVisibilityChecker(ktFile.symbol, null, parentKtElement)

                is CompletionPosition.Type -> createUseSiteVisibilityChecker(ktFile.symbol, null, parentKtElement)

                // Для пакетов проверка видимости не актуальна
                is CompletionPosition.Package -> null
            }

        val symbolFilter: CompletionItemsCollector.SymbolFilter = when (position) {
            is CompletionPosition.Type if position.isAnnotation -> CompletionItemsCollector.SymbolFilter { symbol ->
                with(contextOf<KaSession>()) {
                    when (symbol) {
                        is KaClassSymbol -> symbol.classKind == KaClassKind.ANNOTATION_CLASS
                        is KaTypeAliasSymbol -> symbol.expandedType.expandedSymbol?.classKind == KaClassKind.ANNOTATION_CLASS
                        else -> false
                    }
                }
            }

            else -> CompletionItemsCollector.SymbolFilter { true }
        }

        val collector = CompletionItemsCollector(applicabilityChecker, visibilityChecker, filter, symbolFilter)
        with(collector) {
            when (position) {
                is CompletionPosition.AfterDot -> completeAfterDot(
                    position,
                    ktFile,
                    parentKtElement,
                    element.parentOfType<KtPackageDirective>(withSelf = true) != null
                )

                is CompletionPosition.Identifier -> {
                    collector.add(completeKeywords(parentKtElement, position))
                    completeIdentifier(ktFile, parentKtElement)

                    if ("this".startsWith(position.prefix)) completeThisKeyword(ktFile, parentKtElement)
                }

                is CompletionPosition.Type -> completeType(ktFile, parentKtElement, filter)
                is CompletionPosition.NestedType -> completeNestedType(position)
                is CompletionPosition.Package -> completePackage(ktFile, position.fqName, filter, position.onlyPackages)
            }
        }

        val elements = collector.build().distinctBy { item ->
            when (item) {
                is CompletionItem.Declaration -> item.copy(import = false)
                is CompletionItem.Keyword -> item
            }
        }

        if (elements.isEmpty()) return null
        return elements
    }

context(kaSession: KaSession)
private fun CompletionItemsCollector.completeThisKeyword(file: KtFile, element: KtElement) = with(kaSession) {
    val scopeContext = file.scopeContext(element)

    var first = true
    for (implicitReceiver in scopeContext.implicitReceivers) {
        val receiverType = implicitReceiver.type
        val typeText = receiverType.render(position = Variance.IN_VARIANCE)
        val className = implicitReceiver.ownerSymbol.name?.asString() ?: "Unknown"

        val thisName = if (first) "this" else "this@$className"

        add(listOf(declarationCompletionItem {
            show = thisName
            name = thisName
            insert = thisName
            tail = typeText
            tag = CompletionItemTag.KEYWORD
            import = false
        }))

        first = false
    }

    completeQualifiedThis(file, element)
}

context(kaSession: KaSession)
private fun CompletionItemsCollector.completeQualifiedThis(file: KtFile, element: KtElement) = with(kaSession) {
    // Находим все родительские declaration'ы, которые могут иметь this
    var current: PsiElement? = element
    while (current != null) {
        when (current) {
            is KtClassOrObject -> {
                val classSymbol = current.symbol as? KaClassSymbol
                classSymbol?.let { symbol ->
                    val className = symbol.name?.asString() ?: "Unknown"
                    val typeText = symbol.defaultType.render(position = Variance.IN_VARIANCE)

                    add(listOf(declarationCompletionItem {
                        show = "this@$className"
                        name = "this@$className"
                        insert = "this@$className"
                        tail = typeText
                        import = false
                        tag = CompletionItemTag.KEYWORD

                    }))
                }
            }

            is KtFunction -> {
                val functionName = current.name ?: "lambda"
                add(listOf(declarationCompletionItem {
                    show = "this@$functionName"
                    name = "this@$functionName"
                    insert = "this@$functionName"
                    import = false
                    tail = " (qualified this for function $functionName)"
                    tag = CompletionItemTag.KEYWORD

                }))
            }
        }
        current = current.parent
    }
}

private fun completeKeywords(
    element: PsiElement,
    position: CompletionPosition?,
): List<CompletionItem.Keyword> {
    runCatching {
        val matcher = when {
            position == null -> when {
                element.tokenType == KtTokens.IDENTIFIER -> {
                    element.text.removeSuffix(COMPLETION_FAKE_IDENTIFIER).let(::PlainPrefixMatcher)
                }

                else -> return emptyList()
            }

            else -> position.prefix?.let(::PlainPrefixMatcher) ?: PrefixMatcher.ALWAYS_TRUE
        }
        val result = mutableListOf<CompletionItem.Keyword>()
        KeywordCompletion().complete(
            element,
            matcher,
            isJvmModule = true,
        ) { lookupElement ->
            val presentation = LookupElementPresentation().also { lookupElement.renderElement(it) }
            val text = presentation.itemText ?: lookupElement.lookupString

            // Добавляем специальную обработку для ключевого слова "this"
            if (text == "this") {
                return@complete
            }

            result += keywordCompletionItem {
                textToShow = text.trim()
                name = text.trim()
                textToInsert = text
            }
        }
        return result
    }.getOrHandleException { logE(it) }

    return emptyList() // do not fail the whole completion when one item fails, not well tested
}

context(kaSession: KaSession)
private fun CompletionItemsCollector.completeAfterDot(
    position: CompletionPosition.AfterDot,
    file: KtFile,
    element: KtElement,
    onlyPackages: Boolean,
) = with(kaSession) {
    val receiverTarget = when (val receiver = position.receiver) {
        is KtDotQualifiedExpression -> receiver.selectorExpression?.mainReference?.resolveToSymbol()
        else -> receiver.mainReference?.resolveToSymbol()
    }

    when (receiverTarget) {
        is KaClassSymbol -> {
            completeStaticMembers(receiverTarget)

            if (receiverTarget.classKind == KaClassKind.OBJECT) {
                completeScope(receiverTarget.combinedMemberScope)
            }

            receiverTarget.staticDeclaredMemberScope.classifiers
                .filterIsInstance<KaClassSymbol>()
                .firstOrNull { it.classKind == KaClassKind.COMPANION_OBJECT }
                ?.let { completeScope(it.combinedMemberScope) }
        }

        is KaPackageSymbol -> {
            completePackage(file, receiverTarget.fqName, nameFilter, onlyPackages)
        }

        else -> {
            val receiverType = position.receiver.expressionType

            if(receiverType == null || receiverType is org.jetbrains.kotlin.analysis.api.types.KaErrorType) return@with

            receiverType.scope?.let { completeTypeScope(it) }

            if (position.prefix != null) {
                val scopeContext = file.scopeContext(element)
                for (scope in scopeContext.scopes) {
                    add(scope.scope.callables(nameFilter).filterShadowedJavaFields().filter { it.isExtension })
                }
            }

            add(getKotlinDeclarationsFromIndex(file, nameFilter).filter { symbol ->
                symbol is KaCallableSymbol && symbol.isExtension
            }) { withImport() }
        }
    }
}

context(kaSession: KaSession)
private fun CompletionItemsCollector.completeStaticMembers(kaClassSymbol: KaClassSymbol) = with(kaSession) {
    add(kaClassSymbol.staticDeclaredMemberScope.callables(nameFilter).filterShadowedJavaFields())
}

context(_: KaSession)
private fun CompletionItemsCollector.completeIdentifier(file: KtFile, element: KtElement) {
    completeScopeContext(file, element)
    for (declaration in getKotlinDeclarationsFromIndex(file, nameFilter)) {
        add(declaration) { withImport() }
    }
}

context(kaSession: KaSession)
private fun CompletionItemsCollector.completeScopeContext(file: KtFile, element: KtElement) = with(kaSession) {
    val scopeContext = file.scopeContext(element)
    for (scope in scopeContext.scopes) {
        add(scope.scope.callables(nameFilter).filterShadowedJavaFields())
        add(scope.scope.classifiers(nameFilter))
    }
    for (implicitReceiver in scopeContext.implicitReceivers) {
        implicitReceiver.type.scope?.let { completeTypeScope(it) }
    }
}

context(session: KaSession)
private fun getKotlinDeclarationsFromIndex(ktFile: KtFile, filter: (Name) -> Boolean): Sequence<KaDeclarationSymbol> {
    with(session) {
        val factory = KotlinDeclarationProviderFactory.getInstance(ktFile.project)
        val declarationProvider =
            factory.createDeclarationProvider(analysisScope, useSiteModule)
        val kotlinClasses = sequence {
            for (packageName in declarationProvider.computePackageNamesWithTopLevelCallables()!!) {
                val packageFqName = FqName(packageName)
                for (name in declarationProvider.getTopLevelCallableNamesInPackage(packageFqName)) {
                    if (!filter(name)) continue
                    val callableId = CallableId(packageFqName, name)
                    declarationProvider.getTopLevelFunctions(callableId).forEach { yield(it.symbol) }
                    declarationProvider.getTopLevelProperties(callableId).forEach { yield(it.symbol) }
                }
            }

            for (packageName in declarationProvider.computePackageNamesWithTopLevelClassifiers()!!) {
                val packageFqName = FqName(packageName)
                for (name in declarationProvider.getTopLevelKotlinClassLikeDeclarationNamesInPackage(packageFqName)) {
                    if (!filter(name)) continue
                    val classId = ClassId(packageFqName, name)
                    declarationProvider.getClassLikeDeclarationByClassId(classId)?.let { yield(it.symbol) }
                }
            }
        }

        return kotlinClasses
    }
}


context(kaSession: KaSession)
private fun CompletionItemsCollector.completePackage(
    ktFile: KtFile,
    fqName: FqName,
    filter: (Name) -> Boolean,
    onlyPackages: Boolean,
) = with(kaSession) {
    val subPackageNames = mutableSetOf<String>()

    val javaPsiFacade = runCatching { JavaPsiFacade.getInstance(ktFile.project) }.getOrNull()
    if (javaPsiFacade != null) {
        val psiPackage = javaPsiFacade.findPackage(fqName.asString())
        psiPackage?.subPackages?.forEach { subPkg ->
            val name = subPkg.name
            if (name != null && filter(Name.identifier(name))) {
                subPackageNames.add(name)
            }
        }
    }

    val declarationProvider = KotlinDeclarationProviderFactory.getInstance(ktFile.project)
        .createDeclarationProvider(analysisScope, useSiteModule)

    val allKotlinPackages = (declarationProvider.computePackageNamesWithTopLevelCallables().orEmpty() +
            declarationProvider.computePackageNamesWithTopLevelClassifiers().orEmpty())

    val parentSegmentsSize = fqName.pathSegments().size
    allKotlinPackages.asSequence()
        .map { FqName(it) }
        .filter { it != fqName && !it.isRoot && (fqName.isRoot || it.startsWith(fqName)) }
        .mapNotNull { it.pathSegments().getOrNull(parentSegmentsSize)?.asString() }
        .filter { filter(Name.identifier(it)) }
        .forEach { subPackageNames.add(it) }

    for (pkgName in subPackageNames) {
        add(listOf(declarationCompletionItem {
            name = pkgName
            show = pkgName
            insert = pkgName
            tag = CompletionItemTag.KEYWORD
            import = false
        }))
    }


    if (!onlyPackages && !fqName.isRoot) {
        val packageSymbol = findPackage(fqName)

        if (packageSymbol != null) {
            val classifiers = packageSymbol.packageScope.classifiers(filter)
            add(classifiers) { import = false }

            val callables = packageSymbol.packageScope.callables(filter)
            add(callables) { import = false }
        }
    }
}


context(kaSession: KaSession)
private fun CompletionItemsCollector.completeType(file: KtFile, element: KtElement, nameFilter: (Name) -> Boolean) =
    with(kaSession) {
        for (scope in file.scopeContext(element).scopes) {
            add(scope.scope.classifiers(nameFilter))
        }
    }

context(kaSession: KaSession)
private fun CompletionItemsCollector.completeNestedType(position: CompletionPosition.NestedType) = with(kaSession) {
    val classifiers =
        when (val symbol = position.receiver.referenceExpression?.mainReference?.resolveToSymbol()) {
            is KaTypeAliasSymbol -> symbol.expandedType.scope?.getClassifierSymbols(nameFilter)
            is KaDeclarationContainerSymbol -> symbol.combinedMemberScope.classifiers(nameFilter)
            // Поддержка вложенных типов через полное имя пакета в UserType
            is KaPackageSymbol -> {
                completePackage(position.receiver.containingKtFile, symbol.fqName, nameFilter, false)
                return
            }

            else -> null
        } ?: return
    add(classifiers)
}

context(_: KaSession)
private fun CompletionItemsCollector.completeTypeScope(scope: KaTypeScope) {
    add(scope.getCallableSignatures(nameFilter).filterShadowedJavaFieldsInSignatures())
    add(scope.getClassifierSymbols(nameFilter))
}

context(_: KaSession)
private fun CompletionItemsCollector.completeScope(scope: KaScope) {
    add(scope.callables(nameFilter).filterShadowedJavaFields())
    add(scope.classifiers(nameFilter))
}

context(_: KaSession)
private fun getPosition(element: KtElement): CompletionPosition? {
    // Проверка, находимся ли мы в директиве package или import
    val importDirective = element.parentOfType<KtImportDirective>(withSelf = true)
    val packageDirective = element.parentOfType<KtPackageDirective>(withSelf = true)

    if (importDirective != null || packageDirective != null) {
        // Получаем текущий контекст квалификатора
        var current: PsiElement? = element
        while (current != null && current !is KtDotQualifiedExpression && current !is KtPackageDirective && current !is KtImportDirective) {
            current = current.parent
        }

        val prefix = if (element is KtSimpleNameExpression) element.getReferencedName()
            .removeSuffix(COMPLETION_FAKE_IDENTIFIER) else null

        val fqName = if (current is KtDotQualifiedExpression) {
            current.receiverExpression.text.let(::FqName)
        } else {
            FqName.ROOT
        }

        return CompletionPosition.Package(prefix, fqName, packageDirective != null)
    }

    return when (element) {
        is KtNameReferenceExpression -> {
            when (val parent = element.parent) {
                is KtDotQualifiedExpression if parent.selectorExpression == element -> getPosition(parent)
                is KtUserType if parent.referenceExpression == element -> getPositionByUserType(parent)
                else -> CompletionPosition.Identifier(
                    element.getReferencedName().removeSuffix(COMPLETION_FAKE_IDENTIFIER)
                )
            }
        }

        is KtQualifiedExpression -> {
            val selector = element.selectorExpression
            CompletionPosition.AfterDot(
                (selector as? KtNameReferenceExpression)
                    ?.getReferencedName()
                    ?.removeSuffix(COMPLETION_FAKE_IDENTIFIER),
                element.receiverExpression,
            )
        }

        is KtUserType -> getPositionByUserType(element)

        else -> null
    }
}

private fun Sequence<KaCallableSymbol>.filterShadowedJavaFields(): Sequence<KaCallableSymbol> = sequence {
    val bufferedJavaFields = mutableListOf<KaJavaFieldSymbol>()
    val syntheticPropertyNames = hashSetOf<Name>()

    for (symbol in this@filterShadowedJavaFields) {
        when (symbol) {
            is KaSyntheticJavaPropertySymbol -> {
                syntheticPropertyNames.add(symbol.name)
                yield(symbol)
            }

            is KaJavaFieldSymbol -> {
                bufferedJavaFields.add(symbol)
            }

            else -> {
                yield(symbol)
            }
        }
    }

    for (field in bufferedJavaFields) {
        if (field.name !in syntheticPropertyNames) {
            yield(field)
        }
    }
}

private fun Sequence<KaCallableSignature<*>>.filterShadowedJavaFieldsInSignatures(): Sequence<KaCallableSignature<*>> =
    sequence {
        val bufferedJavaFields = mutableListOf<KaCallableSignature<*>>()
        val syntheticPropertyNames = hashSetOf<Name>()

        for (signature in this@filterShadowedJavaFieldsInSignatures) {
            val symbol = signature.symbol
            when (symbol) {
                is KaSyntheticJavaPropertySymbol -> {
                    syntheticPropertyNames.add(symbol.name)
                    yield(signature)
                }

                is KaJavaFieldSymbol -> {
                    bufferedJavaFields.add(signature)
                }

                else -> {
                    yield(signature)
                }
            }
        }

        for (fieldSignature in bufferedJavaFields) {
            if (fieldSignature.symbol.name !in syntheticPropertyNames) {
                yield(fieldSignature)
            }
        }
    }


private fun getPositionByUserType(element: KtUserType) =
    when (val qualifier = element.qualifier) {
        null -> CompletionPosition.Type(
            element.referencedName.orEmpty().removeSuffix(COMPLETION_FAKE_IDENTIFIER),
            element.isAnnotation()
        )

        else ->
            CompletionPosition.NestedType(
                element.referencedName.orEmpty().removeSuffix(COMPLETION_FAKE_IDENTIFIER),
                qualifier,
            )
    }

fun KtUserType.isAnnotation(): Boolean {
    val typeReference = parent as? KtTypeReference ?: return false
    val constructor = typeReference.parent as? KtConstructorCalleeExpression ?: return false
    return constructor.parent is KtAnnotationEntry
}

private sealed interface CompletionPosition {
    val prefix: String?

    class Identifier(override val prefix: String) : CompletionPosition

    class AfterDot(override val prefix: String?, val receiver: KtExpression) : CompletionPosition {
        val nameExpression: KtSimpleNameExpression?
            get() = when (val selector = (receiver.parent as KtQualifiedExpression).selectorExpression) {
                is KtSimpleNameExpression -> selector
                is KtCallExpression -> selector.getCallNameExpression()
                else -> null
            }
    }

    class Type(override val prefix: String, val isAnnotation: Boolean) : CompletionPosition

    class NestedType(override val prefix: String?, val receiver: KtUserType) : CompletionPosition

    // Новая позиция для автодополнения пакетов (в import, package или при вводе полных путей)
    class Package(override val prefix: String?, val fqName: FqName, val onlyPackages: Boolean) : CompletionPosition
}

private val javaInternalPackages = listOf(
    "sun.",
    "com.sun.",
    "apple.",
    "com.apple.",
    "com.microsoft.",
    "org.jcp.xml.dsig.internal.",
    "jdk.internal.",
    "jdk.javadoc.internal.",
    "jdk.jfr.internal.",
    "jdk.tools.jlink.internal.",
    "jdk.xml.internal.",
    "jdk.jpackage.internal.",
)
