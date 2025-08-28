// depends-on-plugin org.jetbrains.kotlin
// depends-on-plugin com.intellij.java.ide

import com.intellij.codeInspection.*
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import liveplugin.*
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol

class ComponentMetaInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
                val fqName = annotationEntry.shortName?.asString() ?: return
                if (fqName != "ComponentMeta") return

                val annotatedClass = annotationEntry.getStrictParentOfType<KtClass>()
                if (annotatedClass == null) {
                    holder.registerProblem(
                        annotationEntry,
                        "@ComponentMeta can only be applied to classes inheriting from Component",
                        ProblemHighlightType.ERROR
                    )
                    return
                }

                analyze(annotatedClass) {
                    val symbol = annotatedClass.classSymbol ?: return@analyze
                    val inheritsComponent = inheritsFromComponent(symbol)
                    if (!inheritsComponent) {
                        holder.registerProblem(
                            annotationEntry,
                            "Class annotated with @ComponentMeta must inherit from Component<T>",
                            ProblemHighlightType.ERROR
                        )
                        return@analyze
                    }
                }

                val valueArg = annotationEntry.valueArguments
                    .firstOrNull { it.getArgumentName()?.asName?.identifier == "location" || it.getArgumentName() == null }
                    ?.getArgumentExpression() as? KtStringTemplateExpression ?: return

                val location = valueArg.entries.joinToString("") { it.text.trim('"') }

                // Проверка формата ResourceLocation
                if (!isValidResourceLocation(location)) {
                    holder.registerProblem(
                        valueArg,
                        "Invalid ResourceLocation format. Expected 'namespace:path'",
                        ProblemHighlightType.ERROR,
                    )
                }

                // Проверка дубликатов
                val project = annotationEntry.project
                val duplicates = findAllComponentMetaLocations(project)
                    .filter { it.first != annotationEntry && it.second == location }

                if (duplicates.isNotEmpty()) {
                    holder.registerProblem(
                        valueArg,
                        "Duplicate ComponentMeta location: '$location'",
                        ProblemHighlightType.ERROR,
                    )
                }
            }
        }
    }

    private fun inheritsFromComponent(symbol: KaClassSymbol): Boolean {
        return symbol.superTypes.any {
            it.symbol?.classId?.asFqNameString() == "ru.hollowhorizon.hollowengine.common.components.Component"
        }
    }

    private fun isValidResourceLocation(value: String): Boolean {
        if (value.isEmpty()) return false
        val parts = value.split(":")
        if (parts.size != 2) return false
        val (namespace, path) = parts
        return namespace.matches(Regex("[a-z0-9._-]+")) && path.matches(Regex("[a-z0-9/._-]+"))
    }

    private fun findAllComponentMetaLocations(project: Project): List<Pair<KtAnnotationEntry, String>> {
        val result = mutableListOf<Pair<KtAnnotationEntry, String>>()
        val psiManager = PsiManager.getInstance(project)

        val scope = GlobalSearchScope.projectScope(project)
        val kotlinFiles = com.intellij.psi.search.FileTypeIndex
            .getFiles(org.jetbrains.kotlin.idea.KotlinFileType.INSTANCE, scope)

        for (vf in kotlinFiles) {
            val psiFile = psiManager.findFile(vf) as? KtFile ?: continue
            psiFile.accept(object : KtTreeVisitorVoid() {
                override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
                    val name = annotationEntry.shortName?.asString() ?: return
                    if (name != "ComponentMeta") return
                    val valueArg = annotationEntry.valueArguments.firstOrNull()
                        ?.getArgumentExpression() as? KtStringTemplateExpression ?: return
                    val location = valueArg.entries.joinToString("") { it.text.trim('"') }
                    result += annotationEntry to location
                }
            })
        }
        return result
    }
}

// Регистрируем инспекцию
if (isIdeStartup.not()) {
    registerInspection(ComponentMetaInspection())
}