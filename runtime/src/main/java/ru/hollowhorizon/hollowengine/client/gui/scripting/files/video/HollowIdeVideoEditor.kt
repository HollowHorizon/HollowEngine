package ru.hollowhorizon.hollowengine.client.gui.scripting.files.video

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.gui.scripting.HollowIdeOpenFile
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.percent
import ru.hollowhorizon.hollowengine.client.ui.size
import ru.hollowhorizon.hollowengine.client.ui.style.UiImageFit
import ru.hollowhorizon.hollowengine.client.ui.widgets.Video

@Composable
internal fun HollowIdeVideoEditor(file: HollowIdeOpenFile) {
    Video(
        source = file.path,
        fit = UiImageFit.CONTAIN,
        modifier = Modifier.size(100.percent, 100.percent),
    )
}
