package ru.hollowhorizon.hollowengine.client.editor

import de.fabmax.kool.KeyValueStore
import de.fabmax.kool.PassData
import de.fabmax.kool.ViewData
import de.fabmax.kool.input.*
import de.fabmax.kool.math.*
import de.fabmax.kool.math.spatial.BoundingBoxF
import de.fabmax.kool.modules.gizmo.*
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.ClearColorDontCare
import de.fabmax.kool.pipeline.ClearDepthDontCare
import de.fabmax.kool.pipeline.DepthMode
import de.fabmax.kool.scene.LineMesh
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.TrsTransformD
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import de.fabmax.kool.util.Viewport
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.isMouseOverDock
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.client.kool.KoolInitEvent
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.client.kool.gl.GlContext
import ru.hollowhorizon.hollowengine.client.kool.minecraft.MinecraftCamera
import ru.hollowhorizon.hollowengine.client.kool.minecraft.mcCamera
import ru.hollowhorizon.hollowengine.client.kool.minecraft.syncFromMinecraft
import ru.hollowhorizon.hollowengine.client.render.*
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.GuiOverlay
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderOverlayEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage
import ru.hollowhorizon.hollowengine.common.geary.anchor.*
import ru.hollowhorizon.hollowengine.common.geary.api.geary
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions

object TransformGizmoEditor {
    private const val ENABLED_KEY = "hollowengine.transform_gizmo.enabled"
    private const val MODE_KEY = "hollowengine.transform_gizmo.mode"

    private val root = Node("transform-gizmo-root")
    private val inputHandler = InputStack.InputHandler("transform-gizmo-editor")
    private val latePassData = PassData()
    private val pickViewData = ViewData()

    private val entries = linkedMapOf<java.util.UUID, GizmoEntry>()

    private var hoveredKey: java.util.UUID? = null
    private var draggingKey: java.util.UUID? = null
    private var activeKey: java.util.UUID? = null
    private var lastFrustum: Frustum? = null
    private var isInitialized = false
    private var contextMenu: ContextMenuState? = null
    private val overlayLabelState = mutableStateOf<OverlayLabelState?>(null)
    private val overlayContextMenuState = mutableStateOf<ContextMenuState?>(null)

    val enabledState = mutableStateOf(false)
    val modeState = mutableStateOf(GizmoEditMode.TRANSLATE)

    val isEnabled: Boolean get() = enabledState.value
    val mode: GizmoEditMode get() = modeState.value

    val scene by lazy {
        Scene("Transform Gizmo Editor").apply {
            clearColor = ClearColorDontCare
            clearDepth = ClearDepthDontCare
            depthMode = DepthMode.Legacy
            mcCamera()
            addNode(root)
        }
    }

    private val overlayScene: Scene by lazy {
        Scene("Transform Gizmo Editor UI").apply {
            setupUiScene(ClearColorDontCare)
            clearDepth = ClearDepthDontCare
            depthMode = DepthMode.Legacy
        }
    }

    private val overlaySurface: UiSurface by lazy {
        PanelSurface(
            parentScene = overlayScene,
            colors = IdeTheme.colors,
            sizes = IdeTheme.sizes,
            name = "TransformGizmoOverlay",
            backgroundColor = { null },
            width = Grow.Std,
            height = Grow.Std,
        ) {
            modifier
                .background(null)
                .isBlocking(false)

            overlayLabelState.use()?.let { label ->
                Text(formatLabelValue(label.value)) {
                    modifier
                        .margin(start = Dp.fromPx(label.position.x - 36f), top = Dp.fromPx(label.position.y - 16f))
                        .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal)
                        .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary.withAlpha(0.94f), sizes.smallGap))
                        .border(RoundRectBorder(ColorTheme.UI.BackgroundElements, sizes.smallGap, Dimensions.PaddingSmall))
                        .textColor(ColorTheme.UI.WhiteReplacement)
                }
            }

            overlayContextMenuState.use()?.let { menu ->
                Column(width = FitContent, height = FitContent) {
                    modifier
                        .margin(start = Dp.fromPx(menu.position.x), top = Dp.fromPx(menu.position.y))
                        .padding(Dimensions.PaddingMedium)
                        .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary.withAlpha(0.96f), sizes.smallGap))
                        .border(RoundRectBorder(ColorTheme.UI.BackgroundElements, sizes.smallGap, Dimensions.PaddingSmall))

                    Text("Stable Key") {
                        modifier.textColor(ColorTheme.UI.WhiteReplacement)
                    }
                    Text(menu.stableKey.toString()) {
                        modifier
                            .margin(top = Dimensions.PaddingNormal)
                            .textColor(ColorTheme.UI.BackgroundAccent)
                    }
                    Text("Copy Stable Key") {
                        modifier
                            .margin(top = Dimensions.PaddingNormal)
                            .textColor(ColorTheme.Accents.Main)
                            .alignY(AlignmentY.Center)
                    }
                }
            }
        }.apply {
            inputMode = UiSurface.InputCaptureMode.CaptureDisabled
        }
    }

    init {
        inputHandler.pointerListeners += PointerRouter

        enabledState.onChange { _, enabled ->
            KeyValueStore.setBoolean(ENABLED_KEY, enabled)
            if (!enabled) {
                InputStack.remove(inputHandler)
                cancelInteraction()
                entries.values.forEach { it.node.isVisible = false }
            } else {
                InputStack.pushTop(inputHandler)
                entries.values.forEach {
                    it.refreshFromRuntime()
                    it.node.isVisible = true
                }
            }
        }
        modeState.onChange { _, mode ->
            KeyValueStore.setInt(MODE_KEY, mode.ordinal)
            entries.values.forEach { it.configureMode(mode) }
        }
    }

    fun toggleEnabled() = enabledState.set(!isEnabled)

    fun setEnabled(enabled: Boolean) = enabledState.set(enabled)

    fun setMode(mode: GizmoEditMode) = modeState.set(mode)

    fun shouldBlockScreenInput(x: Float, y: Float): Boolean {
        if (!isInitialized) return false
        if (!isEditorAvailable()) return false
        if (isMouseOverDock(x, y)) return false
        return draggingKey != null || hoveredKey != null
    }

    @SubscribeEvent
    fun onKoolInit(event: KoolInitEvent) {
        if (!isInitialized) {
            enabledState.set(KeyValueStore.getBoolean(ENABLED_KEY))
            modeState.set(
                GizmoEditMode.entries.getOrElse(KeyValueStore.getInt(MODE_KEY, 0)) { GizmoEditMode.TRANSLATE }
            )
            isInitialized = true
        }
        if (enabledState.value) {
            InputStack.pushTop(inputHandler)
        }
    }

    @SubscribeEvent
    fun onCaptureFrustum(event: RenderLevelStageEvent) {
        if (event.stage != RenderStage.AFTER_ENTITIES) return
        lastFrustum = event.frustum
    }

    @SubscribeEvent
    fun onRenderOverlay(event: RenderOverlayEvent.Pre) {
        if (event.overlay != GuiOverlay.HOTBAR) return
        if (!isInitialized) return
        if (!isEditorAvailable()) return

        renderLateScene()
    }

    private fun isEditorAvailable(): Boolean {
        val minecraft = Minecraft.getInstance()
        return isEnabled &&
            minecraft.level != null &&
            minecraft.player?.hasPermissions(PlayerPermissions.GAMEMASTER) == true
    }

    private fun cancelInteraction() {
        draggingKey?.let(entries::get)?.let { entry ->
            if (entry.gizmo.isManipulating) {
                entry.gizmo.cancelManipulation()
                entry.refreshFromRuntime()
            }
        }
        hoveredKey = null
        draggingKey = null
        activeKey = null
        contextMenu = null
        overlayLabelState.set(null)
        overlayContextMenuState.set(null)
        syncEntryPresentation()
    }

    private fun syncVisibleEntries() {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        if (level == null || !isEditorAvailable()) {
            entries.values.forEach { it.node.isVisible = false }
            return
        }

        val seen = linkedSetOf<java.util.UUID>()
        val service = MaterializationRuntimeState.service(level)
        val frustum = lastFrustum
        val partialTick = TickHandler.partialTick

        with(level.geary) {
            service.records.forEach { record ->
                val gearyEntity = record.runtimeId.toGeary()
                val model = gearyEntity.get<Model>() ?: return@forEach
                val transform = gearyEntity.get<TransformComponent>() ?: return@forEach
                val resolved = resolveAnchoredTransform(level, record.anchor, transform, partialTick) ?: return@forEach
                val bounds = buildAnchoredRenderBounds(model, resolved.transform, model.scale)
                val visible = frustum?.isVisible(bounds) ?: true

                val entry = entries.getOrPut(record.stableKey) {
                    GizmoEntry(record.stableKey).also { root.addNode(it.node) }
                }
                entry.anchor = record.anchor
                entry.node.isVisible = visible
                entry.updateFromResolved(model, resolved, bounds)
                seen += record.stableKey
            }
        }

        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val (stableKey, entry) = iterator.next()
            if (stableKey in seen) continue
            if (hoveredKey == stableKey) hoveredKey = null
            if (draggingKey == stableKey) draggingKey = null
            if (activeKey == stableKey) activeKey = null
            if (contextMenu?.stableKey == stableKey) contextMenu = null
            root.removeNode(entry.node)
            entry.node.release()
            iterator.remove()
        }

        syncEntryPresentation()
    }

    private fun syncEntryPresentation() {
        entries.forEach { (stableKey, entry) ->
            entry.updatePresentation(
                isHovered = hoveredKey == stableKey,
                isActive = activeKey == stableKey,
            )
        }
    }

    private fun prepareRenderSceneState(): Boolean {
        if (!isInitialized || !isEditorAvailable()) return false
        syncInputHandlerState()
        syncVisibleEntries()
        val backend = KoolManager.context.backend
        backend.collectScene(scene, latePassData)
        return true
    }

    private fun preparePickState(): Boolean {
        if (!isInitialized || !isEditorAvailable()) return false
        syncInputHandlerState()
        syncVisibleEntries()

        val ctx = KoolManager.context
        val pass = scene.mainRenderPass
        if (pass.isFillFrame) {
            val size = ctx.window.size
            if (!pass.viewport.equals(0, 0, size.x, size.y)) {
                pass.viewport = Viewport(0, 0, size.x, size.y)
            }
        }

        (scene.camera as? MinecraftCamera)?.syncFromMinecraft()
        pickViewData.reset(pass.defaultView)
        scene.camera.updateCamera(pickViewData)
        return true
    }

    private fun syncInputHandlerState() {
        if (!isEnabled || !isEditorAvailable()) {
            inputHandler.blockAllPointerInput = false
            InputStack.remove(inputHandler)
            InputStack.updateHandlerStack()
            return
        }

        val pointer = PointerInput.primaryPointer
        val isPointerOverUi = pointer.isValid && isMouseOverDock(pointer.pos.x, pointer.pos.y)
        inputHandler.blockAllPointerInput = !isPointerOverUi && (draggingKey != null || hoveredKey != null)
        InputStack.pushTop(inputHandler)
        InputStack.updateHandlerStack()
    }

    private fun renderLateScene() {
        if (!prepareRenderSceneState()) return
        overlayLabelState.set(currentLabelState())
        overlayContextMenuState.set(contextMenu)
        GlContext.withState {
            KoolManager.context.backend.renderCollectedScene(latePassData)
            if (overlaySurface.parent == null) {
                overlayScene.addNode(overlaySurface)
            }
            overlaySurface.triggerUpdate()
            KoolManager.context.backend.renderSceneLate(overlayScene)
        }
    }

    private fun currentLabelState(): OverlayLabelState? {
        val preferred = draggingKey ?: activeKey ?: hoveredKey
        preferred?.let { key ->
            entries[key]?.currentLabelState()?.let { return it }
        }
        return entries.values.firstNotNullOfOrNull(GizmoEntry::currentLabelState)
    }

    private fun handleContextMenuClick(pointer: Pointer): Boolean {
        val menu = contextMenu ?: return false
        val within = pointer.pos.x >= menu.position.x &&
            pointer.pos.x <= menu.position.x + 260f &&
            pointer.pos.y >= menu.position.y &&
            pointer.pos.y <= menu.position.y + 72f
        if (!within) {
            if (pointer.isAnyButtonClicked) {
                contextMenu = null
            }
            return false
        }
        if (pointer.isRightButtonClicked || pointer.isLeftButtonClicked) {
            val minecraft = Minecraft.getInstance()
            minecraft.keyboardHandler.clipboard = menu.stableKey.toString()
            minecraft.player?.displayClientMessage(Component.literal("Stable key copied"), true)
            pointer.consume()
            contextMenu = null
            return true
        }
        return false
    }

    private fun pickEntry(pointer: Pointer): PickResult? {
        if (!preparePickState()) return null

        val handleRay = RayTest()
        if (!scene.computePickRay(pointer, handleRay.ray)) return null
        var handleEntry: GizmoEntry? = null
        var handleDistance = Double.POSITIVE_INFINITY
        entries.values.asSequence()
            .filter { it.node.isVisible }
            .forEach { entry ->
                val test = RayTest()
                test.clear(camera = scene.camera)
                test.ray.set(handleRay.ray)
                entry.gizmo.rayTest(test)
                if (test.isHit && test.hitDistance < handleDistance) {
                    handleDistance = test.hitDistance
                    handleEntry = entry
                }
            }
        if (handleEntry != null) {
            return PickResult(handleEntry, PickTarget.HANDLE)
        }

        val boundsRay = RayTest()
        if (!scene.computePickRay(pointer, boundsRay.ray)) return null
        var boundsEntry: GizmoEntry? = null
        var boundsDistance = Double.POSITIVE_INFINITY
        entries.values.asSequence()
            .filter { it.node.isVisible }
            .forEach { entry ->
                val hitDistance = entry.boundsHitDistance(boundsRay.ray)
                if (hitDistance != null && hitDistance < boundsDistance) {
                    boundsDistance = hitDistance
                    boundsEntry = entry
                }
            }
        return boundsEntry?.let { PickResult(it, PickTarget.BOUNDS) }
    }

    private fun resolveEntryForNode(node: Node): GizmoEntry? {
        var current: Node? = node
        while (current != null) {
            val resolved = entries.values.firstOrNull { it.node === current }
            if (resolved != null) return resolved
            current = current.parent
        }
        return null
    }

    private object PointerRouter : InputStack.PointerListener {
        override fun handlePointer(pointerState: PointerState, ctx: de.fabmax.kool.KoolContext) {
            if (!isInitialized) return
            if (!isEditorAvailable()) {
                hoveredKey = null
                draggingKey = null
                inputHandler.blockAllPointerInput = false
                syncEntryPresentation()
                return
            }

            val pointer = pointerState.primaryPointer
            if (handleContextMenuClick(pointer)) {
                syncEntryPresentation()
                syncInputHandlerState()
                return
            }
            val draggingEntry = draggingKey?.let(entries::get)
            if (draggingEntry != null) {
                draggingEntry.gizmo.applySpeedAndTickRate()
                draggingEntry.gizmo.handlePointer(pointerState, ctx)
                hoveredKey = draggingEntry.stableKey
                activeKey = draggingEntry.stableKey
                if (!pointer.isLeftButtonDown && !draggingEntry.gizmo.isManipulating) {
                    draggingKey = null
                }
                syncEntryPresentation()
                syncInputHandlerState()
                return
            }

            if (isMouseOverDock(pointer.pos.x, pointer.pos.y)) {
                hoveredKey = null
                inputHandler.blockAllPointerInput = false
                syncEntryPresentation()
                return
            }

            val hoveredPick = pickEntry(pointer)
            hoveredKey = hoveredPick?.entry?.stableKey

            when (hoveredPick?.target) {
                PickTarget.HANDLE -> {
                    hoveredPick.entry.gizmo.applySpeedAndTickRate()
                    hoveredPick.entry.gizmo.handlePointer(pointerState, ctx)
                    if (hoveredPick.entry.gizmo.isManipulating || pointer.isConsumed()) {
                        activeKey = hoveredPick.entry.stableKey
                        draggingKey = hoveredPick.entry.stableKey
                        contextMenu = null
                    } else if (pointer.isRightButtonClicked) {
                        activeKey = hoveredPick.entry.stableKey
                        contextMenu = ContextMenuState(hoveredPick.entry.stableKey, Vec2f(pointer.pos.x, pointer.pos.y))
                        pointer.consume()
                    }
                }

                PickTarget.BOUNDS -> {
                    if (pointer.isLeftButtonClicked) {
                        activeKey = hoveredPick.entry.stableKey
                        contextMenu = null
                        pointer.consume()
                    } else if (pointer.isRightButtonClicked) {
                        activeKey = hoveredPick.entry.stableKey
                        contextMenu = ContextMenuState(hoveredPick.entry.stableKey, Vec2f(pointer.pos.x, pointer.pos.y))
                        pointer.consume()
                    }
                }

                null -> if (pointer.isRightButtonClicked || pointer.isLeftButtonClicked) {
                    contextMenu = null
                }
            }

            syncEntryPresentation()
            syncInputHandlerState()
        }
    }

    private class GizmoEntry(
        val stableKey: java.util.UUID,
    ) {
        val gizmo = GizmoNode("transform-gizmo-$stableKey").apply {
            gizmoSize = 2.5f
        }
        val translationOverlay = TranslationOverlay(gizmo)
        val rotationOverlay = RotationOverlay(gizmo)
        val scaleOverlay = ScaleOverlay(gizmo)
        private val boundsMesh = createBoundsMesh()
        private val translationHandles = buildTranslationHandles()
        private val rotationHandles = buildRotationHandles()
        private val scaleHandles = buildScaleHandles()
        val node = Node("transform-gizmo-root-$stableKey").apply {
            addNode(boundsMesh)
            addNode(gizmo)
            addNode(translationOverlay)
            addNode(rotationOverlay)
            addNode(scaleOverlay)
        }

        var anchor: AnchorComponent = WorldAnchor()
        private var lastAppliedTransform: TransformComponent? = null
        private var lastBounds: AABB? = null
        private var lastBoundsColor = BOUNDS_COLOR
        private val gizmoClientOffset = MutableMat4d()
        private val gizmoWorldMatrix = MutableMat4d()
        private val decomposedTranslation = MutableVec3d()
        private val decomposedRotation = MutableQuatD()
        private val decomposedScale = MutableVec3d()

        init {
            translationOverlay.isPickable = false
            rotationOverlay.isPickable = false
            scaleOverlay.isPickable = false
            gizmo.gizmoListeners += translationOverlay
            gizmo.gizmoListeners += rotationOverlay
            gizmo.gizmoListeners += scaleOverlay
            configureMode(TransformGizmoEditor.mode)
            gizmo.gizmoListeners += object : GizmoListener {
                override fun onManipulationStart(startTransform: TrsTransformD) {
                    TransformGizmoEditor.activeKey = stableKey
                    TransformGizmoEditor.draggingKey = stableKey
                }

                override fun onManipulationFinished(startTransform: TrsTransformD, endTransform: TrsTransformD) {
                    TransformGizmoEditor.draggingKey = null
                }

                override fun onManipulationCanceled(startTransform: TrsTransformD) {
                    TransformGizmoEditor.draggingKey = null
                    refreshFromRuntime()
                }

                override fun onGizmoUpdate(transform: TrsTransformD) {
                    applyFromGizmo()
                }
            }
        }

        fun configureMode(mode: GizmoEditMode) {
            gizmo.handles.toList().forEach(gizmo::removeHandle)
            val handles = when (mode) {
                GizmoEditMode.TRANSLATE -> translationHandles
                GizmoEditMode.ROTATE -> rotationHandles
                GizmoEditMode.SCALE -> scaleHandles
            }
            handles.forEach(gizmo::addHandle)
        }

        fun refreshFromRuntime() {
            val level = Minecraft.getInstance().level ?: return
            val snapshot = MaterializationRuntimeState.service(level).snapshot(stableKey) ?: return
            anchor = snapshot.anchorOrNull() ?: anchor
            val transform = snapshot.transformOrNull() ?: TransformComponent()
            lastAppliedTransform = transform
            val model = snapshot.modelOrNull() ?: return
            val resolved = resolveAnchoredTransform(level, anchor, transform, TickHandler.partialTick) ?: return
            updateFromResolved(model, resolved, buildAnchoredRenderBounds(model, resolved.transform, model.scale))
        }

        fun updateFromResolved(
            model: Model,
            resolved: ru.hollowhorizon.hollowengine.client.render.ResolvedAnchorTransform,
            bounds: AABB,
        ) {
            if (!gizmo.isManipulating) {
                gizmoClientOffset.setIdentity()
                    .rotate(quatFToGizmoRotation(resolved.transform.rotation))
                    .scale(
                        MutableVec3d(
                            resolved.transform.scale.x.toDouble(),
                            resolved.transform.scale.y.toDouble(),
                            resolved.transform.scale.z.toDouble(),
                        )
                    )
                gizmo.gizmoTransform.setCompositionOf(
                    Vec3d(
                        resolved.transform.translation.x.toDouble(),
                        resolved.transform.translation.y.toDouble(),
                        resolved.transform.translation.z.toDouble(),
                    ),
                )
                gizmo.updateModelMatRecursive()
            }
            rebuildBounds(bounds, model)
        }

        fun updatePresentation(isHovered: Boolean, isActive: Boolean) {
            val color = when {
                isActive -> ACTIVE_BOUNDS_COLOR
                isHovered -> HOVER_BOUNDS_COLOR
                else -> BOUNDS_COLOR
            }
            if (lastBounds != null && color != lastBoundsColor) {
                rebuildBounds(lastBounds!!, null, color)
            }
        }

        private fun rebuildBounds(bounds: AABB, model: Model? = null, overrideColor: Color? = null) {
            val color = overrideColor ?: when {
                TransformGizmoEditor.activeKey == stableKey -> ACTIVE_BOUNDS_COLOR
                TransformGizmoEditor.hoveredKey == stableKey -> HOVER_BOUNDS_COLOR
                else -> BOUNDS_COLOR
            }
            if (bounds == lastBounds && color == lastBoundsColor && model == null) return

            boundsMesh.clear()
            boundsMesh.color = color
            boundsMesh.addBoundingBox(
                BoundingBoxF(
                    Vec3f(bounds.minX.toFloat(), bounds.minY.toFloat(), bounds.minZ.toFloat()),
                    Vec3f(bounds.maxX.toFloat(), bounds.maxY.toFloat(), bounds.maxZ.toFloat()),
                ),
                color = color,
            )
            lastBounds = bounds
            lastBoundsColor = color
        }

        private fun createBoundsMesh(): LineMesh =
            LineMesh("transform-bounds-$stableKey").apply {
                isCastingShadow = false
                shader = KslUnlitShader {
                    color { vertexColor() }
                    pipeline {
                        lineWidth = 3f
                    }
                }
            }

        fun currentLabelState(): OverlayLabelState? =
            when {
                translationOverlay.isVisible && translationOverlay.isLabelValid ->
                    OverlayLabelState(translationOverlay.labelPosition, translationOverlay.labelValue)

                rotationOverlay.isVisible && rotationOverlay.isLabelValid ->
                    OverlayLabelState(rotationOverlay.labelPosition, rotationOverlay.labelValue)

                scaleOverlay.isVisible && scaleOverlay.isLabelValid ->
                    OverlayLabelState(scaleOverlay.labelPosition, scaleOverlay.labelValue)

                gizmo.isManipulating -> fallbackLabelState()

                else -> null
            }

        fun boundsHitDistance(ray: de.fabmax.kool.math.RayD): Double? {
            val bounds = lastBounds ?: return null
            return intersectRayAabb(ray.origin.x, ray.origin.y, ray.origin.z, ray.direction.x, ray.direction.y, ray.direction.z, bounds)
        }

        private fun applyFromGizmo() {
            val level = Minecraft.getInstance().level ?: return
            gizmoWorldMatrix.setIdentity()
                .mul(gizmo.gizmoTransform.matrixD)
                .mul(gizmoClientOffset)
            gizmoWorldMatrix.decompose(decomposedTranslation, decomposedRotation, decomposedScale)
            val updatedTransform = worldTransformToComponent(
                level = level,
                anchor = anchor,
                worldPosition = Vec3(decomposedTranslation.x, decomposedTranslation.y, decomposedTranslation.z),
                worldRotation = gizmoRotationToQuatF(decomposedRotation),
                worldScale = Vec3f(
                    decomposedScale.x.toFloat(),
                    decomposedScale.y.toFloat(),
                    decomposedScale.z.toFloat(),
                ),
                partialTick = TickHandler.partialTick,
            ) ?: return

            if (updatedTransform == lastAppliedTransform) return
            lastAppliedTransform = updatedTransform

            val service = MaterializationRuntimeState.service(level)
            val snapshot = service.snapshot(stableKey) ?: return
            val updatedSnapshot = when (val currentAnchor = snapshot.anchorOrNull()) {
                is WorldAnchor -> snapshot
                    .withIdentity(
                        worldAnchorFor(
                            Vec3(
                                updatedTransform.translation.x.toDouble(),
                                updatedTransform.translation.y.toDouble(),
                                updatedTransform.translation.z.toDouble(),
                            ),
                            currentAnchor.localId,
                        ),
                        stableKey,
                    )
                    .withOrReplace(updatedTransform)

                is EntityAnchor -> snapshot.withOrReplace(updatedTransform)
                else -> return
            }

            service.materialize(updatedSnapshot)
            AnchoredTransformUpdatePacket(stableKey, updatedTransform).send()
        }

        private fun fallbackLabelState(): OverlayLabelState? {
            val value = when (val manipulator = gizmo.latestManipulatorValue.value) {
                is ManipulatorValue.ManipulatorValue1d -> manipulator.value
                is ManipulatorValue.ManipulatorValue3d -> manipulator.value.length()
                is ManipulatorValue.ManipulatorValue4d -> null
                null -> null
            } ?: return null

            val projected = MutableVec3d()
            if (!scene.camera.projectScreen(gizmo.gizmoTransform.translation, scene.mainRenderPass.viewport, projected)) {
                return null
            }
            return OverlayLabelState(Vec2f(projected.x.toFloat(), projected.y.toFloat()), value)
        }
    }

    private data class PickResult(
        val entry: GizmoEntry,
        val target: PickTarget,
    )

    private data class ContextMenuState(
        val stableKey: java.util.UUID,
        val position: Vec2f,
    )

    private data class OverlayLabelState(
        val position: Vec2f,
        val value: Double,
    )

    private enum class PickTarget {
        HANDLE,
        BOUNDS,
    }

    private val BOUNDS_COLOR = Color(1f, 1f, 1f, 0.35f)
    private val HOVER_BOUNDS_COLOR = Color(0.4f, 0.9f, 1f, 0.75f)
    private val ACTIVE_BOUNDS_COLOR = Color(1f, 0.8f, 0.25f, 0.95f)
}

enum class GizmoEditMode {
    TRANSLATE,
    ROTATE,
    SCALE,
}

private fun buildTranslationHandles(): List<GizmoHandle> = listOf(
    AxisHandle(
        color = MdColor.RED,
        axis = GizmoHandle.Axis.POS_X,
        handleShape = AxisHandle.HandleType.ARROW,
        name = "axis-POS_X",
    ),
    AxisHandle(
        color = MdColor.LIGHT_GREEN,
        axis = GizmoHandle.Axis.POS_Y,
        handleShape = AxisHandle.HandleType.ARROW,
        name = "axis-POS_Y",
    ),
    AxisHandle(
        color = MdColor.BLUE,
        axis = GizmoHandle.Axis.POS_Z,
        handleShape = AxisHandle.HandleType.ARROW,
        name = "axis-POS_Z",
    ),
    PlaneHandle(
        color = MdColor.RED,
        axis = GizmoHandle.Axis.POS_X,
        name = "plane-POS_X",
    ),
    PlaneHandle(
        color = MdColor.LIGHT_GREEN,
        axis = GizmoHandle.Axis.POS_Y,
        name = "plane-POS_Y",
    ),
    PlaneHandle(
        color = MdColor.BLUE,
        axis = GizmoHandle.Axis.POS_Z,
        name = "plane-POS_Z",
    ),
    CenterCircleHandle(
        color = Color.WHITE,
        radius = 0.28f,
    ),
)

private fun buildRotationHandles(): List<GizmoHandle> = listOf(
    AxisRotationHandle(color = MdColor.RED, axis = GizmoHandle.Axis.POS_X, radius = 0.95f),
    AxisRotationHandle(color = MdColor.LIGHT_GREEN, axis = GizmoHandle.Axis.POS_Y, radius = 0.95f),
    AxisRotationHandle(color = MdColor.BLUE, axis = GizmoHandle.Axis.POS_Z, radius = 0.95f),
)

private fun buildScaleHandles(): List<GizmoHandle> = listOf(
    CenterCircleHandle(
        color = Color.WHITE,
        radius = 1.2f,
        innerRadius = 0.28f,
        gizmoOperation = UniformScale(),
        name = "scale-uniform",
    ),
)

fun GizmoNode.applySpeedAndTickRate() {
    dragSpeedModifier.set(if (KeyboardInput.isShiftDown) 0.1f else 1.0f)
    if (KeyboardInput.isCtrlDown) {
        translationTick.set(if (KeyboardInput.isShiftDown) 0.1 else 1.0)
        rotationTick.set(if (KeyboardInput.isShiftDown) 1.0 else 5.0)
        scaleTick.set(if (KeyboardInput.isShiftDown) 0.01 else 0.1)
    } else {
        translationTick.set(0.0)
        rotationTick.set(0.0)
        scaleTick.set(0.0)
    }
}

private fun formatLabelValue(value: Double): String {
    val absValue = kotlin.math.abs(value)
    val precision = when {
        absValue >= 1000.0 -> 0
        absValue >= 100.0 -> 1
        absValue >= 1.0 -> 2
        absValue >= 0.01 -> 3
        else -> 4
    }
    return "%.${precision}f".format(java.util.Locale.ROOT, value)
}

private fun intersectRayAabb(
    originX: Double,
    originY: Double,
    originZ: Double,
    dirX: Double,
    dirY: Double,
    dirZ: Double,
    bounds: AABB,
): Double? {
    var tMin = Double.NEGATIVE_INFINITY
    var tMax = Double.POSITIVE_INFINITY

    fun update(origin: Double, dir: Double, min: Double, max: Double): Boolean {
        if (kotlin.math.abs(dir) < 1.0e-9) {
            return origin in min..max
        }
        val inv = 1.0 / dir
        var t0 = (min - origin) * inv
        var t1 = (max - origin) * inv
        if (t0 > t1) {
            val swap = t0
            t0 = t1
            t1 = swap
        }
        tMin = kotlin.math.max(tMin, t0)
        tMax = kotlin.math.min(tMax, t1)
        return tMax >= tMin
    }

    if (!update(originX, dirX, bounds.minX, bounds.maxX)) return null
    if (!update(originY, dirY, bounds.minY, bounds.maxY)) return null
    if (!update(originZ, dirZ, bounds.minZ, bounds.maxZ)) return null
    if (tMax < 0.0) return null
    return if (tMin >= 0.0) tMin else tMax
}
