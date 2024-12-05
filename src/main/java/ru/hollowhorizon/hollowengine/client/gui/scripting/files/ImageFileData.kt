package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.modules.ui2.Image
import de.fabmax.kool.modules.ui2.ImageSize
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.imageSize
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.TextureProps
import de.fabmax.kool.pipeline.backend.gl.GlTexture
import de.fabmax.kool.pipeline.backend.gl.LoadedTextureGl
import net.minecraft.client.renderer.texture.DynamicTexture
import ru.hollowhorizon.hc.client.kool.MCGlApi
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2

class ImageFileData(project: IDEGuiV2, name: String, path: String, val image: DynamicTexture) : FileData(project, name, path) {
    val texture = Texture2d(TextureProps(defaultSamplerSettings = SamplerSettings().nearest())).apply {
        gpuTexture = LoadedTextureGl(MCGlApi.TEXTURE_2D, GlTexture(image.id), MCGlApi.backend, this, 0).apply {
            width = image.pixels?.width ?: 1
            height = image.pixels?.height ?: 1
        }
        loadingState = Texture.LoadingState.LOADED
    }

    override fun save() {

    }

    override fun UiScope.compose() {
        Image(texture) {
            modifier.imageSize(ImageSize.ZoomContent)
        }
    }
}