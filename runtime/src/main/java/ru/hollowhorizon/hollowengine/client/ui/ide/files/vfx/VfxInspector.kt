package ru.hollowhorizon.hollowengine.client.ui.ide.files.vfx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeVfxDocument
import ru.hollowhorizon.hollowengine.client.ui.inspector.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.widgets.tooltipOnHover
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.vfx.*

/** What the shared inspector shows for the selected node of an effect. */
internal fun vfxNodeInspector(
    document: HollowIdeVfxDocument,
    state: VfxEditorState,
    nodeId: String,
): InspectorTarget {
    val node = document.effect.node(nodeId)
    val type = node?.let(VfxNodeTypes::of)
    return InspectorTarget(
        id = "vfx-node-$nodeId",
        title = node?.name ?: nodeId,
        subtitle = type?.titleKey?.lang,
        icon = type?.icon,
    ) {
        val live = document.effect.node(nodeId) ?: return@InspectorTarget
        CompositionLocalProvider(
            LocalVfxFieldFocus provides { property -> state.focusProperty(nodeId, property) },
            LocalVfxDriven provides vfxDrivenLookup(document, state.preview, nodeId),
        ) {
            NodeFields(document, state, live)
        }
    }
}

private fun HollowIdeVfxDocument.replace(node: VfxNodeSpec) = edit(mergeKey = "node:${node.id}") { it.withNode(node) }

@Composable
private fun NodeFields(document: HollowIdeVfxDocument, state: VfxEditorState, node: VfxNodeSpec) {
    Folding(state, "node", vfxText("section_node")) {
        NameRow(vfxText("name"), node.name) { document.replace(node.withCommon(name = it)) }
        ToggleRow(vfxText("enabled"), node.enabled) { document.replace(node.withCommon(enabled = it)) }
    }

    Folding(state, "transform", vfxText("section_transform"), openByDefault = false) {
        val transform = node.transform
        VfxFloat3Row(vfxText("position"), transform.position, VfxAnimatables.POSITION) {
            document.replace(node.withCommon(transform = transform.copy(position = it)))
        }
        VfxFloat3Row(vfxText("rotation"), transform.rotation, VfxAnimatables.ROTATION) {
            document.replace(node.withCommon(transform = transform.copy(rotation = it)))
        }
        VfxFloat3Row(vfxText("scale"), transform.scale, VfxAnimatables.SCALE) {
            document.replace(node.withCommon(transform = transform.copy(scale = it)))
        }
    }

    if (node is VfxEmitterSpec) EmitterFields(document, state, node)
}

/** A section that remembers whether it is open per effect editor. */
@Composable
internal fun Folding(
    state: VfxEditorState,
    key: String,
    title: String,
    openByDefault: Boolean = true,
    content: @Composable () -> Unit,
) {
    val open = state.isSectionOpen(key, openByDefault)
    CollapsibleSection(title, open, id = "vfx-section-$key", onToggle = { state.toggleSection(key, openByDefault) }) {
        content()
    }
}

@Composable
private fun EmitterFields(document: HollowIdeVfxDocument, state: VfxEditorState, emitter: VfxEmitterSpec) {
    val emission = emitter.emission
    Folding(state, "emission", vfxText("section_emission")) {
        VfxValueRow(vfxText("rate"), emission.rate, VfxAnimatables.RATE, vfxText("rate_hint")) {
            document.replace(emitter.withEmitter(emission = emission.copy(rate = it)))
        }
        VfxIntRow(vfxText("burst"), emission.burst, vfxText("burst_hint"), min = 0, max = 4096) {
            document.replace(emitter.withEmitter(emission = emission.copy(burst = it)))
        }
        VfxNumberRow(vfxText("duration"), emission.duration, vfxText("duration_hint"), min = 0f) {
            document.replace(emitter.withEmitter(emission = emission.copy(duration = it)))
        }
        ToggleRow(vfxText("loop"), emission.loop) {
            document.replace(emitter.withEmitter(emission = emission.copy(loop = it)))
        }
        if (emission.loop) {
            VfxNumberRow(vfxText("loop_delay"), emission.loopDelay, vfxText("loop_delay_hint"), min = 0f) {
                document.replace(emitter.withEmitter(emission = emission.copy(loopDelay = it)))
            }
        }
        VfxIntRow(
            vfxText("max_particles"),
            emission.maxParticles,
            vfxText("max_particles_hint"),
            min = 1,
            max = 20_000
        ) {
            document.replace(emitter.withEmitter(emission = emission.copy(maxParticles = it)))
        }
    }

    ShapeFields(document, state, emitter)

    val spawn = emitter.spawn
    Folding(state, "spawn", vfxText("section_spawn")) {
        VfxValueRow(vfxText("lifetime"), spawn.lifetime, VfxAnimatables.LIFETIME, vfxText("lifetime_hint")) {
            document.replace(emitter.withEmitter(spawn = spawn.copy(lifetime = it)))
        }
        VfxValueRow(vfxText("speed"), spawn.speed, VfxAnimatables.SPEED, vfxText("speed_hint")) {
            document.replace(emitter.withEmitter(spawn = spawn.copy(speed = it)))
        }
        VfxVec3Row(vfxText("spawn_offset"), spawn.offset, VfxAnimatables.OFFSET, vfxText("spawn_offset_hint")) {
            document.replace(emitter.withEmitter(spawn = spawn.copy(offset = it)))
        }
        VfxValueRow(
            vfxText("inherit_velocity"),
            spawn.inheritVelocity,
            VfxAnimatables.INHERIT_VELOCITY,
            vfxText("inherit_velocity_hint"),
        ) {
            document.replace(emitter.withEmitter(spawn = spawn.copy(inheritVelocity = it)))
        }
    }

    val appearance = emitter.appearance
    Folding(state, "appearance", vfxText("section_appearance")) {
        ToggleRow(vfxText("uniform_size"), appearance.uniformSize) {
            document.replace(emitter.withEmitter(appearance = appearance.copy(uniformSize = it)))
        }
        VfxVec3Row(
            vfxText("size"),
            appearance.size,
            VfxAnimatables.SIZE,
            vfxText("size_hint"),
            uniform = appearance.uniformSize,
        ) {
            document.replace(emitter.withEmitter(appearance = appearance.copy(size = it)))
        }
        VfxVec3Row(vfxText("spin"), appearance.rotation, VfxAnimatables.SPIN, vfxText("spin_hint")) {
            document.replace(emitter.withEmitter(appearance = appearance.copy(rotation = it)))
        }
        VfxColorRow(vfxText("color"), appearance.color, VfxAnimatables.COLOR, vfxText("color_hint")) {
            document.replace(emitter.withEmitter(appearance = appearance.copy(color = it)))
        }
    }

    val motion = emitter.motion
    Folding(state, "motion", vfxText("section_motion")) {
        VfxValueRow(vfxText("gravity"), motion.gravity, VfxAnimatables.GRAVITY, vfxText("gravity_hint")) {
            document.replace(emitter.withEmitter(motion = motion.copy(gravity = it)))
        }
        VfxValueRow(vfxText("drag"), motion.drag, VfxAnimatables.DRAG, vfxText("drag_hint")) {
            document.replace(emitter.withEmitter(motion = motion.copy(drag = it)))
        }
    }

    when (emitter) {
        is VfxQuadEmitterSpec -> QuadFields(document, state, emitter)
        is VfxMeshEmitterSpec -> MeshFields(document, state, emitter)
        else -> Unit
    }

    VfxModuleSections(document, state, emitter)

    Folding(state, "space", vfxText("section_space"), openByDefault = false) {
        Pills(VfxSimulationSpace.entries, emitter.space, { vfxText("space_${it.name.lowercase()}") }) {
            document.replace(emitter.withEmitter(space = it))
        }
        if (emitter.space == VfxSimulationSpace.LOCAL) {
            ToggleRow(vfxText("inherit_rotation"), emitter.inheritRotation) {
                document.replace(emitter.withEmitter(inheritRotation = it))
            }
            ToggleRow(vfxText("inherit_scale"), emitter.inheritScale) {
                document.replace(emitter.withEmitter(inheritScale = it))
            }
        }
        VfxNumberRow(vfxText("max_step"), emitter.maxStep, vfxText("max_step_hint"), min = 1f / 240f, max = 0.25f) {
            document.replace(emitter.withEmitter(maxStep = it))
        }
    }
}

/** The shape shows only what chosen kind reads. */
@Composable
private fun ShapeFields(document: HollowIdeVfxDocument, state: VfxEditorState, emitter: VfxEmitterSpec) {
    val shape = emitter.shape
    fun update(next: VfxShape) = document.replace(emitter.withEmitter(shape = next))

    Folding(state, "shape", vfxText("section_shape")) {
        Pills(VfxShapeKind.entries, shape.kind, { vfxText("shape_${it.name.lowercase()}") }) {
            update(shape.copy(kind = it))
        }

        val round =
            shape.kind == VfxShapeKind.SPHERE || shape.kind == VfxShapeKind.CONE || shape.kind == VfxShapeKind.DISC
        if (round) {
            VfxValueRow(
                vfxText("radius"),
                shape.radius,
                VfxAnimatables.SHAPE_RADIUS
            ) { update(shape.copy(radius = it)) }
            VfxValueRow(
                vfxText("thickness"),
                shape.thickness,
                VfxAnimatables.SHAPE_THICKNESS,
                vfxText("thickness_hint")
            ) {
                update(shape.copy(thickness = it))
            }
        }
        if (shape.kind == VfxShapeKind.CONE) {
            VfxValueRow(vfxText("angle"), shape.angle, VfxAnimatables.SHAPE_ANGLE, vfxText("angle_hint")) {
                update(shape.copy(angle = it))
            }
        }
        if (shape.kind == VfxShapeKind.BOX) {
            VfxVec3Row(vfxText("extents"), shape.extents, VfxAnimatables.SHAPE_EXTENTS, vfxText("extents_hint")) {
                update(shape.copy(extents = it))
            }
        }
        if (shape.kind == VfxShapeKind.LINE) {
            VfxVec3Row(vfxText("line_end"), shape.extents, VfxAnimatables.SHAPE_EXTENTS, vfxText("line_end_hint")) {
                update(shape.copy(extents = it))
            }
        }

        Text(vfxText("direction_mode"), tags = listOf("insp-inline-label"))
        Pills(VfxDirectionMode.entries, shape.directionMode, { vfxText("aim_${it.name.lowercase()}") }) {
            update(shape.copy(directionMode = it))
        }
        val readsDirection =
            shape.directionMode == VfxDirectionMode.FIXED || shape.directionMode == VfxDirectionMode.SHAPE && (shape.kind == VfxShapeKind.POINT || shape.kind == VfxShapeKind.BOX || shape.kind == VfxShapeKind.LINE)
        if (readsDirection) {
            VfxFloat3Row(vfxText("direction"), shape.direction) { update(shape.copy(direction = it)) }
        }
    }
}

@Composable
private fun QuadFields(document: HollowIdeVfxDocument, state: VfxEditorState, emitter: VfxQuadEmitterSpec) {
    val material = emitter.material
    fun update(next: VfxMaterialSpec) = document.replace(emitter.copy(material = next))

    Folding(state, "material", vfxText("section_material")) {
        TextRow(vfxText("texture"), material.texture) { update(material.copy(texture = it)) }
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

    Folding(state, "facing", vfxText("section_facing")) {
        Pills(VfxFacing.entries, emitter.facing, { vfxText("facing_${it.name.lowercase()}") }) {
            document.replace(emitter.copy(facing = it))
        }
        if (emitter.facing == VfxFacing.CAMERA_AXIS) {
            VfxFloat3Row(vfxText("facing_axis"), emitter.facingAxis) { document.replace(emitter.copy(facingAxis = it)) }
        }
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
        Text(axis, modifier = Modifier.fontSize(8f).foreground(UiColor(0.55f, 0.58f, 0.65f, 1f)))
        VfxNumberCellInline(value) { onChange(it.coerceIn(0f, 1f)) }
    }
}

@Composable
private fun BlendPicker(current: VfxBlend, onChange: (VfxBlend) -> Unit) {
    Row(modifier = Modifier.size(100.percent).gap(4.px)) {
        VfxBlend.entries.forEach { blend ->
            val selected = blend == current
            Column(
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

private fun UiCanvasDrawScope.drawBlendPreview(
    blend: VfxBlend,
    width: Float,
    height: Float,
    selected: Boolean,
) {
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

@Composable
private fun MeshFields(document: HollowIdeVfxDocument, state: VfxEditorState, emitter: VfxMeshEmitterSpec) {
    Folding(state, "model", vfxText("section_model")) {
        TextRow(vfxText("model"), emitter.model) { document.replace(emitter.copy(model = it)) }
        ToggleRow(vfxText("align_to_velocity"), emitter.alignToVelocity) {
            document.replace(emitter.copy(alignToVelocity = it))
        }
        ToggleRow(vfxText("emissive"), emitter.emissive) { document.replace(emitter.copy(emissive = it)) }
    }
}

/** What the inspector shows when nothing in the tree is selected. */
internal fun vfxEffectInspector(document: HollowIdeVfxDocument, state: VfxEditorState): InspectorTarget =
    InspectorTarget(id = "vfx-effect", title = vfxText("effect")) {
        val effect = document.effect
        Folding(state, "timeline", vfxText("section_timeline")) {
            VfxNumberRow(
                vfxText("timeline_duration"),
                effect.timeline.duration,
                vfxText("timeline_duration_hint"),
                min = 0.1f
            ) { duration ->
                document.edit { it.copy(timeline = it.timeline.copy(duration = duration)) }
            }
            ToggleRow(vfxText("timeline_loop"), effect.timeline.loop) { loop ->
                document.edit { it.copy(timeline = it.timeline.copy(loop = loop)) }
            }
        }
        Folding(state, "stats", vfxText("section_stats")) {
            Readonly(vfxText("nodes"), effect.walk().size.toString())
            Readonly(vfxText("expressions"), effect.expressions().size.toString())
        }
    }

private val SampleParticle = UiColor(1f, 0.55f, 0.15f, 0.75f)
private val DarkGround = UiColor(0.12f, 0.13f, 0.15f, 1f)
private val LightGround = UiColor(0.78f, 0.8f, 0.84f, 1f)
private val SelectedBorder = UiColor(0.84f, 0.5f, 0.11f, 1f)
private val IdleBorder = UiColor(0.19f, 0.2f, 0.24f, 1f)
private val SelectedText = UiColor(0.93f, 0.94f, 0.96f, 1f)
private val MutedText = UiColor(0.55f, 0.58f, 0.65f, 1f)
