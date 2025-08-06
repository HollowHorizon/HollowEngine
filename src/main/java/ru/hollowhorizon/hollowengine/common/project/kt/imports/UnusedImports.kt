package ru.hollowhorizon.hollowengine.common.project.kt.imports

import org.jetbrains.kotlin.diagnostics.DiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SimpleDiagnostic
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticRenderer
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getImportableDescriptor

val UNUSED_IMPORT_FACTORY = DiagnosticFactory0.create<KtElement>(Severity.WARNING).apply {
    initializeName("UnusedImportRenderer")
    initDefaultRenderer(object : DiagnosticRenderer<SimpleDiagnostic<KtElement>> {
        override fun render(diagnostic: SimpleDiagnostic<KtElement>): String {
            return "Unused import directive"

        }

        override fun renderParameters(diagnostic: SimpleDiagnostic<KtElement>): Array<out Any?> {
            return emptyArray()
        }

    })
}


fun collectUnusedImports(file: KtFile, trace: BindingTraceContext) {
    val references = mutableListOf<KtReferenceExpression>()
    file.accept(object : KtTreeVisitorVoid() {
        override fun visitReferenceExpression(expression: KtReferenceExpression) {
            references += expression
            super.visitReferenceExpression(expression)
        }
    })

    val usedFqNames = references.mapNotNull {
        if (it.parentsWithSelf.any { it is KtImportDirective }) return@mapNotNull null
        val target = trace[BindingContext.REFERENCE_TARGET, it]
        target?.getImportableDescriptor()?.fqNameSafe
    }.toSet()

    file.importDirectives.filter { importDirective ->
        val importedFqName = importDirective.importedFqName ?: return@filter false
        val isUsed = if (importDirective.isAllUnder) {
            // Импорт типа: `.*`
            usedFqNames.any { it.parent() == importedFqName }
        } else {
            // Обычный импорт
            importedFqName in usedFqNames
        }
        !isUsed
    }.forEach {
        trace.report(UNUSED_IMPORT_FACTORY.on(it))
    }
}
