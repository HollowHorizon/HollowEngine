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

package ru.hollowhorizon.hc.client.models.internal.manager

import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.models.internal.Transform
import ru.hollowhorizon.hc.client.models.internal.animations.AnimationType
import ru.hollowhorizon.hc.client.models.internal.controller.AutoController
import ru.hollowhorizon.hc.client.models.internal.controller.Controller
import ru.hollowhorizon.hc.client.models.internal.controller.StateMachineBuilder
import ru.hollowhorizon.hc.client.models.internal.controller.animationController
import ru.hollowhorizon.hc.client.render.entity.HollowEntityRenderer
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapability
import ru.hollowhorizon.hc.common.coroutines.coroutineScope
import ru.hollowhorizon.hc.common.utils.isPhysicalClient
import ru.hollowhorizon.hc.common.utils.molang.runtime.MolangContext
import ru.hollowhorizon.hc.common.utils.rl

@HollowCapability(IAnimated::class)
class AnimatedEntityCapability : CapabilityInstance() {
    var model by syncable("%NO_MODEL%")
    val textures by syncableMap<String, String>()
    var transform by syncable(Transform())

    var controller by syncable(
        animationController {
            automatic()
            head("Head")
        }
    ) { new, old ->
        if(!isPhysicalClient) return@syncable
        Minecraft.getInstance().coroutineScope.launch {
            new.layers.find { it.name == Controller.AUTOMATIC_LAYER }?.let {
                if (model == HollowEntityRenderer.NO_MODEL) return@let
                val model = HollowModelManager.getOrCreate(model.rl)
                val stateMachine = AutoController.create(StateMachineBuilder(), AnimationType.load(model))
                it.stateMachine = stateMachine.build()
            }

            new.transferFrom(old)
        }
    }

    val molangContext by lazy { MolangContext(provider) }
}

