package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.Assets
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.TextureProps
import de.fabmax.kool.util.toBuffer
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2

class ImageFileData(project: IDEGuiV2, name: String, path: String, var image: ByteArray) :
    FileData(project, name, path) {


    override fun save() {

    }

    override fun UiScope.compose() {
        modifier.backgroundColor(colors.backgroundMid)

        Image(remember {
            val settings = TextureProps(defaultSamplerSettings = SamplerSettings().nearest())
            Texture2d(settings) {
                Assets.loadImageFromBuffer(image.toBuffer(), "image/png", settings)
            }
        }) {
            modifier.imageSize(ImageSize.FitContent)
                .size(Grow(0.9f), Grow(0.9f))
                .align(AlignmentX.Center, AlignmentY.Center)
        }
    }
}