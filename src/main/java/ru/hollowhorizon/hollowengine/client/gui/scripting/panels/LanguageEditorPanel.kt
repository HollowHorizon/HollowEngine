package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.lang.LanguageViewModel
import ru.hollowhorizon.hollowengine.client.lang.TranslationRow

class LanguageEditorPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.translations", dock) {
    override val icon = "hollowengine:textures/gui/icons/language.svg"
    private val vm = LanguageViewModel()

    private val sourceLangPopup = ItemPopupMenu<Unit>("SourceLangPopup")
    private val targetLangPopup = ItemPopupMenu<Unit>("TargetLangPopup")

    init {
        vm.load()
    }

    override fun UiScope.compose() {
        Column(Grow.Std, Grow.Std) {
            modifier.padding(sizes.gap)

            Row(Grow.Std, 40.dp) {
                modifier.margin(bottom = sizes.smallGap)

                LanguageButton("Из: ${vm.sourceLang.use()}", sourceLangPopup) {
                    buildLangMenu { newLang ->
                        vm.sourceLang.set(newLang)
                        vm.load()
                    }
                }

                Box(sizes.smallGap) {}
                // TODO: Kool не поддерживает эти символы, надо рисовать кодом :(
                Text("→") { modifier.alignY(AlignmentY.Center) }
                Box(sizes.smallGap) {}

                LanguageButton("В: ${vm.targetLang.use()}", targetLangPopup) {
                    buildLangMenu { newLang ->
                        vm.targetLang.set(newLang)
                        vm.load()
                    }
                }

                Box(sizes.largeGap) {}

                TextField(vm.searchQuery.use()) {
                    modifier.width(250.dp).alignY(AlignmentY.Center)
                        .hint("Поиск...")
                        .onChange {
                            vm.searchQuery.set(it)
                            vm.applyFilters()
                        }
                }

                Box(sizes.gap) {}

                Row(height = Grow.Std) {
                    modifier.onClick {
                        vm.showOnlyMissing.set(!vm.showOnlyMissing.value)
                        vm.applyFilters()
                    }
                    Checkbox(vm.showOnlyMissing.use()) { modifier.alignY(AlignmentY.Center) }
                    Text("Пустые") { modifier.alignY(AlignmentY.Center).margin(start = 4.dp) }
                }

                Box(Grow.Std) {}

                Button("Сохранить") {
                    modifier.alignY(AlignmentY.Center)
                        .padding(horizontal = sizes.gap)
                        .colors(textColor = Color.WHITE, textHoverColor = Color.WHITE)
                        .onClick { vm.save() }
                }
            }

            Row(Grow.Std) {
                modifier.backgroundColor(IdeTheme.hoveredColors.background).padding(vertical = 4.dp)
                Text("Ключ перевода") { modifier.width(Grow(0.35f)).margin(start = sizes.gap) }
                Text("Оригинал") { modifier.width(Grow(0.3f)) }
                Text("Перевод") { modifier.width(Grow(0.35f)) }
            }

            Box(Grow.Std, Grow.Std) {
                LazyColumn(Grow.Std, Grow.Std) {
                    itemsIndexed(vm.filteredTranslations) { index, row ->
                        TranslationItem(row, index % 2 == 0)
                    }
                }
            }
        }

        sourceLangPopup()
        targetLangPopup()
    }

    private fun UiScope.LanguageButton(
        label: String,
        popup: ItemPopupMenu<Unit>,
        menuBuilder: () -> SubMenuItem<Unit>,
    ) {
        Box {
            val isHovered = remember { mutableStateOf(false) }
            val color by animateColorAsState(
                if (isHovered.use()) colors.secondary.withAlpha(0.5f) else colors.secondaryVariant,
                tween(0.15f, Easing.easeOutQuart)
            )
            modifier
                .backgroundColor(color)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .alignY(AlignmentY.Center)
                .onEnter { isHovered.set(true) }
                .onExit { isHovered.set(false) }
                .onClick {
                    popup.show(Vec2f(it.screenPosition), menuBuilder(), Unit)
                }

            Text(label) { modifier.alignY(AlignmentY.Center) }
        }
    }

    private fun buildLangMenu(onSelect: (String) -> Unit): SubMenuItem<Unit> {
        return SubMenuItem("Выберите язык", null) {
            vm.getAvailableLanguages().forEach { langCode ->
                item(langCode) { onSelect(langCode) }
            }
        }
    }

    private fun UiScope.TranslationItem(
        item: TranslationRow,
        isEven: Boolean,
    ) {
        Row(Grow.Std) {
            if (isEven) modifier.backgroundColor(colors.background.withAlpha(0.8f))
            // TODO: Пожалуй стоит сделать тут ScrollPane/ScrollArea?
            //  Чтобы можно было прокручивать слишком длинные переводы, а ещё нужно подумать насчёт многострочных...

            Text(item.key) {
                modifier.width(Grow(0.35f)).padding(sizes.smallGap)
                    .textColor(
                        if (!item.targetValue.use().isEmpty()) Color.GRAY else colors.primary
                    )
            }

            Text(item.sourceValue) {
                modifier.width(Grow(0.3f)).padding(sizes.smallGap)
            }

            TextField(item.targetValue.use()) {
                modifier.width(Grow(0.35f))
                    .padding(sizes.smallGap)
                    .colors(lineColor = colors.secondaryVariant, lineColorFocused = colors.primary)
                    .onChange { item.targetValue.set(it) }
            }
        }
    }
}