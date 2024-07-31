/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.scripting.story

import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.kotlinscript.common.scripting.kotlin.AbstractHollowScriptConfiguration
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.defaultImports

@KotlinScript(
    displayName = "Story Script",
    fileExtension = "se.kts",
    compilationConfiguration = StoryScriptConfiguration::class
)
abstract class StoryScript(server: MinecraftServer) : StoryStateMachine(server)

class StoryScriptConfiguration : AbstractHollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.events.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.camera.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues.sonhar.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.particles.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.world.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.nodes.players.*",
        "ru.hollowhorizon.hollowengine.common.scripting.story.*",
        "ru.hollowhorizon.hollowengine.common.scripting.*",
        "ru.hollowhorizon.hollowengine.common.npcs.*",
        "ru.hollowhorizon.hollowengine.common.util.*",
        "ru.hollowhorizon.hollowengine.common.entities.NPCEntity",
        "ru.hollowhorizon.hollowengine.client.camera.ShakeTarget",
        "ru.hollowhorizon.hc.client.models.gltf.animations.AnimationType",
        "ru.hollowhorizon.hc.client.models.gltf.manager.LayerMode",
        "ru.hollowhorizon.hc.client.models.gltf.manager.SubModel",
        "ru.hollowhorizon.hc.client.models.gltf.animations.PlayMode",
        "ru.hollowhorizon.hc.client.models.gltf.Transform",
        "ru.hollowhorizon.hc.client.utils.math.Interpolation",
        "ru.hollowhorizon.hc.common.ui.*",
        "ru.hollowhorizon.hc.common.ui.widgets.*",
        "ru.hollowhorizon.hc.common.ui.animations.*",
        "net.minecraftforge.event.*",
        "net.minecraft.core.*",
        "net.minecraft.world.item.trading.MerchantOffer",
        "ru.hollowhorizon.hc.client.utils.*",
        "net.minecraft.world.level.Level"
    )

    baseClass(StoryStateMachine::class)
})
