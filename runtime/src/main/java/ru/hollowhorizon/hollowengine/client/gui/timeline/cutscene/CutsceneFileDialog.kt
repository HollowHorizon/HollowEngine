package ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions

class CutsceneFileDialog(
    private val session: CutsceneEditorSession,
) {
    private enum class Mode {
        IMPORT,
        EXPORT,
    }

    private val popup = AutoPopup(scopeName = "cutscene-file-dialog")
    private val mode = mutableStateOf(Mode.EXPORT)
    private val folder = mutableStateOf("")
    private val name = mutableStateOf("New Cutscene")
    private val selectedFile = mutableStateOf("")
    private val files = mutableStateOf(CutsceneStorage.listFiles())
    private val message = mutableStateOf("")

    init {
        popup.popupContent = Composable {
            modifier.size(Grow.Std, Grow.Std)
                .background(null)
                .onClick { popup.hide() }

            Box(Grow.Std, Grow.Std) {
                modifier
                    .align(AlignmentX.Center, AlignmentY.Center)
                    .backgroundColor(Color.BLACK.withAlpha(0.35f))

                DialogContent()
            }
        }
    }

    operator fun UiScope.invoke() {
        popup()
    }

    context(scope: UiScope)
    fun draw() = with(scope) {
        popup()
    }

    fun showExport(defaultName: String) {
        mode.set(Mode.EXPORT)
        name.set(defaultName.ifBlank { "New Cutscene" })
        folder.set("")
        selectedFile.set("")
        refreshFiles()
        popup.show(Vec2f.ZERO)
    }

    fun showImport() {
        mode.set(Mode.IMPORT)
        folder.set("")
        name.set("")
        selectedFile.set(files.value.firstOrNull().orEmpty())
        refreshFiles()
        popup.show(Vec2f.ZERO)
    }

    private fun refreshFiles() {
        files.set(CutsceneStorage.listFiles())
        if (selectedFile.value !in files.value) {
            selectedFile.set(files.value.firstOrNull().orEmpty())
        }
        message.set("")
    }

    private fun UiScope.DialogContent() {
        val currentMode = mode.use()

        Column {
            modifier
                .align(AlignmentX.Center, AlignmentY.Center)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingMedium))
                .border(
                    RoundRectBorder(
                        ColorTheme.UI.BackgroundAccent,
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall
                    )
                )
                .padding(Dimensions.PaddingMedium)

            Text(if (currentMode == Mode.EXPORT) "Export cutscene" else "Import cutscene") {
                modifier
                    .margin(Dimensions.PaddingNormal)
                    .textColor(ColorTheme.UI.WhiteReplacement)
            }

            if (currentMode == Mode.EXPORT) {
                Field("Folder", folder.use(), "subfolder or empty") { folder.set(it) }
                Field("Name", name.use(), "cutscene name") { name.set(it) }
                Text("hollowengine/${CutsceneStorage.ROOT_READABLE_PATH}") {
                    modifier
                        .margin(horizontal = Dimensions.PaddingNormal, vertical = Dimensions.PaddingSmall)
                        .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.6f))
                }
            } else {
                FilePicker()
            }

            val currentMessage = message.use()
            if (currentMessage.isNotBlank()) {
                Text(currentMessage) {
                    modifier
                        .width(Grow.Std)
                        .margin(Dimensions.PaddingNormal)
                        .textColor(ColorTheme.Console.Error)
                        .isWrapText(true)
                }
            }

            Actions(currentMode)
        }
    }

    private fun UiScope.Field(
        label: String,
        value: String,
        hint: String,
        onChange: (String) -> Unit,
    ) {
        Column(width = Grow.Std) {
            modifier.margin(Dimensions.PaddingNormal)

            Text(label) {
                modifier
                    .margin(bottom = Dimensions.PaddingSmall)
                    .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.75f))
            }

            TextField(value) {
                modifier
                    .size(Grow.Std, FitContent)
                    .padding(Dimensions.PaddingNormal)
                    .hint(hint)
                    .colors(
                        textColor = ColorTheme.UI.WhiteReplacement,
                        lineColor = ColorTheme.UI.BackgroundElements.withAlpha(0.5f),
                        lineColorFocused = ColorTheme.UI.BackgroundAccent.withAlpha(0.75f),
                    )
                    .onChange(onChange)
            }
        }
    }

    private fun UiScope.FilePicker() {
        Text("hollowengine/${CutsceneStorage.ROOT_READABLE_PATH}") {
            modifier
                .margin(Dimensions.PaddingNormal)
                .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.75f))
        }

        ScrollArea(
            containerModifier = {
                it.height(Grow(1f, max = 240.dp))
                    .background(RoundRectBackground(ColorTheme.UI.BackgroundGeneral, Dimensions.PaddingNormal))
            },
            withHorizontalScrollbar = true,
        ) {
            modifier
                .width(Grow.Std)
                .padding(Dimensions.PaddingNormal)

            val availableFiles = files.use()
            val selected = selectedFile.use()
            if (availableFiles.isEmpty()) {
                Text("No cutscenes found") {
                    modifier
                        .margin(Dimensions.PaddingMedium)
                        .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.6f))
                }
            } else {
                Column(width = Grow.Std) {
                    availableFiles.forEach { file ->
                        FileRow(file, selected == file)
                    }
                }
            }
        }
    }

    private fun UiScope.FileRow(file: String, isSelected: Boolean) {
        Row(width = Grow.Std) {
            modifier
                .margin(vertical = Dimensions.PaddingSmall)
                .padding(Dimensions.PaddingMedium)
                .background(
                    RoundRectBackground(
                        if (isSelected) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundElements,
                        Dimensions.PaddingNormal,
                    )
                )
                .onClick { selectedFile.set(file) }

            Text(file) {
                modifier
                    .alignY(AlignmentY.Center)
                    .textColor(ColorTheme.UI.WhiteReplacement)
            }
        }
    }

    private fun UiScope.Actions(currentMode: Mode) {
        Row(width = Grow.Std) {
            modifier
                .margin(Dimensions.PaddingNormal)
                .padding(top = Dimensions.PaddingNormal)

            Button(if (currentMode == Mode.EXPORT) "Export" else "Import") {
                modifier
                    .colors(
                        ColorTheme.UI.BackgroundElements,
                        ColorTheme.UI.WhiteReplacement,
                        ColorTheme.UI.BackgroundAccent,
                        Color.WHITE,
                    )
                    .onClick {
                        runCatching {
                            if (currentMode == Mode.EXPORT) {
                                session.exportCutscene(folder.value, name.value)
                                refreshFiles()
                            } else {
                                session.importCutscene(selectedFile.value)
                            }
                        }.onSuccess {
                            popup.hide()
                        }.onFailure { error ->
                            message.set(error.message ?: error::class.java.simpleName)
                        }
                    }
            }

            Box(width = Grow.Std) {}

            Button("Cancel") {
                modifier
                    .alignX(AlignmentX.End)
                    .colors(
                        ColorTheme.UI.BackgroundElements,
                        ColorTheme.UI.WhiteReplacement,
                        ColorTheme.UI.BackgroundAccent,
                        Color.WHITE,
                    )
                    .onClick { popup.hide() }
            }
        }
    }
}
