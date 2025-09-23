// depends-on-plugin org.jetbrains.kotlin
// depends-on-plugin com.intellij.java.ide

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import liveplugin.*
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.asJava.toLightAnnotation
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*


class ResourceLocationInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                val selector = expression.selectorExpression?.text ?: return
                if (selector != "rl") return

                val receiver = expression.receiverExpression
                if (receiver !is KtStringTemplateExpression) return

                val text = receiver.entries.joinToString("") { it.text.trim('"') }

                // Проверяем формат namespace:path
                val parts = text.split(":")
                if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                    holder.registerProblem(
                        receiver,
                        "Invalid ResourceLocation: expected namespace:path",
                        ProblemHighlightType.ERROR,
                    )
                }
            }
        }
    }
}

registerInspection(ResourceLocationInspection())

val typedAction = com.intellij.openapi.editor.actionSystem.EditorActionManager.getInstance().typedAction


fun showResourceCompletion(editor: Editor, project: Project) {
    val caretOffset = editor.caretModel.offset
    if (caretOffset <= 0) return

    val psiFile = PsiManager.getInstance(project).findFile(editor.virtualFile) ?: return
    val elementAt = psiFile.findElementAt(caretOffset - 1) ?: return

    val stringExpr = PsiTreeUtil.getParentOfType(elementAt, KtStringTemplateExpression::class.java) ?: return
    val parentDot = stringExpr.parent as? KtDotQualifiedExpression ?: return
    if (parentDot.selectorExpression?.text != "rl") return

    val base = project.basePath ?: return
    val assets = java.io.File("$base/src/main/resources/assets")
    if (!assets.exists()) return

    val elementRange = stringExpr.textRange
    val elementText = stringExpr.text // включает кавычки, например: "abc/def"
    val caretInElement = caretOffset - elementRange.startOffset
    val prefixStart = 1
    val prefixEnd = (caretInElement - 1).coerceIn(0, elementText.length) // -1 чтобы исключить кавычку
    val prefix = if (prefixEnd >= prefixStart) elementText.substring(prefixStart, prefixEnd + 1) else ""

    val candidates = assets.walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(assets).invariantSeparatorsPath.replaceFirst("/", ":") }
        .filter { it.startsWith(prefix) } // предварительная фильтрация
        .map { path ->
            LookupElementBuilder.create(path)
                .withIcon(FileTypeManager.getInstance().getFileTypeByFileName(path.substringAfterLast('/')).icon)
                .withLookupString(path)
                .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE)

        }
        .toList()

    if (candidates.isEmpty()) return

    LookupManager.getInstance(project).hideActiveLookup()

    val lookup = LookupManager.getInstance(project).showLookup(editor, candidates.toTypedArray(), prefix)
}

fun TypedAction.setupHandler(
    disposable: com.intellij.openapi.Disposable,
    handler: (Editor, Char, com.intellij.openapi.actionSystem.DataContext) -> Unit,
) {
    val wrapper = object : TypedActionHandler {
        override fun execute(
            editor: Editor,
            charTyped: Char,
            dataContext: com.intellij.openapi.actionSystem.DataContext,
        ) {
            handler(editor, charTyped, dataContext)
        }
    }
    val original = this.rawHandler
    this.setupRawHandler(wrapper)
    com.intellij.openapi.Disposable {
        this.setupRawHandler(original)
    }.also { disposable }
}

class SubscribeEventImplicitUsageProvider : ImplicitUsageProvider {
    override fun isImplicitUsage(element: PsiElement): Boolean {
        if (element is PsiMethod) {
            return element.hasAnnotation("ru.hollowhorizon.hollowengine.common.events.SubscribeEvent")
        }
        return false
    }

    override fun isImplicitRead(element: PsiElement) = false
    override fun isImplicitWrite(element: PsiElement) = false
}

ImplicitUsageProvider.EP_NAME.point.registerExtension(SubscribeEventImplicitUsageProvider(), pluginDisposable)

class SubscribeEventLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val function = element.parent as? KtFunction ?: return null
        if (element != function.nameIdentifier) return null

        if (function.isSubscribeEvent()) {

            return LineMarkerInfo(
                function.nameIdentifier!!,
                function.nameIdentifier!!.textRange,
                AllIcons.Actions.Lightning,
                { "Event subscriber" },
                null,
                GutterIconRenderer.Alignment.LEFT
            )
        }
        return null
    }
}

fun KtFunction.isSubscribeEvent(): Boolean = annotationEntries.any { it.isSubscribeEvent() }
fun KtAnnotationEntry.isSubscribeEvent(): Boolean {
    return toLightAnnotation()?.qualifiedName == "ru.hollowhorizon.hollowengine.common.events.SubscribeEvent"
}

LineMarkerProviders.getInstance()
    .addExplicitExtension(KotlinLanguage.INSTANCE, SubscribeEventLineMarkerProvider(), pluginDisposable)

class SubscribeEventInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                if (function.isSubscribeEvent()) {
                    analyze(function) {
                        val symbol = function.symbol

                        val eventClassId = ClassId.fromString(
                            "ru/hollowhorizon/hollowengine/common/events/Event"
                        )

                        symbol.receiverParameter?.let { eventParam ->
                            if (symbol.isExtension) {
                                if (symbol.valueParameters.isEmpty()) {
                                    val paramType = eventParam.returnType

                                    if (!paramType.isSubtypeOf(eventClassId)) {
                                        holder.registerProblem(
                                            function.nameIdentifier ?: function,
                                            "First parameter must implement Event",
                                            ProblemHighlightType.ERROR,
                                        )
                                    }
                                } else {
                                    holder.registerProblem(
                                        function.nameIdentifier ?: function,
                                        "Event subscriber must have only one parameter of type Event",
                                        ProblemHighlightType.ERROR,
                                    )
                                }
                                return@analyze
                            }
                        }

                        val firstParam = symbol.valueParameters.firstOrNull()
                        if (firstParam != null && symbol.valueParameters.size == 1) {
                            val paramType = firstParam.returnType

                            if (!paramType.isSubtypeOf(eventClassId)) {
                                holder.registerProblem(
                                    function.nameIdentifier ?: function,
                                    "First parameter must extend Event",
                                    ProblemHighlightType.ERROR,
                                )
                            }
                        } else {
                            holder.registerProblem(
                                function.nameIdentifier ?: function,
                                "Event subscriber must have only one parameter of type Event",
                                ProblemHighlightType.ERROR,
                            )
                        }
                    }
                }

                super.visitNamedFunction(function)
            }
        }
    }
}

registerInspection(SubscribeEventInspection())