package ru.hollowhorizon.hollowengine.client.kool

import de.fabmax.kool.Assets
import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.KoolSystem
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.*
import kotlinx.serialization.ExperimentalSerializationApi
import net.minecraft.resources.ResourceLocation
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentOverlay
import ru.hollowhorizon.hollowengine.client.kool.minecraft.ImageManager
import ru.hollowhorizon.hollowengine.client.kool.minecraft.MCAssetLoader
import ru.hollowhorizon.hollowengine.client.kool.minecraft.SamplerMode
import ru.hollowhorizon.hollowengine.client.utils.stream
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat
import ru.hollowhorizon.hollowengine.common.utils.rl

@OptIn(ExperimentalSerializationApi::class)
object KoolManager {
    val LOGGER: Logger = LogManager.getLogger()

    val MONOCRAFT: MsdfFontData
    val context: MCKoolContext

    init {
        Log.printer = LogPrinter { level, tag, message ->
            LOGGER.info("[$level] $tag: $message")
        }
        KoolSystem.initialize(KoolConfigJvm(defaultAssetLoader = MCAssetLoader))

        context = MCKoolContext()
        context.addScene(ScriptingEnvironmentOverlay.scene)

        val fontInfo = JsonFormat.decodeFromStream<MsdfMeta>("hollowengine:fonts/monocraft.json".rl.stream)
        val msdfMap = Texture2d(TexFormat.RGBA, MipMapping.Off, SamplerSettings(), "MsdfFont:${fontInfo.name}") {
            Assets.loadImage2d("fonts/monocraft.png")
                .getOrDefault(SingleColorTexture.getColorTextureData(Color.BLACK))
        }
        MONOCRAFT = MsdfFontData(msdfMap, fontInfo)

        KoolInitEvent.post(KoolInitEvent())
    }
}

class KoolInitEvent : ClientEvent {
    fun loadTexture(texture: ResourceLocation, mode: SamplerMode = SamplerMode.NEAREST) {
        ImageManager.load(texture, mode)
    }

    fun attachScene(scene: Scene) {
        KoolManager.context.scenes.stageAdd(scene, 0)
    }

    companion object : EventHandler<KoolInitEvent>()
}