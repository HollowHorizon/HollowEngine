package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.Assets
import de.fabmax.kool.MimeType
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.MipMapping
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.toBuffer
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid

class ImageFileData(name: String, path: String, var image: ByteArray) :
    FileData(name, path) {


    override fun save() {

    }

    override fun UiScope.compose() {
        modifier.backgroundColor(colors.backgroundMid)

        Image(remember {
            Texture2d(mipMapping = MipMapping.Off, samplerSettings = SamplerSettings().nearest()) {
                Assets.loadImageFromBuffer(image.toBuffer(), MimeType.IMAGE_PNG)
            }
        }) {
            modifier.imageSize(ImageSize.FitContent)
                .size(Grow(0.9f), Grow(0.9f))
                .align(AlignmentX.Center, AlignmentY.Center)
        }
    }
}