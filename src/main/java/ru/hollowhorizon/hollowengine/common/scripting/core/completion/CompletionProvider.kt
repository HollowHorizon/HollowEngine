package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.editor.Document
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.TypeParameterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.asFlexibleType
import org.jetbrains.kotlin.types.isFlexible
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.codeInsight.ReferenceVariantsHelper
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.resolve.getResolutionScope
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.util.IdeDescriptorRenderersScripting
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.util.importableFqName
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.util.isVisible

class CompletionProvider(
    private val psiFiles: MutableList<KtFile>,
    filename: String,
    private val lineNumber: Int,
    private val charNumber: Int,
) {
    private val NUMBER_OF_CHAR_IN_COMPLETION_NAME = 40
    private val NUMBER_OF_CHAR_IN_TAIL = 60
    private val currentProject: Project
    private var currentPsiFile: KtFile? = null
    private var currentDocument: Document? = null
    private var caretPositionOffset: Int = 0

    private val expressionForScope: PsiElement?
        get() {
            var element = currentPsiFile!!.findElementAt(caretPositionOffset)
            if (element == null || element !is KtElement) element =
                currentPsiFile!!.findElementAt(caretPositionOffset - 1)
            while (element !is KtExpression && element != null) {
                element = element.parent
            }
            return element
        }

    init {
        psiFiles
            .filter { it.name == filename }
            .forEach { currentPsiFile = it }
        this.currentProject = currentPsiFile!!.project
        this.currentDocument = currentPsiFile!!.viewProvider.document
    }

    @Synchronized
    fun getResult(env: KotlinCoreEnvironment): Pair<AnalysisResult, List<CompletionVariant>> {
        try {
            addExpressionAtCaret()

            val resolveResult = ResolveUtils.analyzeFileForJvm(env, psiFiles, currentProject)

            val analysisResult = resolveResult.first
            val containerProvider = resolveResult.second
            val bindingContext = analysisResult.bindingContext
            val moduleDescriptor = analysisResult.moduleDescriptor
            GlobalClassesIndex.scan(moduleDescriptor)

            val element = expressionForScope as? KtElement ?: return analysisResult to emptyList()

            val descriptors = ArrayList<DeclarationDescriptor>()
            var isTipsManagerCompletion = true
            val resolutionFacade = KotlinResolutionFacade(env.project, containerProvider, moduleDescriptor)
            val inDescriptor: DeclarationDescriptor =
                element.getResolutionScope(bindingContext, resolutionFacade).ownerDescriptor

            val helper = ReferenceVariantsHelper(
                bindingContext,
                resolutionFacade,
                analysisResult.moduleDescriptor,
                VisibilityFilter(inDescriptor, bindingContext, element, resolutionFacade),
                emptySet()
            )
            val result = ArrayList<ScoredDescriptor>()

            if (element is KtSimpleNameExpression) {
                descriptors += helper.getReferenceVariants(
                    element, DescriptorKindFilter.ALL, NAME_FILTER,
                    filterOutJavaGettersAndSetters = true,
                    filterOutShadowed = true,
                    excludeNonInitializedVariable = true,
                    useReceiverType = null
                )

                val imports = element.containingKtFile.importDirectives.mapNotNull { it.importedFqName?.parentOrNull() }.toSet()

                if(element.parent !is KtDotQualifiedExpression) GlobalClassesIndex.CLASSES.keys.mapNotNull {
                    (fuzzyCamelHumpScore(element.text, it.asString()) ?: return@mapNotNull null) to it
                }.filter { it.first.score >= 0 }.forEach { (score, name) ->
                    result += GlobalClassesIndex.CLASSES[name]?.map { fullName ->
                        ScoredDescriptor(
                            score,
                            0,
                            name.asString().length,
                            0,
                            name.asString().equals(element.text, ignoreCase = true),
                            moduleDescriptor.getPackage(fullName).memberScope.getContributedClassifier(name, NoLookupLocation.FROM_IDE) ?: return@forEach,
                            if(fullName !in imports) listOf(fullName.child(name).render()) else emptyList()
                        )
                    } ?: emptyList()
                }
            } else if (element.parent is KtSimpleNameExpression) {
                descriptors += helper.getReferenceVariants(
                    element.parent as KtSimpleNameExpression,
                    DescriptorKindFilter.ALL,
                    NAME_FILTER,
                    true,
                    true,
                    true,
                    null
                )
            } else {
                isTipsManagerCompletion = false
                val resolutionScope: LexicalScope?
                val parent = if (element is KtDotQualifiedExpression) element else element.parent
                if (parent is KtQualifiedExpression) {
                    val receiverExpression = parent.receiverExpression

                    val expressionType =
                        bindingContext.get(BindingContext.EXPRESSION_TYPE_INFO, receiverExpression)?.type
                    resolutionScope = bindingContext.get(BindingContext.LEXICAL_SCOPE, receiverExpression)

                    if (expressionType != null && resolutionScope != null) {
                        descriptors += expressionType.memberScope.getContributedDescriptors(
                            DescriptorKindFilter.ALL,
                            MemberScope.ALL_NAME_FILTER
                        )
                    }

                    bindingContext.get(BindingContext.QUALIFIER, receiverExpression)?.apply {
                        val isObject = (descriptor as? ClassDescriptor)?.kind?.isObject ?: false
                        if (isObject) descriptors += classValueReceiver?.type?.memberScope
                            ?.getContributedDescriptors(DescriptorKindFilter.ALL, MemberScope.ALL_NAME_FILTER)
                            ?: emptyList()

                        staticScope.let {
                            descriptors += it.getContributedDescriptors(
                                DescriptorKindFilter.ALL,
                                MemberScope.ALL_NAME_FILTER
                            )
                        }
                    }
                } else {
                    resolutionScope = bindingContext.get(BindingContext.LEXICAL_SCOPE, element as KtExpression)
                    if (resolutionScope != null) {
                        descriptors += resolutionScope.getContributedDescriptors(
                            DescriptorKindFilter.ALL,
                            MemberScope.ALL_NAME_FILTER
                        )
                    } else {
                        return analysisResult to emptyList()
                    }
                }
            }


            var prefix = currentPsiFile!!.findElementAt(caretPositionOffset-1)?.text ?: element.text
            if(prefix == ".") prefix = ""
            result += filterCandidates(prefix, descriptors)

            return analysisResult to (sortedCandidates(result).map {
                val presentableText = getPresentableText(it.descriptor, element.isCallableReference())

                val fullName = presentableText.first
                var completionText = fullName
                var position = completionText.indexOf('(')
                if (position != -1) {
                    //If this is a string with a package after
                    if (completionText[position - 1] == ' ') {
                        position -= 2
                    }
                    //if this is a method without args
                    if (completionText[position + 1] == ')') {
                        position++
                    }
                    completionText = completionText.substring(0, position + 1)
                }
                position = completionText.indexOf(":")
                if (position != -1) {
                    completionText = completionText.substring(0, position - 1)
                }

                CompletionVariant(
                    completionText, fullName,
                    presentableText.second,
                    getIconFromDescriptor(it.descriptor),
                    it.descriptor,
                    it.matchResult.matchedIndices,
                    it.imports
                )
            } + keywordsCompletionVariants(
                KtTokens.KEYWORDS,
                prefix
            ) + keywordsCompletionVariants(KtTokens.SOFT_KEYWORDS, prefix)).distinct()

        } catch (e: Throwable) {
            throw IllegalStateException(e)
        }

    }

    private fun getIconFromDescriptor(descriptor: DeclarationDescriptor): CompletionVariant.Icon {
        return when (descriptor) {
            is FunctionDescriptor -> CompletionVariant.Icon.METHOD
            is PropertyDescriptor, is LocalVariableDescriptor -> CompletionVariant.Icon.VARIABLE
            is ClassDescriptor -> CompletionVariant.Icon.CLASS
            is PackageFragmentDescriptor, is PackageViewDescriptor -> CompletionVariant.Icon.PACKAGE
            is ValueParameterDescriptor -> CompletionVariant.Icon.VARIABLE
            is TypeParameterDescriptorImpl -> CompletionVariant.Icon.CLASS
            else -> CompletionVariant.Icon.UNKNOWN
        }
    }

    private fun formatName(builder: String, symbols: Int): String {
        return if (builder.length > symbols) {
            builder.substring(0, symbols) + "..."
        } else builder
    }

    private fun addExpressionAtCaret() {
        caretPositionOffset = getOffsetFromLineAndChar(lineNumber, charNumber)
        val text = currentPsiFile!!.text
        if (caretPositionOffset <= text.length) {
            currentDocument = currentPsiFile!!.viewProvider.document
        }
    }

    private fun getOffsetFromLineAndChar(line: Int, charNumber: Int): Int {
        val lineStart = currentDocument!!.getLineStartOffset(line)
        return lineStart + charNumber
    }

    private fun keywordsCompletionVariants(keywords: TokenSet, prefix: String): List<CompletionVariant> {
        return keywords.types
            .map { (it as KtKeywordToken).value }
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .mapTo(ArrayList()) {
                CompletionVariant(
                    it,
                    it,
                    "",
                    CompletionVariant.Icon.UNKNOWN,
                    null
                )
            }
    }


    private val RENDERER = IdeDescriptorRenderersScripting.SOURCE_CODE.withOptions {
        classifierNamePolicy = ClassifierNamePolicy.SHORT
        typeNormalizer = IdeDescriptorRenderersScripting.APPROXIMATE_FLEXIBLE_TYPES
        parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
        typeNormalizer = { kotlinType: KotlinType ->
            if (kotlinType.isFlexible()) {
                kotlinType.asFlexibleType().lowerBound
            } else kotlinType
        }
    }

    // This code is a fragment of org.jetbrains.kotlin.idea.completion.CompletionSession from Kotlin IDE Plugin
    // with a few simplifications which were possible because webdemo has very restricted environment (and well,
    // because requirements on compeltion' quality in web-demo are lower)
    private inner class VisibilityFilter(
        private val inDescriptor: DeclarationDescriptor,
        private val bindingContext: BindingContext,
        private val element: KtElement,
        private val resolutionFacade: KotlinResolutionFacade,
    ) : (DeclarationDescriptor) -> Boolean {
        override fun invoke(descriptor: DeclarationDescriptor): Boolean {
            if (descriptor is TypeParameterDescriptor && !isTypeParameterVisible(descriptor)) return false

            if (descriptor is DeclarationDescriptorWithVisibility) {
                return descriptor.isVisible(element, null, bindingContext, resolutionFacade)
            }

            if (descriptor.isInternalImplementationDetail()) return false

            return true
        }

        private fun isTypeParameterVisible(typeParameter: TypeParameterDescriptor): Boolean {
            val owner = typeParameter.containingDeclaration
            var parent: DeclarationDescriptor? = inDescriptor
            while (parent != null) {
                if (parent == owner) return true
                if (parent is ClassDescriptor && !parent.isInner) return false
                parent = parent.containingDeclaration
            }
            return true
        }

        private fun DeclarationDescriptor.isInternalImplementationDetail(): Boolean =
            importableFqName?.asString() in excludedFromCompletion
    }

    private val NAME_FILTER = { name: Name ->
        !name.isSpecial
    }

    // see DescriptorLookupConverter.createLookupElement
    private fun getPresentableText(
        descriptor: DeclarationDescriptor,
        isCallableReferenceCompletion: Boolean = false,
    ): Pair<String, String> {
        var presentableText = if (descriptor is ConstructorDescriptor)
            descriptor.constructedClass.name.asString()
        else
            descriptor.name.asString()
        var typeText = ""
        var tailText = ""

        if (descriptor is FunctionDescriptor) {
            val returnType = descriptor.returnType
            typeText = if (returnType != null) RENDERER.renderType(returnType) else ""

            if (!isCallableReferenceCompletion)
                presentableText += RENDERER.renderFunctionParameters(descriptor)

            val extensionFunction = descriptor.extensionReceiverParameter != null
            val containingDeclaration = descriptor.containingDeclaration
            if (containingDeclaration != null && extensionFunction) {
                tailText += " for " + RENDERER.renderType(descriptor.extensionReceiverParameter!!.type)
                tailText += " in " + DescriptorUtils.getFqName(containingDeclaration)
            }
        } else if (descriptor is VariableDescriptor) {
            val outType = descriptor.type
            typeText = RENDERER.renderType(outType)
        } else if (descriptor is ClassDescriptor) {
            val declaredIn = descriptor.containingDeclaration
            tailText = " (" + DescriptorUtils.getFqName(declaredIn) + ")"

            descriptor.declaredTypeParameters.map { it.name }.ifNotEmpty {
                presentableText += '<'+joinToString(", ")+'>'
            }
        } else {
            typeText = RENDERER.render(descriptor)
        }

        return if (typeText.isEmpty()) {
            Pair(presentableText, tailText)
        } else {
            Pair(presentableText, typeText)
        }
    }

    private fun KtElement.isCallableReference() =
        parent is KtCallableReferenceExpression && this == (parent as KtCallableReferenceExpression).callableReference

    companion object {
        private val excludedFromCompletion: List<String> = listOf(
            "kotlin.jvm.internal",
            "kotlin.coroutines.experimental.intrinsics",
            "kotlin.coroutines.intrinsics",
            "kotlin.coroutines.experimental.jvm.internal",
            "kotlin.coroutines.jvm.internal",
            "kotlin.reflect.jvm.internal"
        )
    }
}

var userText: String = ""