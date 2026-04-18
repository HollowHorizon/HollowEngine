package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.kool.KoolManager.MONOCRAFT
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.utils.rl

@Serializable
@SerialName("hollowengine:entity/add_effect")
class EntityAddEffectBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>()
    val duration by input<Number>()
    val amplifier by input<Number>()

    var effectRl: String = "minecraft:speed"
    var position = Vec2f.ZERO

    override suspend fun execute() {
        val effect = BuiltInRegistries.MOB_EFFECT.getHolder(effectRl.rl).orElseThrow()

        entity().addEffect(MobEffectInstance(effect, duration().toInt(), amplifier().toInt()))
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.entity_add_effect".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(entity)

        Box {
            modifier.alignY(AlignmentY.Center).margin(horizontal = Dimensions.PaddingSmall.scaled())
            TextField {
                modifier
                    .text(effectRl)
                    .width(FitContent)
                    .margin(Dimensions.PaddingNormal.scaled())
                    .font(font)
                    .onChange {
                        effectRl = it
                        position = Vec2f(uiNode.leftPx, uiNode.bottomPx)
                    }
                if (ResourceLocation.tryParse(effectRl)?.let { BuiltInRegistries.MOB_EFFECT.containsKey(it) } == true) {
                    modifier.textColor = Color.WHITE
                } else {
                    modifier.textColor = Color.RED
                }
            }

            val allEffects = BuiltInRegistries.MOB_EFFECT.keySet().map { it.toString() }
            val completions =
                allEffects.filter { it.contains(effectRl, ignoreCase = true) && it != effectRl }.sorted().take(5)

            if (completions.isNotEmpty()) {
                Popup(position.x, position.y) {
                    modifier.background(null).border(null).zLayer(UiSurface.LAYER_POPUP)
                    LazyColumn(FitContent, 350.dp) {
                        items(completions) { res ->
                            Box(Grow.Std) {
                                modifier.backgroundColor(Color("252930FF")).padding(Dimensions.PaddingSmall.scaled())
                                    .onClick { effectRl = res }
                                Text(res) {
                                    modifier.font(MsdfFont(MONOCRAFT, 18f)).textColor(Color.WHITE)
                                        .zLayer(UiSurface.LAYER_POPUP)
                                }
                            }
                        }
                    }
                }
            }
        }

        Text("hollowengine.gui.codeblocks.label.entity_effect_duration".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(duration)
        Text("hollowengine.gui.codeblocks.label.entity_effect_level".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(amplifier)
    }
}

@Serializable
@SerialName("hollowengine:entity/remove_effect")
class EntityRemoveEffectBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>()
    var effectRl: String = "minecraft:speed"
    var position = Vec2f.ZERO

    override suspend fun execute() {
       val effect = BuiltInRegistries.MOB_EFFECT.getHolder(effectRl.rl).orElseThrow()

        entity().removeEffect(effect)
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.entity_remove_effect".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_effect".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
        }

        Box {
            modifier.alignY(AlignmentY.Center).margin(horizontal = Dimensions.PaddingSmall.scaled())
            TextField {
                modifier
                    .text(effectRl)
                    .width(150.dp)
                    .onChange {
                        effectRl = it
                        position = Vec2f(uiNode.leftPx, uiNode.bottomPx)
                    }
                if (ResourceLocation.tryParse(effectRl)?.let { BuiltInRegistries.MOB_EFFECT.containsKey(it) } == true) {
                    modifier.textColor = Color.WHITE
                } else {
                    modifier.textColor = Color.RED
                }
            }

            val allEffects = BuiltInRegistries.MOB_EFFECT.keySet().map { it.toString() }
            val completions =
                allEffects.filter { it.contains(effectRl, ignoreCase = true) && it != effectRl }.sorted().take(5)

            if (completions.isNotEmpty()) {
                Popup(position.x, position.y) {
                    modifier.background(null).border(null).zLayer(UiSurface.LAYER_POPUP)
                    LazyColumn {
                        items(completions) { res ->
                            Box(Grow.Std) {
                                modifier.backgroundColor(Color("252930FF")).padding(Dimensions.PaddingSmall.scaled())
                                    .onClick { effectRl = res }
                                Text(res) { modifier.font(MsdfFont(MONOCRAFT, 18f)).textColor(Color.WHITE) }
                            }
                        }
                    }
                }
            }
        }
    }
}