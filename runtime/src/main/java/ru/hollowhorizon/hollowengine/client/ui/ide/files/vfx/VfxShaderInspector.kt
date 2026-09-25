package ru.hollowhorizon.hollowengine.client.ui.ide.files.vfx

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.ui.Text
import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeVfxDocument
import ru.hollowhorizon.hollowengine.client.ui.inspector.Hint
import ru.hollowhorizon.hollowengine.client.ui.inspector.InspectorButton
import ru.hollowhorizon.hollowengine.client.ui.inspector.TextRow
import ru.hollowhorizon.hollowengine.client.vfx.render.VfxShaderDeclarations
import ru.hollowhorizon.hollowengine.common.vfx.VfxCameraShakeSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxPostEffectSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxProperty
import ru.hollowhorizon.hollowengine.common.vfx.VfxSamplerSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxUniformSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxUniformValue

@Composable
internal fun PostEffectFields(document: HollowIdeVfxDocument, state: VfxEditorState, post: VfxPostEffectSpec) {
    Folding(state, "post", vfxText("section_post")) {
        TextRow(vfxText("shader"), post.shader, id = "vfx-post-shader") { typed ->
            val shader = typed.trim()
            document.replace(post.copy(shader = shader, uniforms = alignUniforms(shader, post.uniforms)))
        }
        Hint(vfxText("shader_post_hint"))
        VfxShaderFields(
            post.shader,
            post.uniforms,
            post.samplers,
            onUniforms = { document.replace(post.copy(uniforms = it)) },
            onSamplers = { document.replace(post.copy(samplers = it)) },
        )
    }
}

@Composable
internal fun CameraShakeFields(document: HollowIdeVfxDocument, state: VfxEditorState, shake: VfxCameraShakeSpec) {
    Folding(state, "shake", vfxText("section_shake")) {
        VfxValueRow(vfxText("shake_strength"), shake.strength, VfxProperty.SHAKE_STRENGTH, vfxText("shake_strength_hint")) {
            document.replace(shake.copy(strength = it))
        }
        VfxValueRow(vfxText("shake_frequency"), shake.frequency, VfxProperty.SHAKE_FREQUENCY, vfxText("shake_frequency_hint")) {
            document.replace(shake.copy(frequency = it))
        }
        VfxFloat3Row(vfxText("shake_amplitude"), shake.amplitude, hint = vfxText("shake_amplitude_hint")) {
            document.replace(shake.copy(amplitude = it))
        }
    }
}

/** [authored] brought in line with what [shader] declares, or left as it is when it cannot be read. */
internal fun alignUniforms(shader: String?, authored: List<VfxUniformSpec>): List<VfxUniformSpec> =
    shader?.let(VfxShaderDeclarations::of)?.align(authored) ?: authored

@Composable
internal fun VfxShaderFields(
    shader: String?,
    uniforms: List<VfxUniformSpec>,
    samplers: List<VfxSamplerSpec>,
    onUniforms: (List<VfxUniformSpec>) -> Unit,
    onSamplers: (List<VfxSamplerSpec>) -> Unit,
) {
    if (shader.isNullOrBlank()) return
    val declaration = VfxShaderDeclarations.of(shader)
    if (declaration == null) {
        Hint(vfxText("shader_missing"))
        return
    }

    if (declaration.uniforms.isNotEmpty()) Text(vfxText("section_uniforms"), tags = listOf("insp-inline-label"))
    declaration.uniforms.forEach { uniform ->
        val value = uniforms.firstOrNull { it.name == uniform.name && uniform.accepts(it.value) }?.value
            ?: uniform.defaultValue()
        val property = VfxProperty.uniform(uniform.name)
        fun set(next: VfxUniformValue) = onUniforms(uniforms.upsert(VfxUniformSpec(uniform.name, next)) { it.name })

        when (value) {
            is VfxUniformValue.Scalar -> VfxValueRow(uniform.name, value.value, property) {
                set(VfxUniformValue.Scalar(it))
            }

            is VfxUniformValue.Vector -> VfxVec3Row(uniform.name, value.value, property) {
                set(VfxUniformValue.Vector(it))
            }

            is VfxUniformValue.Color -> VfxColorRow(uniform.name, value.value, property) {
                set(VfxUniformValue.Color(it))
            }
        }
    }

    val stale = uniforms.filter { declaration.uniform(it.name) == null }
    if (stale.isNotEmpty()) {
        Hint(vfxText("uniforms_stale") + " " + stale.joinToString { it.name })
        InspectorButton(vfxText("uniforms_drop")) { onUniforms(uniforms - stale.toSet()) }
    }

    if (declaration.samplers.isNotEmpty()) Text(vfxText("section_samplers"), tags = listOf("insp-inline-label"))
    declaration.samplers.forEach { name ->
        val texture = samplers.firstOrNull { it.name == name }?.texture.orEmpty()
        VfxAssetRow(name, texture, listOf(".png"), id = "vfx-sampler-$name") { typed ->
            val kept = samplers.filterNot { it.name == name }
            onSamplers(if (typed.isBlank()) kept else samplers.upsert(VfxSamplerSpec(name, typed)) { it.name })
        }
    }
}

/** [item] in place of the element with the same key, or added at the end when there is none. */
private fun <T> List<T>.upsert(item: T, key: (T) -> String): List<T> {
    val index = indexOfFirst { key(it) == key(item) }
    return if (index < 0) this + item else toMutableList().also { it[index] = item }
}
