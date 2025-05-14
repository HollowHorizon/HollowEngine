package ru.hollowhorizon.hollowengine.client.gui.npcs.dialogue

import de.fabmax.kool.scene.Scene
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.models.internal.Transform
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.common.utils.get
import ru.hollowhorizon.hollowengine.client.gui.KoolGui
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.model

object DialogueGui : KoolGui {
    private var scale = 1f
    val vitalik = NpcEntity(Minecraft.getInstance().level!!).apply {
        model = "hollowengine:models/monster.gltf"
        this[AnimatedEntityCapability::class.java].transform = Transform(sX = 0.75f, sY = 0.75f, sZ = 0.75f)
    }

    override fun Scene.setup() {
        TODO("Not yet implemented")
    }

}