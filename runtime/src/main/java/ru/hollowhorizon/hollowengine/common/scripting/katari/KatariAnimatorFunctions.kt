package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.KatariFunctionDefinition
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariParameterType
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariTypes
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariValue
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.geary.api.set
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationPlayMode
import ru.hollowhorizon.hollowengine.common.geary.components.BoneMask
import ru.hollowhorizon.hollowengine.common.geary.components.LayerBlendMode

internal val KATARI_ANIMATOR = KatariParameterType("AnimatorController")

internal fun katariAnimatorFunctions(server: MinecraftServer): List<KatariFunctionDefinition> {
    return listOf(
        immediate("animatorController", signature = valueSignature().returns(KATARI_ANIMATOR)) {
            KatariAnimatorBuilder().toKatariHost()
        },
        immediate(
            "animatorController",
            signature = namedValueSignature(
                KATARI_ANIMATOR,
                KatariTypes.Boolean.param("enabled", KatariValue.Bool(true)),
            ),
        ) { args ->
            KatariAnimatorBuilder(args.getOrNull(0)?.asBool() ?: true).toKatariHost()
        },
        immediate("animator", signature = valueSignature().returns(KATARI_ANIMATOR)) {
            KatariAnimatorBuilder().toKatariHost()
        },
        immediate("setAnimator", signature = memberSignature(KATARI_ENTITY, KATARI_ANIMATOR)) { args ->
            val entity = args.receiver<Entity>("setAnimator")
            val builder = args.getOrNull(1).asHost<KatariAnimatorBuilder>("AnimatorController", "setAnimator builder")
            entity.set(builder.build())
        },
        immediate("clearAnimator", signature = memberSignature(KATARI_ANIMATOR).returns(KATARI_ANIMATOR)) { args ->
            args.receiver<KatariAnimatorBuilder>("clearAnimator").clear().toKatariHost()
        },
        immediate("removeLayer", signature = memberSignature(KATARI_ANIMATOR, KatariTypes.Text).returns(KATARI_ANIMATOR)) { args ->
            args.receiver<KatariAnimatorBuilder>("removeLayer")
                .removeLayer(args.getOrNull(1)?.asText() ?: error("removeLayer(id) expects id"))
                .toKatariHost()
        },
        clipFunction(),
        clipAdvancedFunction(),
        clipFullFunction(),
        controllerFunction(),
        controllerFullFunction(),
        stateFunction(),
        stateFullFunction(),
        transitionFunction(),
        transitionFullFunction(),
        proceduralFunction(),
        proceduralFullFunction(),
        boneTransformFunction(),
    )
}

private fun clipFunction() = immediate(
    "clip",
    signature = memberSignature(KATARI_ANIMATOR, KatariTypes.Text, KatariTypes.Text).returns(KATARI_ANIMATOR),
) { args ->
    args.receiver<KatariAnimatorBuilder>("clip")
        .clip(
            id = args.getOrNull(1)?.asText() ?: error("clip(id, animation) expects id"),
            animation = args.getOrNull(2)?.asText() ?: error("clip(id, animation) expects animation"),
        )
        .toKatariHost()
}

private fun clipAdvancedFunction() = immediate(
    "clip",
    signature = memberSignature(
        KATARI_ANIMATOR,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Int,
        KatariTypes.Text,
        KatariTypes.Double,
        KatariTypes.Double,
    ).returns(KATARI_ANIMATOR),
) { args ->
    args.receiver<KatariAnimatorBuilder>("clip")
        .clip(
            id = args.getOrNull(1)?.asText() ?: error("clip expects id"),
            animation = args.getOrNull(2)?.asText() ?: error("clip expects animation"),
            playMode = args.getOrNull(3)?.asText().orEmpty().toPlayModeOrDefault(AnimationPlayMode.Once),
            speed = args.getOrNull(4)?.asText() ?: "1",
            weight = args.getOrNull(5)?.asText() ?: "1",
            priority = args.getOrNull(6)?.asInt() ?: 0,
            blendMode = args.getOrNull(7)?.asText().orEmpty().toBlendModeOrDefault(LayerBlendMode.Override),
            fadeIn = (args.getOrNull(8)?.asDouble() ?: 0.0).toFloat(),
            fadeOut = (args.getOrNull(9)?.asDouble() ?: 0.0).toFloat(),
        )
        .toKatariHost()
}

private fun clipFullFunction() = immediate(
    "clip",
    signature = namedMemberSignature(
        receiver = KATARI_ANIMATOR,
        returnType = KATARI_ANIMATOR,
        KatariTypes.Text.param("id"),
        KatariTypes.Text.param("animation"),
        KatariTypes.Text.param("playMode", KatariValue.Text("once")),
        KatariTypes.Text.param("speed", KatariValue.Text("1")),
        KatariTypes.Text.param("weight", KatariValue.Text("1")),
        KatariTypes.Int.param("priority", KatariValue.Int32(0)),
        KatariTypes.Text.param("blendMode", KatariValue.Text("override")),
        KatariTypes.Text.param("mask", KatariValue.Text("")),
        KatariTypes.Double.param("fadeIn", KatariValue.Float64(0.0)),
        KatariTypes.Double.param("fadeOut", KatariValue.Float64(0.0)),
        KatariTypes.Text.param("referencePose", KatariValue.Text("")),
        KatariTypes.Boolean.param("removeOnEnd", KatariValue.Bool(true)),
    ),
) { args ->
    args.receiver<KatariAnimatorBuilder>("clip")
        .clip(
            id = args.getOrNull(1)?.asText() ?: error("clip expects id"),
            animation = args.getOrNull(2)?.asText() ?: error("clip expects animation"),
            playMode = args.getOrNull(3)?.asText().orEmpty().toPlayModeOrDefault(AnimationPlayMode.Once),
            speed = args.getOrNull(4)?.asText() ?: "1",
            weight = args.getOrNull(5)?.asText() ?: "1",
            priority = args.getOrNull(6)?.asInt() ?: 0,
            blendMode = args.getOrNull(7)?.asText().orEmpty().toBlendModeOrDefault(LayerBlendMode.Override),
            mask = maskOf(args.getOrNull(8)?.asText() ?: ""),
            fadeIn = (args.getOrNull(9)?.asDouble() ?: 0.0).toFloat(),
            fadeOut = (args.getOrNull(10)?.asDouble() ?: 0.0).toFloat(),
            referencePose = args.getOrNull(11)?.asText()?.takeIf(String::isNotBlank),
            removeOnEnd = args.getOrNull(12)?.asBool() ?: true,
        )
        .toKatariHost()
}

private fun controllerFunction() = immediate(
    "controller",
    signature = memberSignature(KATARI_ANIMATOR, KatariTypes.Text, KatariTypes.Text).returns(KATARI_ANIMATOR),
) { args ->
    args.receiver<KatariAnimatorBuilder>("controller")
        .controller(
            id = args.getOrNull(1)?.asText() ?: error("controller(id, entryState) expects id"),
            entryState = args.getOrNull(2)?.asText()?.takeIf(String::isNotBlank),
        )
        .toKatariHost()
}

private fun controllerFullFunction() = immediate(
    "controller",
    signature = namedMemberSignature(
        receiver = KATARI_ANIMATOR,
        returnType = KATARI_ANIMATOR,
        KatariTypes.Text.param("id"),
        KatariTypes.Text.param("entryState", KatariValue.Text("")),
        KatariTypes.Text.param("weight", KatariValue.Text("1")),
        KatariTypes.Int.param("priority", KatariValue.Int32(0)),
        KatariTypes.Text.param("blendMode", KatariValue.Text("override")),
        KatariTypes.Text.param("mask", KatariValue.Text("")),
        KatariTypes.Double.param("fadeIn", KatariValue.Float64(0.0)),
        KatariTypes.Double.param("fadeOut", KatariValue.Float64(0.0)),
    ),
) { args ->
    args.receiver<KatariAnimatorBuilder>("controller")
        .controller(
            id = args.getOrNull(1)?.asText() ?: error("controller expects id"),
            entryState = args.getOrNull(2)?.asText()?.takeIf(String::isNotBlank),
            weight = args.getOrNull(3)?.asText() ?: "1",
            priority = args.getOrNull(4)?.asInt() ?: 0,
            blendMode = args.getOrNull(5)?.asText().orEmpty().toBlendModeOrDefault(LayerBlendMode.Override),
            mask = maskOf(args.getOrNull(6)?.asText() ?: ""),
            fadeIn = (args.getOrNull(7)?.asDouble() ?: 0.0).toFloat(),
            fadeOut = (args.getOrNull(8)?.asDouble() ?: 0.0).toFloat(),
        )
        .toKatariHost()
}

private fun stateFunction() = immediate(
    "state",
    signature = memberSignature(KATARI_ANIMATOR, KatariTypes.Text, KatariTypes.Text, KatariTypes.Text, KatariTypes.Text)
        .returns(KATARI_ANIMATOR),
) { args ->
    args.receiver<KatariAnimatorBuilder>("state")
        .state(
            controllerId = args.getOrNull(1)?.asText() ?: error("state expects controller id"),
            stateId = args.getOrNull(2)?.asText() ?: error("state expects state id"),
            animation = args.getOrNull(3)?.asText() ?: error("state expects animation"),
            playMode = args.getOrNull(4)?.asText().orEmpty().toPlayModeOrDefault(AnimationPlayMode.Loop),
        )
        .toKatariHost()
}

private fun stateFullFunction() = immediate(
    "state",
    signature = namedMemberSignature(
        receiver = KATARI_ANIMATOR,
        returnType = KATARI_ANIMATOR,
        KatariTypes.Text.param("controllerId"),
        KatariTypes.Text.param("stateId"),
        KatariTypes.Text.param("animation"),
        KatariTypes.Text.param("playMode", KatariValue.Text("loop")),
        KatariTypes.Text.param("speed", KatariValue.Text("1")),
        KatariTypes.Text.param("referencePose", KatariValue.Text("")),
    ),
) { args ->
    args.receiver<KatariAnimatorBuilder>("state")
        .state(
            controllerId = args.getOrNull(1)?.asText() ?: error("state expects controller id"),
            stateId = args.getOrNull(2)?.asText() ?: error("state expects state id"),
            animation = args.getOrNull(3)?.asText() ?: error("state expects animation"),
            playMode = args.getOrNull(4)?.asText().orEmpty().toPlayModeOrDefault(AnimationPlayMode.Loop),
            speed = args.getOrNull(5)?.asText() ?: "1",
            referencePose = args.getOrNull(6)?.asText()?.takeIf(String::isNotBlank),
        )
        .toKatariHost()
}

private fun transitionFunction() = immediate(
    "transition",
    signature = memberSignature(
        KATARI_ANIMATOR,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
    ).returns(KATARI_ANIMATOR),
) { args ->
    args.receiver<KatariAnimatorBuilder>("transition")
        .transition(
            controllerId = args.getOrNull(1)?.asText() ?: error("transition expects controller id"),
            from = args.getOrNull(2)?.asText() ?: "",
            to = args.getOrNull(3)?.asText() ?: error("transition expects target state"),
            condition = args.getOrNull(4)?.asText() ?: "true",
            duration = args.getOrNull(5)?.asText() ?: "0",
        )
        .toKatariHost()
}

private fun transitionFullFunction() = immediate(
    "transition",
    signature = namedMemberSignature(
        receiver = KATARI_ANIMATOR,
        returnType = KATARI_ANIMATOR,
        KatariTypes.Text.param("controllerId"),
        KatariTypes.Text.param("from", KatariValue.Text("")),
        KatariTypes.Text.param("to"),
        KatariTypes.Text.param("condition", KatariValue.Text("true")),
        KatariTypes.Text.param("duration", KatariValue.Text("0")),
        KatariTypes.Int.param("priority", KatariValue.Int32(0)),
        KatariTypes.Double.param("exitTime", hasDefault = true),
    ),
) { args ->
    args.receiver<KatariAnimatorBuilder>("transition")
        .transition(
            controllerId = args.getOrNull(1)?.asText() ?: error("transition expects controller id"),
            from = args.getOrNull(2)?.asText() ?: "",
            to = args.getOrNull(3)?.asText() ?: error("transition expects target state"),
            condition = args.getOrNull(4)?.asText() ?: "true",
            duration = args.getOrNull(5)?.asText() ?: "0",
            priority = args.getOrNull(6)?.asInt() ?: 0,
            exitTime = args.getOrNull(7)?.asDouble()?.toFloat(),
        )
        .toKatariHost()
}

private fun proceduralFunction() = immediate(
    "procedural",
    signature = memberSignature(KATARI_ANIMATOR, KatariTypes.Text).returns(KATARI_ANIMATOR),
) { args ->
    args.receiver<KatariAnimatorBuilder>("procedural")
        .procedural(args.getOrNull(1)?.asText() ?: error("procedural(id) expects id"))
        .toKatariHost()
}

private fun proceduralFullFunction() = immediate(
    "procedural",
    signature = namedMemberSignature(
        receiver = KATARI_ANIMATOR,
        returnType = KATARI_ANIMATOR,
        KatariTypes.Text.param("id"),
        KatariTypes.Text.param("weight", KatariValue.Text("1")),
        KatariTypes.Int.param("priority", KatariValue.Int32(0)),
        KatariTypes.Text.param("blendMode", KatariValue.Text("additive")),
        KatariTypes.Text.param("mask", KatariValue.Text("")),
        KatariTypes.Double.param("fadeIn", KatariValue.Float64(0.0)),
        KatariTypes.Double.param("fadeOut", KatariValue.Float64(0.0)),
    ),
) { args ->
    args.receiver<KatariAnimatorBuilder>("procedural")
        .procedural(
            id = args.getOrNull(1)?.asText() ?: error("procedural expects id"),
            weight = args.getOrNull(2)?.asText() ?: "1",
            priority = args.getOrNull(3)?.asInt() ?: 0,
            blendMode = args.getOrNull(4)?.asText().orEmpty().toBlendModeOrDefault(LayerBlendMode.Additive),
            mask = maskOf(args.getOrNull(5)?.asText() ?: ""),
            fadeIn = (args.getOrNull(6)?.asDouble() ?: 0.0).toFloat(),
            fadeOut = (args.getOrNull(7)?.asDouble() ?: 0.0).toFloat(),
        )
        .toKatariHost()
}

private fun boneTransformFunction() = immediate(
    "boneTransform",
    signature = memberSignature(
        KATARI_ANIMATOR,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
        KatariTypes.Text,
    ).returns(KATARI_ANIMATOR),
) { args ->
    args.receiver<KatariAnimatorBuilder>("boneTransform")
        .boneTransform(
            layerId = args.getOrNull(1)?.asText() ?: error("boneTransform expects layer id"),
            bone = args.getOrNull(2)?.asText() ?: error("boneTransform expects bone"),
            translation = vectorExpression(args.textAt(3), args.textAt(4), args.textAt(5)),
            rotation = vectorExpression(args.textAt(6), args.textAt(7), args.textAt(8)),
            scale = vectorExpression(args.textAt(9, "1"), args.textAt(10, "1"), args.textAt(11, "1")),
        )
        .toKatariHost()
}

internal fun KatariAnimatorBuilder.toKatariHost() = KatariValue.HostObject("AnimatorController", this)

private fun List<KatariValue>.textAt(index: Int, default: String = "0") =
    getOrNull(index)?.asText()?.takeIf(String::isNotBlank) ?: default

private fun String.toBlendModeOrDefault(default: LayerBlendMode): LayerBlendMode = when (lowercase()) {
    "" -> default
    "add", "additive" -> LayerBlendMode.Additive
    "override", "replace" -> LayerBlendMode.Override
    else -> error("Unknown animation blend mode `$this`")
}

private fun String.toPlayModeOrDefault(default: AnimationPlayMode): AnimationPlayMode =
    if (isBlank()) default else toAnimationPlayMode()

internal fun maskOf(text: String): BoneMask {
    val bones = text.split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toTypedArray()
    return if (bones.isEmpty()) BoneMask.full() else BoneMask.of(*bones)
}
