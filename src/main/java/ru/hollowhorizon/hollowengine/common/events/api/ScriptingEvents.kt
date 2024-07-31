package ru.hollowhorizon.hollowengine.common.events.api

import net.minecraftforge.eventbus.api.Event
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.load.java.structure.JavaArrayType
import org.jetbrains.kotlin.load.java.structure.JavaClassifierType
import org.jetbrains.kotlin.load.java.structure.JavaPrimitiveType
import org.jetbrains.kotlin.load.java.structure.JavaType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.scopes.HierarchicalScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.utils.parentsWithSelf
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.SharedScriptCompilationContext
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import ru.hollowhorizon.hc.client.utils.JavaHacks
import ru.hollowhorizon.hollowengine.common.util.scripting.FieldDescriptor
import ru.hollowhorizon.hollowengine.common.util.scripting.MethodDescriptor
import ru.hollowhorizon.hollowengine.common.util.scripting.getMethodsAndVariables
import ru.hollowhorizon.hollowengine.common.util.scripting.simpleClassName
import ru.hollowhorizon.hollowengine.mixins.scripting.BindingTraceContextAccessor
import ru.hollowhorizon.hollowengine.mixins.scripting.SlicedMapImplAccessor
import kotlin.script.experimental.api.SourceCode

val JavaType.name: String
    get() = when (this) {
        is JavaClassifierType -> {
            classifier?.name?.asString() ?: throw ClassNotFoundException("$this is not a JavaClassifierType")
        }

        is JavaPrimitiveType -> {
            type?.typeName?.asString() ?: "Void"
        }

        is JavaArrayType -> {
            "Array<${componentType.name}>"
        }

        else -> "???"
    }

class AfterCodeAnalysisEvent(
    val context: SharedScriptCompilationContext,
    val script: SourceCode,
    val sourceFiles: List<KtFile>
) : Event()

fun completions(module: ModuleDescriptor, trace: BindingTrace, file: KtFile, cursor: Int): List<String> {
     val elements = file.findElementAt(cursor)?.parentsWithSelf?.filterIsInstance<KtExpression>() ?: return emptyList()

     val traceAccessor: BindingTraceContextAccessor = JavaHacks.forceCast(trace)
     val mapAccessor: SlicedMapImplAccessor = JavaHacks.forceCast(traceAccessor.map())

     elements.flatMap { it.parentsWithSelf }.toSet().firstIsInstanceOrNull<KtImportDirective>()?.let {
         val match = Regex("import ((\\w+\\.)*)[\\w*]*").matchEntire(it.text) ?: return@let
         val parentDot = match.groupValues[1].ifBlank { "." }
         val parent = parentDot.substring(0, parentDot.length - 1)
         val parentPackage = module.getPackage(FqName.fromSegments(parent.split('.')))
         return parentPackage.memberScope.getContributedDescriptors().map { it.name.asString() }
             .filter { it.isNotEmpty() }.sorted()
     }

     val target = elements.firstOrNull()
     if (target is KtNameReferenceExpression) {
         mapAccessor.map()[target]?.get(BindingContext.LEXICAL_SCOPE.key)?.let { scope ->
             return identifiers(scope).map { descriptor ->
                 when (descriptor) {
                     is PropertyDescriptor -> {
                         val result = descriptor.returnType?.simpleClassName ?: "???"
                         return@map FieldDescriptor(descriptor.name.asString(), result).toString()
                     }

                     is FunctionDescriptor -> {
                         if(descriptor.name.asString().startsWith("<")) return@map "???"
                         val result = descriptor.returnType?.simpleClassName ?: "???"
                         val args =
                             descriptor.valueParameters.map { it.name.asString() + ": " + it.type.simpleClassName }
                         return@map MethodDescriptor(descriptor.name.asString(), args, result).toString()
                     }

                     is ClassDescriptor -> {
                         return@map descriptor.name.asString()
                     }

                     else -> return@map descriptor.name.asString()
                 }
             }.filter { it.contains(target.text) }.sortedBy { it.indexOf(target.text) }.toList()
         }
     }

     var isStatic = false

     val elementType = elements.mapNotNull {
         val expression = mapAccessor.map()[it]?.get(BindingContext.EXPRESSION_TYPE_INFO.key)?.type
         if (expression != null) {
             return@mapNotNull TypeUtils.getClassDescriptor(expression)
         } else {
             isStatic = true
             mapAccessor.map()[it]?.get(BindingContext.QUALIFIER.key)?.descriptor as? ClassDescriptor
         }
     }.firstOrNull() ?: return emptyList()

     val completions = elementType.getMethodsAndVariables(isStatic && elementType.kind != ClassKind.OBJECT)

     return completions.first.map { it.toString() } + completions.second.map { it.toString() }
}

private fun identifiers(scope: LexicalScope): Sequence<DeclarationDescriptor> =
    scope.parentsWithSelf
        .flatMap(::scopeIdentifiers)
        .flatMap(::explodeConstructors)

private fun scopeIdentifiers(scope: HierarchicalScope): Sequence<DeclarationDescriptor> {
    val locals = scope.getContributedDescriptors().asSequence()
    val members = implicitMembers(scope)

    return locals + members
}

private fun explodeConstructors(declaration: DeclarationDescriptor): Sequence<DeclarationDescriptor> {
    return when (declaration) {
        is ClassDescriptor ->
            declaration.constructors.asSequence() + declaration

        else ->
            sequenceOf(declaration)
    }
}

private fun implicitMembers(scope: HierarchicalScope): Sequence<DeclarationDescriptor> {
    if (scope !is LexicalScope) return emptySequence()
    val implicit = scope.implicitReceiver ?: return emptySequence()

    return implicit.type.memberScope.getContributedDescriptors().asSequence()
}
