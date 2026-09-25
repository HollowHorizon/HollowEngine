package ru.hollowhorizon.hollowengine.client.ui.ide.files.vfx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeVfxDocument
import ru.hollowhorizon.hollowengine.client.ui.inspector.AssetPickerButton
import ru.hollowhorizon.hollowengine.client.ui.inspector.Hint
import ru.hollowhorizon.hollowengine.client.ui.inspector.LocalInspectorHost
import ru.hollowhorizon.hollowengine.client.ui.inspector.Pills
import ru.hollowhorizon.hollowengine.client.ui.inspector.TextRow
import ru.hollowhorizon.hollowengine.client.ui.inspector.ToggleRow
import ru.hollowhorizon.hollowengine.client.ui.inspector.assetCompletions
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.widgets.tooltipOnHover
import ru.hollowhorizon.hollowengine.common.vfx.*

internal val VfxModelExtensions = listOf(".glb", ".gltf", ".fbx", ".obj")
private val TextureExtensions = listOf(".png")

/** A resource path with the completions and the picker the inspector offers for assets. */
@Composable
internal fun VfxAssetRow(
    label: String,
    value: String,
    extensions: List<String>,
    hint: String? = null,
    id: String = "vfx-asset-$label",
    onChange: (String) -> Unit,
) {
    val host = LocalInspectorHost.current
    val candidates = remember(extensions, host) { host?.assets(extensions).orEmpty() }
    Row(modifier = Modifier.size(100.percent).gap(3.px).alignItems(vertical = UiAlign.CENTER)) {
        VfxFieldLabel(label, hint)
        Box(modifier = Modifier.size(0.px, UiLength.Fit).grow(1f)) {
            TextRow(
                label = "",
                value = value,
                id = id,
                completions = if (candidates.isEmpty()) null else assetCompletions(candidates),
                trailing = { AssetPickerButton(extensions, value, label, onChange) },
                onChange = onChange,
            )
        }
    }
}

@Composable
internal fun PlaneFields(document: HollowIdeVfxDocument, state: VfxEditorState, plane: VfxPlaneSpec) {
    Folding(state, "facing", vfxText("section_facing")) {
        Pills(VfxFacing.entries, plane.facing, { vfxText("facing_${it.name.lowercase()}") }) {
            document.replace(plane.copy(facing = it))
        }
        if (plane.facing == VfxFacing.CAMERA_AXIS) {
            VfxFloat3Row(vfxText("facing_axis"), plane.facingAxis, hint = vfxText("facing_axis_hint")) {
                document.replace(plane.copy(facingAxis = it))
            }
        }
    }
    SurfaceFields(document, state, plane)
}

@Composable
internal fun MeshFields(document: HollowIdeVfxDocument, state: VfxEditorState, mesh: VfxMeshSpec) {
    Folding(state, "mesh", vfxText("section_mesh")) {
        ToggleRow(vfxText("align_to_velocity"), mesh.alignToVelocity) {
            document.replace(
                when (mesh) {
                    is VfxCubeSpec -> mesh.copy(alignToVelocity = it)
                    is VfxSphereSpec -> mesh.copy(alignToVelocity = it)
                    is VfxCylinderSpec -> mesh.copy(alignToVelocity = it)
                    else -> mesh
                }
            )
        }
        when (mesh) {
            is VfxSphereSpec -> VfxIntRow(vfxText("segments"), mesh.segments, min = 4, max = 128) {
                document.replace(mesh.copy(segments = it))
            }

            is VfxCylinderSpec -> {
                VfxIntRow(vfxText("segments"), mesh.segments, min = 3, max = 128) {
                    document.replace(mesh.copy(segments = it))
                }
                ToggleRow(vfxText("caps"), mesh.caps) { document.replace(mesh.copy(caps = it)) }
            }

            else -> Unit
        }
    }
    SurfaceFields(document, state, mesh)
}

@Composable
internal fun ModelFields(document: HollowIdeVfxDocument, state: VfxEditorState, model: VfxModelSpec) {
    Folding(state, "model", vfxText("section_model")) {
        VfxAssetRow(vfxText("model"), model.model, VfxModelExtensions) { document.replace(model.copy(model = it)) }
        ToggleRow(vfxText("align_to_velocity"), model.alignToVelocity) {
            document.replace(model.copy(alignToVelocity = it))
        }
        ToggleRow(vfxText("emissive"), model.emissive) { document.replace(model.copy(emissive = it)) }
    }
}

@Composable
internal fun TrailFields(document: HollowIdeVfxDocument, state: VfxEditorState, trail: VfxTrailSpec) {
    Folding(state, "trail", vfxText("section_trail")) {
        VfxValueRow(vfxText("width"), trail.width, VfxProperty.WIDTH, vfxText("ribbon_width_hint")) {
            document.replace(trail.copy(width = it))
        }
        VfxNumberRow(vfxText("trail_lifetime"), trail.lifetime, vfxText("trail_lifetime_hint"), min = 0.01f) {
            document.replace(trail.copy(lifetime = it))
        }
        VfxNumberRow(vfxText("trail_min_distance"), trail.minDistance, vfxText("trail_min_distance_hint"), min = 0f) {
            document.replace(trail.copy(minDistance = it))
        }
        VfxIntRow(vfxText("trail_max_points"), trail.maxPoints, min = 2, max = 256) {
            document.replace(trail.copy(maxPoints = it))
        }
        RibbonUvRows(trail.uvMode, trail.tileLength, { document.replace(trail.copy(uvMode = it)) }) {
            document.replace(trail.copy(tileLength = it))
        }
    }
    SurfaceFields(document, state, trail)
}

@Composable
internal fun BeamFields(document: HollowIdeVfxDocument, state: VfxEditorState, beam: VfxBeamSpec) {
    Folding(state, "beam", vfxText("section_beam")) {
        TextRow(vfxText("beam_target"), beam.target, id = "vfx-beam-target") { document.replace(beam.copy(target = it.trim())) }
        if (beam.target.isBlank()) {
            VfxFloat3Row(vfxText("beam_end"), beam.end, VfxProperty.BEAM_END, vfxText("beam_end_hint")) {
                document.replace(beam.copy(end = it))
            }
        }
        VfxValueRow(vfxText("width"), beam.width, VfxProperty.WIDTH, vfxText("ribbon_width_hint")) {
            document.replace(beam.copy(width = it))
        }
        VfxIntRow(vfxText("segments"), beam.segments, min = 1, max = 256) { document.replace(beam.copy(segments = it)) }
        VfxValueRow(vfxText("beam_noise"), beam.noise, VfxProperty.BEAM_NOISE, vfxText("beam_noise_hint")) {
            document.replace(beam.copy(noise = it))
        }
        VfxNumberRow(vfxText("frequency"), beam.noiseFrequency, vfxText("beam_frequency_hint"), min = 0f) {
            document.replace(beam.copy(noiseFrequency = it))
        }
        VfxNumberRow(vfxText("beam_noise_speed"), beam.noiseSpeed, min = 0f) { document.replace(beam.copy(noiseSpeed = it)) }
        VfxNumberRow(vfxText("uv_scroll"), beam.uvScroll, vfxText("uv_scroll_hint")) { document.replace(beam.copy(uvScroll = it)) }
        RibbonUvRows(beam.uvMode, beam.tileLength, { document.replace(beam.copy(uvMode = it)) }) {
            document.replace(beam.copy(tileLength = it))
        }
    }
    SurfaceFields(document, state, beam)
}

@Composable
private fun RibbonUvRows(mode: VfxRibbonUv, tileLength: Float, onMode: (VfxRibbonUv) -> Unit, onTile: (Float) -> Unit) {
    Pills(VfxRibbonUv.entries, mode, { vfxText("ribbon_uv_${it.name.lowercase()}") }, onMode)
    if (mode == VfxRibbonUv.TILE) {
        VfxNumberRow(vfxText("tile_length"), tileLength, vfxText("tile_length_hint"), min = 0.01f, onChange = onTile)
    }
}

/** The tint and the material, which every surface has. */
@Composable
private fun SurfaceFields(document: HollowIdeVfxDocument, state: VfxEditorState, surface: VfxSurfaceSpec) {
    val material = surface.material
    fun update(next: VfxMaterialSpec) = document.replace(surface.withSurface(material = next))

    Folding(state, "material", vfxText("section_material")) {
        VfxColorRow(vfxText("tint"), surface.tint, VfxProperty.TINT, vfxText("tint_hint")) {
            document.replace(surface.withSurface(tint = it))
        }
        VfxAssetRow(vfxText("texture"), material.texture, TextureExtensions) { update(material.copy(texture = it)) }
        Text(vfxText("blend"), tags = listOf("insp-inline-label"))
        BlendPicker(material.blend) { update(material.copy(blend = it)) }
        ToggleRow(vfxText("lit_by_world"), material.lighting == VfxLighting.WORLD) { lit ->
            update(material.copy(lighting = if (lit) VfxLighting.WORLD else VfxLighting.UNLIT))
        }
        UvRows(material.uv) { update(material.copy(uv = it)) }
    }

    Folding(state, "depth", vfxText("section_depth"), openByDefault = false) {
        ToggleRow(vfxText("depth_test"), material.depthTest) { update(material.copy(depthTest = it)) }
        ToggleRow(vfxText("depth_write"), material.depthWrite) { update(material.copy(depthWrite = it)) }
        ToggleRow(vfxText("cull"), material.cull) { update(material.copy(cull = it)) }
    }

    Folding(state, "shader", vfxText("section_shader"), openByDefault = material.shader != null) {
        TextRow(vfxText("shader"), material.shader.orEmpty(), id = "vfx-material-shader") { typed ->
            val shader = typed.trim().ifBlank { null }
            update(material.copy(shader = shader, uniforms = alignUniforms(shader, material.uniforms)))
        }
        val attributes = when (surface) {
            is VfxMeshSpec -> "shader_mesh_hint"
            is VfxPlaneSpec -> "shader_plane_hint"
            else -> "shader_ribbon_hint"
        }
        Hint(vfxText(attributes))
        VfxShaderFields(
            material.shader,
            material.uniforms,
            material.samplers,
            onUniforms = { update(material.copy(uniforms = it)) },
            onSamplers = { update(material.copy(samplers = it)) },
        )
    }
}

@Composable
private fun UvRows(uv: VfxUvRect, onChange: (VfxUvRect) -> Unit) {
    Row(modifier = Modifier.size(100.percent).gap(3.px).alignItems(vertical = UiAlign.CENTER)) {
        VfxFieldLabel(vfxText("uv_from"), vfxText("uv_hint"))
        UvNumber("U", uv.u0) { onChange(uv.copy(u0 = it)) }
        UvNumber("V", uv.v0) { onChange(uv.copy(v0 = it)) }
    }
    Row(modifier = Modifier.size(100.percent).gap(3.px).alignItems(vertical = UiAlign.CENTER)) {
        VfxFieldLabel(vfxText("uv_to"))
        UvNumber("U", uv.u1) { onChange(uv.copy(u1 = it)) }
        UvNumber("V", uv.v1) { onChange(uv.copy(v1 = it)) }
    }
}

@Composable
private fun UvNumber(axis: String, value: Float, onChange: (Float) -> Unit) {
    Row(modifier = Modifier.size(0.px, UiLength.Fit).grow(1f).gap(2.px).alignItems(vertical = UiAlign.CENTER)) {
        Text(axis, modifier = Modifier.fontSize(8f).foreground(MutedText))
        VfxNumberCellInline(value) { onChange(it.coerceIn(0f, 1f)) }
    }
}

@Composable
private fun BlendPicker(current: VfxBlend, onChange: (VfxBlend) -> Unit) {
    Row(modifier = Modifier.size(100.percent).gap(4.px)) {
        VfxBlend.entries.forEach { blend ->
            val selected = blend == current
            Column(
                id = "vfx-blend-${blend.name.lowercase()}",
                modifier = Modifier.size(0.px, UiLength.Fit).grow(1f).gap(2.px).alignItems(horizontal = UiAlign.CENTER)
                    .input(hoverable = true, clickable = true).cursor(UiCursorShape.HAND)
                    .tooltipOnHover(vfxText("blend_${blend.name.lowercase()}_hint")).onClick { event ->
                        onChange(blend)
                        event.consume()
                    },
            ) {
                Box(
                    modifier = Modifier.size(100.percent, 26.px).drawBehind(key = blend to selected) {
                        drawBlendPreview(blend, size.width, size.height, selected)
                    },
                )
                Text(
                    vfxText("blend_${blend.name.lowercase()}"),
                    modifier = Modifier.fontSize(8f).foreground(if (selected) SelectedText else MutedText),
                )
            }
        }
    }
}

private fun UiCanvasDrawScope.drawBlendPreview(blend: VfxBlend, width: Float, height: Float, selected: Boolean) {
    val half = width / 2f
    drawRect(UiRect(0f, 0f, half, height), UiPaint.Color(DarkGround))
    drawRect(UiRect(half, 0f, width - half, height), UiPaint.Color(LightGround))

    val inset = height * 0.22f
    val left = UiRect(width * 0.2f, inset, half - width * 0.2f, height - inset * 2f)
    val right = UiRect(half, inset, half - width * 0.2f, height - inset * 2f)
    drawRect(left, UiPaint.Color(blended(blend, DarkGround)))
    drawRect(right, UiPaint.Color(blended(blend, LightGround)))

    val border = if (selected) SelectedBorder else IdleBorder
    drawRect(UiRect(0f, 0f, width, 1f), UiPaint.Color(border))
    drawRect(UiRect(0f, height - 1f, width, 1f), UiPaint.Color(border))
    drawRect(UiRect(0f, 0f, 1f, height), UiPaint.Color(border))
    drawRect(UiRect(width - 1f, 0f, 1f, height), UiPaint.Color(border))
}

/** What the sample particle leaves on [ground], worked out the way the blend state would. */
private fun blended(blend: VfxBlend, ground: UiColor): UiColor {
    val alpha = SampleParticle.alpha
    fun channel(source: Float, destination: Float): Float = when (blend) {
        VfxBlend.OPAQUE -> source
        VfxBlend.BLEND -> source * alpha + destination * (1f - alpha)
        VfxBlend.ADDITIVE -> (destination + source * alpha).coerceAtMost(1f)
        VfxBlend.MULTIPLY -> source * destination + destination * (1f - alpha)
    }
    return UiColor(
        channel(SampleParticle.red, ground.red),
        channel(SampleParticle.green, ground.green),
        channel(SampleParticle.blue, ground.blue),
        1f,
    )
}

private val SampleParticle = UiColor(1f, 0.55f, 0.15f, 0.75f)
private val DarkGround = UiColor(0.12f, 0.13f, 0.15f, 1f)
private val LightGround = UiColor(0.78f, 0.8f, 0.84f, 1f)
private val SelectedBorder = UiColor(0.84f, 0.5f, 0.11f, 1f)
private val IdleBorder = UiColor(0.19f, 0.2f, 0.24f, 1f)
private val SelectedText = UiColor(0.93f, 0.94f, 0.96f, 1f)
private val MutedText = UiColor(0.55f, 0.58f, 0.65f, 1f)
