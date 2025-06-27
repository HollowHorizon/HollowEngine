package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.containingPackage
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar.TitleBarCreationEvent

object GlobalClassesIndex {
    private var isScanned = false
    val CLASSES = Object2ObjectOpenHashMap<Name, MutableSet<FqName>>()

    fun scan(moduleDescriptor: ModuleDescriptor) {
        if (isScanned || !HollowEngine.config.ideConfig.indexClasses) return
        CLASSES.clear()
        isVisible = true

        moduleDescriptor.collectGlobalClasses(
            { true },
            { processed, total -> progress = processed / total.toFloat() }
        ).forEach { classId ->
            classId.containingPackage()?.let {
                CLASSES.getOrPut(classId.name) { ObjectOpenHashSet() }.add(it)
            }
        }

        isVisible = false

        isScanned = true
    }

    private fun ModuleDescriptor.collectGlobalClasses(
        name: (Name) -> Boolean,
        onProgress: (Int, Int) -> Unit = { _, _ -> }, // (processed, total)
    ): List<ClassDescriptor> {
        val classes = mutableListOf<ClassDescriptor>()
        val visitedPackages = mutableSetOf<FqName>()
        val toVisit = ArrayDeque<FqName>()
        toVisit += FqName.ROOT

        // Сначала подсчитаем примерное число пакетов (для оценки прогресса)
        // Можно сделать более точным, если нужно, но так — достаточно эффективно
        val estimatedPackages = mutableSetOf<FqName>()
        run {
            val stack = ArrayDeque<FqName>()
            stack += FqName.ROOT
            while (stack.isNotEmpty()) {
                val current = stack.removeFirst()
                if (!estimatedPackages.add(current)) continue

                val memberScope = getPackage(current).memberScope
                memberScope.getContributedDescriptors(DescriptorKindFilter.PACKAGES).forEach { subPackage ->
                    stack += current.child(subPackage.name)
                }
            }
        }

        val totalPackages = estimatedPackages.size.coerceAtLeast(1)
        var processedPackages = 0

        while (toVisit.isNotEmpty()) {
            val packageFqName = toVisit.removeFirst()
            if (!visitedPackages.add(packageFqName)) continue

            val packageFragment = getPackage(packageFqName)
            val memberScope = packageFragment.memberScope

            // Собираем классы
            memberScope.getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS, name)
                .filterIsInstance<ClassDescriptor>()
                .forEach { classes.add(it) }

            // Добавляем вложенные пакеты
            memberScope.getContributedDescriptors(DescriptorKindFilter.PACKAGES).forEach { subPackage ->
                toVisit += packageFqName.child(subPackage.name)
            }

            // Прогресс по пакетам
            processedPackages++
            onProgress(processedPackages, totalPackages)
        }

        return classes.distinct()
    }

    fun reload() {
        isScanned = false
    }
}

internal var isVisible = false
internal var progress = 0f

@SubscribeEvent
fun onProgressRender(event: TitleBarCreationEvent.Center) {
    if(isVisible) event.append {
        Box {
            modifier.size(FitContent, Grow.Std)
                .align(AlignmentX.Center, AlignmentY.Center)
                .background(RoundRectBackground(Color("1B1E23FF"), sizes.smallGap))
                .border(RoundRectBorder(Color("394450FF"), sizes.smallGap, sizes.borderWidth))
                .padding(sizes.smallGap * 0.5f)

            Box {
                modifier.size(Grow(progress), Grow.Std)
                    .align(AlignmentX.Start, AlignmentY.Top)
                    .background(RoundRectBackground(Color("586D84FF"), sizes.smallGap))
            }

            Text("Индексация проекта...") {
                modifier.margin(horizontal=sizes.gap).font(sizes.normalText.derive(16f))
            }

            surface.triggerUpdate()
        }
    }
}