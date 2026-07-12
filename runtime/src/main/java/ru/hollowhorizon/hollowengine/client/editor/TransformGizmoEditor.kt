package ru.hollowhorizon.hollowengine.client.editor

import androidx.compose.runtime.mutableStateOf as composeStateOf
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
import de.fabmax.kool.scene.*
import de.fabmax.kool.scene.geometry.IndexedVertexList
import de.fabmax.kool.scene.geometry.PrimitiveType
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import de.fabmax.kool.util.Viewport
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentOverlay
import ru.hollowhorizon.hollowengine.client.gui.scripting.isMouseOverDock
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.client.kool.KoolInitEvent
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.client.kool.gl.GlContext
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.kool.minecraft.MinecraftCamera
import ru.hollowhorizon.hollowengine.client.kool.minecraft.mcCamera
import ru.hollowhorizon.hollowengine.client.kool.minecraft.syncFromMinecraft
import ru.hollowhorizon.hollowengine.client.render.*
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.GuiOverlay
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderOverlayEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage
import ru.hollowhorizon.hollowengine.common.geary.binding.*
import ru.hollowhorizon.hollowengine.common.geary.components.*
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.snapshot.LevelSnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.Snapshot
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.COPY
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.GENERAL
import ru.hollowhorizon.hollowengine.generated.Assets.Hollowengine.Textures.Gui.Icons.REMOVE
import java.util.*
import kotlin.math.*

@ClientOnly
object TransformGizmoEditor {
    private const val ENABLED_KEY = "hollowengine.transform_gizmo.enabled"
    private const val MODE_KEY = "hollowengine.transform_gizmo.mode"

    private const val CONTEXT_MENU_WIDTH = 280f
    private const val CONTEXT_MENU_HEIGHT = 132f
    private const val INSPECTOR_WIDTH = 420f
    private const val INSPECTOR_MARGIN = 16f

    internal val MODEL_ICON = "hollowengine:textures/gui/icons/box.svg".rl
    internal val TRANSFORM_ICON = "hollowengine:textures/gui/icons/world.svg".rl
    internal val POINT_LIGHT_ICON = "hollowengine:textures/gui/icons/light_point.svg".rl
    internal val SPOT_LIGHT_ICON = "hollowengine:textures/gui/icons/light_spot.svg".rl

    private val root = Node("transform-gizmo-root")
    private val latePassData = PassData()
    private val pickViewData = ViewData()

    private val entries = linkedMapOf<GizmoEntryId, GizmoEntry>()

    private var hoveredKey: GizmoEntryId? = null
    private var draggingKey: GizmoEntryId? = null
    private var activeKey: GizmoEntryId? = null
    private var lastFrustum: Frustum? = null
    private var isInitialized = false
    private var contextMenu: ContextMenuState? = null

    private val overlayLabelState = mutableStateOf<OverlayLabelState?>(null)
    private val inspectorState = TransformGizmoInspectorState(::applySnapshotUpdate)

    val enabledState = composeStateOf(false)
    val modeState = composeStateOf(GizmoEditMode.TRANSLATE)

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

    private val overlaySurface: UiSurface by lazy {
        PanelSurface(
            parentScene = ScriptingEnvironmentOverlay.scene,
            colors = IdeTheme.colors,
            sizes = IdeTheme.sizes,
            name = "TransformGizmoOverlay",
            backgroundColor = { null },
            layout = CellLayout,
            width = Grow.Std,
            height = Grow.Std,
        ) {
            modifier.background(null)

            overlayLabelState.use()?.let { label ->
                Text(formatLabelValue(label.value)) {
                    modifier
                        .margin(start = Dp.fromPx(label.position.x - 36f), top = Dp.fromPx(label.position.y - 16f))
                        .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal)
                        .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary.withAlpha(0.94f), sizes.smallGap))
                        .border(RoundRectBorder(ColorTheme.UI.BackgroundElements, sizes.smallGap, Dimensions.PaddingSmall))
                        .textColor(ColorTheme.UI.WhiteReplacement)
                        .isBlocking(false)
                }
            }

            renderContextMenu()
            with(inspectorState) { RenderPanel() }
        }.apply {
            inputMode = UiSurface.InputCaptureMode.CapturePassthrough
            inputHandler.pointerListeners += PointerRouter
        }
    }

    private fun applyEnabled(enabled: Boolean) {
        KeyValueStore.setBoolean(ENABLED_KEY, enabled)
        if (!enabled) {
            cancelInteraction()
            entries.values.forEach { it.node.isVisible = false }
        } else {
            entries.values.forEach {
                it.refreshFromRuntime()
                it.node.isVisible = true
            }
        }
        updateInputBlockingState()
    }

    private fun applyMode(mode: GizmoEditMode) {
        KeyValueStore.setInt(MODE_KEY, mode.ordinal)
        entries.values.forEach { it.configureMode(mode) }
    }

    fun toggleEnabled() = setEnabled(!isEnabled)

    fun setEnabled(enabled: Boolean) {
        if (enabledState.value == enabled) return
        enabledState.value = enabled
        applyEnabled(enabled)
    }

    fun setMode(mode: GizmoEditMode) {
        if (modeState.value == mode) return
        modeState.value = mode
        applyMode(mode)
    }

    fun shouldBlockScreenInput(x: Float, y: Float): Boolean {
        if (!isInitialized) return false
        if (!isWorldInputEnabled()) return false
        if (!isEditorAvailable()) return false
        if (isMouseOverDock(x, y)) return false
        return draggingKey != null || hoveredKey != null || isOverlayUiHit(x, y)
    }

    @SubscribeEvent
    fun onKoolInit(event: KoolInitEvent) {
        if (!isInitialized) {
            val storedEnabled = KeyValueStore.getBoolean(ENABLED_KEY)
            val storedMode =
                GizmoEditMode.entries.getOrElse(KeyValueStore.getInt(MODE_KEY, 0)) { GizmoEditMode.TRANSLATE }
            enabledState.value = storedEnabled
            modeState.value = storedMode
            applyEnabled(storedEnabled)
            applyMode(storedMode)
            isInitialized = true
        }
        updateInputBlockingState()
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
        updateInputBlockingState()
        if (!isEditorAvailable()) return
        renderLateScene()
    }

    private fun UiScope.renderContextMenu() {
        val menu = contextMenu ?: return
        val entry = entries[menu.entryId] ?: return
        val isInspectorOpened = inspectorState.isVisibleFor(menu.entryId.snapshotId)

        Column(width = FitContent, height = FitContent) {
            modifier
                .margin(start = Dp.fromPx(menu.position.x), top = Dp.fromPx(menu.position.y))
                .padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary.withAlpha(0.98f), sizes.smallGap))
                .border(RoundRectBorder(ColorTheme.UI.BackgroundElements, sizes.smallGap, Dimensions.PaddingSmall))

            PopupActionRow(
                label = if (isInspectorOpened) "Hide Components" else "Show Components",
                icon = GENERAL,
            ) {
                if (isInspectorOpened) inspectorState.close()
                else inspectorState.open(entry.snapshot ?: return@PopupActionRow, entry.target ?: return@PopupActionRow)
                contextMenu = null
            }
            PopupActionRow(label = "Copy Snapshot ID", icon = COPY) {
                Minecraft.getInstance().keyboardHandler.clipboard = menu.entryId.snapshotId.toString()
                Minecraft.getInstance().player?.displayClientMessage(Component.literal("Snapshot id copied"), true)
                contextMenu = null
            }
            PopupActionRow(label = "Clear Selection", icon = REMOVE) {
                clearSelection()
                contextMenu = null
            }
        }
    }

    private fun UiScope.PopupActionRow(label: String, icon: ResourceLocation?, onClick: () -> Unit) {
        Row(width = Grow.Std) {
            var isHovered by remember(false)
            modifier
                .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal)
                .background(
                    RoundRectBackground(
                        (if (isHovered) ColorTheme.UI.BackgroundElements else ColorTheme.UI.BackgroundSecondary).withAlpha(0.98f),
                        Dimensions.PaddingMedium,
                    )
                )
                .onEnter { isHovered = true }
                .onExit { isHovered = false }
                .onClick { onClick() }

            if (icon != null) {
                Image(icon) {
                    modifier
                        .size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .margin(end = Dimensions.PaddingMedium)
                        .alignY(AlignmentY.Center)
                }
            }
            Text(label) {
                modifier
                    .alignY(AlignmentY.Center)
                    .textColor(ColorTheme.UI.WhiteReplacement)
            }
        }
    }

    private fun isEditorAvailable(): Boolean {
        val minecraft = Minecraft.getInstance()
        return isEnabled &&
            minecraft.level != null &&
            minecraft.player?.hasPermissions(PlayerPermissions.GAMEMASTER) == true
    }

    private fun isWorldInputEnabled(): Boolean {
        if (!isEditorAvailable()) return false
        val minecraft = Minecraft.getInstance()
        if (minecraft.screen != null && minecraft.screen !is ChatScreen) return false
        return minecraft.screen is ChatScreen || !ScriptingEnvironmentOverlay.isCollapsed
    }

    private fun updateInputBlockingState() {
        val pointer = PointerInput.primaryPointer
        val isPointerOverDock = pointer.isValid && isMouseOverDock(pointer.pos.x, pointer.pos.y)
        val isPointerOverOverlay = pointer.isValid && isOverlayUiHit(pointer.pos.x, pointer.pos.y)
        overlaySurface.inputHandler.blockAllPointerInput =
            isWorldInputEnabled() &&
                !isPointerOverDock &&
                !isPointerOverOverlay &&
                (draggingKey != null || hoveredKey != null)
    }

    private fun clearSelection() {
        activeKey = null
        contextMenu = null
        inspectorState.close()
        syncEntryPresentation()
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
        inspectorState.close()
        syncEntryPresentation()
        updateInputBlockingState()
    }

    private fun syncVisibleEntries() {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        if (level == null || !isEditorAvailable()) {
            entries.values.forEach { it.node.isVisible = false }
            inspectorState.close()
            return
        }

        val seen = linkedSetOf<GizmoEntryId>()
        val service = NodeRuntimeState.service(level)
        val frustum = lastFrustum
        val partialTick = TickHandler.partialTick

        service.records.forEach { record ->
            val snapshot = service.snapshot(record.snapshotId) ?: return@forEach
            val hostEntityUuid = snapshot.hostEntityUuidOrNull() ?: record.hostEntityUuid

            val claimedNodes = hashSetOf<java.util.UUID>()
            snapshot.modelNodes().forEach { modelNode ->
                val resolved = resolveNodeTransform(level, hostEntityUuid, modelNode.transform, partialTick) ?: return@forEach
                val bounds = buildNodeRenderBounds(modelNode.model, resolved.transform)
                val visible = frustum?.isVisible(bounds) ?: true
                val entryId = GizmoEntryId(record.snapshotId, modelNode.nodeId)
                val entry = entries.getOrPut(entryId) {
                    GizmoEntry(entryId).also { root.addNode(it.node) }
                }
                val target = resolveTarget(model = modelNode.model, light = null)
                entry.hostEntityUuid = hostEntityUuid
                entry.entityId = record.hostEntity?.id
                entry.snapshot = snapshot
                entry.target = target
                entry.node.isVisible = visible
                entry.modelComponent = modelNode.model
                entry.lightComponent = null
                entry.updateFromResolved(resolved, computeTargetBounds(target, resolved, modelNode.model))
                seen += entryId
                claimedNodes += modelNode.nodeId
                if (activeKey == entryId) inspectorState.refresh(snapshot, target)
            }

            snapshot.lightNodes().forEach { lightNode ->
                if (lightNode.nodeId in claimedNodes) return@forEach
                val resolved = resolveNodeTransform(level, hostEntityUuid, lightNode.transform, partialTick) ?: return@forEach
                val target = resolveTarget(model = null, light = lightNode.light)
                val bounds = computeTargetBounds(target, resolved, null)
                val visible = frustum?.isVisible(bounds) ?: true
                val entryId = GizmoEntryId(record.snapshotId, lightNode.nodeId)
                val entry = entries.getOrPut(entryId) {
                    GizmoEntry(entryId).also { root.addNode(it.node) }
                }
                entry.hostEntityUuid = hostEntityUuid
                entry.entityId = record.hostEntity?.id
                entry.snapshot = snapshot
                entry.target = target
                entry.node.isVisible = visible
                entry.modelComponent = null
                entry.lightComponent = lightNode.light
                entry.updateFromResolved(resolved, bounds)
                seen += entryId
                if (activeKey == entryId) inspectorState.refresh(snapshot, target)
            }
        }

        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val (entryId, entry) = iterator.next()
            if (entryId in seen) continue
            if (hoveredKey == entryId) hoveredKey = null
            if (draggingKey == entryId) draggingKey = null
            if (activeKey == entryId) activeKey = null
            if (contextMenu?.entryId == entryId) contextMenu = null
            if (inspectorState.isVisibleFor(entryId.snapshotId)) inspectorState.close()
            root.removeNode(entry.node)
            entry.node.release()
            iterator.remove()
        }

        syncEntryPresentation()
    }

    private fun resolveTarget(model: Model?, light: LightComponent?): TransformGizmoTarget {
        if (model != null) {
            return TransformGizmoTarget(TransformGizmoTargetType.MODEL, "Model", MODEL_ICON)
        }
        return when (light) {
            is PointLightComponent -> TransformGizmoTarget(TransformGizmoTargetType.POINT_LIGHT, "Point Light", POINT_LIGHT_ICON)
            is SpotLightComponent -> TransformGizmoTarget(TransformGizmoTargetType.SPOT_LIGHT, "Spot Light", SPOT_LIGHT_ICON)
            else -> TransformGizmoTarget(TransformGizmoTargetType.TRANSFORM, "Transform", TRANSFORM_ICON)
        }
    }

    private fun computeTargetBounds(
        target: TransformGizmoTarget,
        resolved: ResolvedNodeTransform,
        model: Model?,
    ): AABB = when (target.type) {
        TransformGizmoTargetType.MODEL -> {
            if (model != null) buildNodeRenderBounds(model, resolved.transform)
            else buildGenericBounds(resolved.transform)
        }

        TransformGizmoTargetType.POINT_LIGHT,
        TransformGizmoTargetType.SPOT_LIGHT,
        -> buildLightEditorBounds(resolved.transform.translation)

        TransformGizmoTargetType.TRANSFORM -> buildGenericBounds(resolved.transform)
    }

    private fun syncEntryPresentation() {
        entries.forEach { (entryKey, entry) ->
            entry.updatePresentation(
                isHovered = hoveredKey == entryKey,
                isActive = activeKey == entryKey,
            )
        }
    }

    private fun prepareRenderSceneState(): Boolean {
        if (!isInitialized || !isEditorAvailable()) return false
        updateInputBlockingState()
        syncVisibleEntries()
        KoolManager.context.backend.collectScene(scene, latePassData)
        return true
    }

    private fun preparePickState(): Boolean {
        if (!isInitialized || !isEditorAvailable()) return false

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

    private fun renderLateScene() {
        if (!prepareRenderSceneState()) return
        overlayLabelState.set(currentLabelState())
        if (overlaySurface.parent == null) {
            ScriptingEnvironmentOverlay.scene.addNode(overlaySurface)
        }
        overlaySurface.triggerUpdate()

        GlContext.withState {
            KoolManager.context.backend.renderCollectedScene(latePassData)
        }
    }

    private fun currentLabelState(): OverlayLabelState? {
        val preferred = draggingKey ?: activeKey ?: hoveredKey
        preferred?.let { key ->
            entries[key]?.currentLabelState()?.let { return it }
        }
        return entries.values.firstNotNullOfOrNull(GizmoEntry::currentLabelState)
    }

    private fun pickEntry(pointer: Pointer): PickResult? {
        if (!preparePickState()) return null

        val activeEntry = activeKey?.let(entries::get)?.takeIf { it.node.isVisible }
        if (activeEntry != null) {
            val handleRay = RayTest()
            if (scene.computePickRay(pointer, handleRay.ray)) {
                val test = RayTest()
                test.clear(camera = scene.camera)
                test.ray.set(handleRay.ray)
                activeEntry.gizmo.rayTest(test)
                if (test.isHit) return PickResult(activeEntry, PickTarget.HANDLE)
            }
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

    private fun isOverlayUiHit(x: Float, y: Float): Boolean {
        val windowSize = KoolManager.context.window.size
        val menu = contextMenu
        if (menu != null &&
            x >= menu.position.x &&
            x <= menu.position.x + CONTEXT_MENU_WIDTH &&
            y >= menu.position.y &&
            y <= menu.position.y + CONTEXT_MENU_HEIGHT
        ) {
            return true
        }

        if (inspectorState.isVisible) {
            val left = windowSize.x - INSPECTOR_WIDTH - INSPECTOR_MARGIN
            return x >= left && y >= INSPECTOR_MARGIN
        }

        return false
    }

    private fun refreshInspectorIfSelected(entryId: GizmoEntryId) {
        if (!inspectorState.isVisibleFor(entryId.snapshotId)) return
        val entry = entries[entryId] ?: return
        val snapshot = entry.snapshot ?: return
        val target = entry.target ?: return
        inspectorState.refresh(snapshot, target)
    }

    private fun applySnapshotUpdate(snapshot: Snapshot) {
        val snapshotId = snapshot.snapshotIdOrNull() ?: return
        val level = Minecraft.getInstance().level ?: return
        val service = NodeRuntimeState.service(level)
        service.materialize(snapshot)
        val activeEntry = entries.values.firstOrNull { it.entryId.snapshotId == snapshotId }
        if (activeEntry != null) {
            inspectorState.refresh(snapshot, activeEntry.target ?: resolveTarget(activeEntry.modelComponent, activeEntry.lightComponent))
            activeEntry.refreshFromRuntime()
        }
        if (snapshot is ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot) {
            activeEntry?.entityId?.let { EntitySnapshotUpdatePacket(it, snapshot).send() }
        } else {
            TransformGizmoInspectorPackets.sendSnapshot(snapshot)
        }
    }

    private object PointerRouter : InputStack.PointerListener {
        override fun handlePointer(pointerState: PointerState, ctx: de.fabmax.kool.KoolContext) {
            if (!isInitialized) return
            if (!isEditorAvailable() || !isWorldInputEnabled()) {
                hoveredKey = null
                draggingKey = null
                updateInputBlockingState()
                syncEntryPresentation()
                return
            }

            val pointer = pointerState.primaryPointer
            if (isOverlayUiHit(pointer.pos.x, pointer.pos.y)) {
                hoveredKey = null
                updateInputBlockingState()
                syncEntryPresentation()
                return
            }

            val draggingEntry = draggingKey?.let(entries::get)
            if (draggingEntry != null) {
                draggingEntry.gizmo.applySpeedAndTickRate()
                draggingEntry.gizmo.handlePointer(pointerState, ctx)
                hoveredKey = draggingEntry.entryId
                activeKey = draggingEntry.entryId
                if (!pointer.isLeftButtonDown && !draggingEntry.gizmo.isManipulating) {
                    draggingKey = null
                    refreshInspectorIfSelected(draggingEntry.entryId)
                }
                syncEntryPresentation()
                updateInputBlockingState()
                return
            }

            if (isMouseOverDock(pointer.pos.x, pointer.pos.y)) {
                hoveredKey = null
                updateInputBlockingState()
                syncEntryPresentation()
                return
            }

            val hoveredPick = pickEntry(pointer)
            hoveredKey = hoveredPick?.entry?.entryId

            when (hoveredPick?.target) {
                PickTarget.HANDLE -> {
                    hoveredPick.entry.gizmo.applySpeedAndTickRate()
                    hoveredPick.entry.gizmo.handlePointer(pointerState, ctx)
                    if (hoveredPick.entry.gizmo.isManipulating || pointer.isConsumed()) {
                        activeKey = hoveredPick.entry.entryId
                        draggingKey = hoveredPick.entry.entryId
                        contextMenu = null
                    } else if (pointer.isRightButtonClicked) {
                        activeKey = hoveredPick.entry.entryId
                        contextMenu = ContextMenuState(hoveredPick.entry.entryId, Vec2f(pointer.pos.x, pointer.pos.y))
                        pointer.consume()
                    }
                }

                PickTarget.BOUNDS -> {
                    if (pointer.isLeftButtonClicked) {
                        activeKey = hoveredPick.entry.entryId
                        contextMenu = null
                        pointer.consume()
                    } else if (pointer.isRightButtonClicked) {
                        activeKey = hoveredPick.entry.entryId
                        contextMenu = ContextMenuState(hoveredPick.entry.entryId, Vec2f(pointer.pos.x, pointer.pos.y))
                        pointer.consume()
                    }
                }

                null -> {
                    if (pointer.isLeftButtonClicked) clearSelection()
                    else if (pointer.isRightButtonClicked) contextMenu = null
                }
            }

            syncEntryPresentation()
            updateInputBlockingState()
        }
    }

    private class GizmoEntry(
        val entryId: GizmoEntryId,
    ) {
        val snapshotId: java.util.UUID get() = entryId.snapshotId
        val nodeId: java.util.UUID get() = entryId.nodeId

        val gizmo = GizmoNode("transform-gizmo-$snapshotId-$nodeId").apply {
            gizmoSize = 2.5f
        }
        val translationOverlay = TranslationOverlay(gizmo)
        val rotationOverlay = RotationOverlay(gizmo)
        val scaleOverlay = ScaleOverlay(gizmo)
        private val lightVisual = Node("transform-light-visual-$snapshotId-$nodeId").apply {
            transform = TrsTransformF()
        }
        private val lightVisualTransform = lightVisual.transform as TrsTransformF
        private val pointLightMesh = createPointLightMesh()
        private val pointLightTransform = pointLightMesh.transform as TrsTransformF
        private val spotOuterMesh = createSpotLightMesh("spot-outer")
        private val spotOuterTransform = spotOuterMesh.transform as TrsTransformF
        private val spotInnerMesh = createSpotLightMesh("spot-inner")
        private val spotInnerTransform = spotInnerMesh.transform as TrsTransformF
        private val boundsShader = KslUnlitShader {
            color { uniformColor(BOUNDS_COLOR) }
            pipeline { lineWidth = 3f }
        }
        private val boundsMesh = createBoundsMesh()
        private val boundsTransform = boundsMesh.transform as TrsTransformF
        private val translationHandles = buildTranslationHandles()
        private val rotationHandles = buildRotationHandles()
        private val scaleHandles = buildScaleHandles()
        val node = Node("transform-gizmo-root-$snapshotId-$nodeId").apply {
            addNode(boundsMesh)
            addNode(lightVisual)
            addNode(gizmo)
            addNode(translationOverlay)
            addNode(rotationOverlay)
            addNode(scaleOverlay)
        }

        var hostEntityUuid: UUID? = null
        var entityId: Int? = null
        var snapshot: Snapshot? = null
        var target: TransformGizmoTarget? = null
        var modelComponent: Model? = null
        var lightComponent: LightComponent? = null

        private var lastAppliedTransform: TransformComponent? = null
        private var lastBounds: AABB? = null
        private var lastBoundsColor = BOUNDS_COLOR
        private var lastResolved: ResolvedNodeTransform? = null
        private val gizmoWorldMatrix = MutableMat4d()
        private val decomposedTranslation = MutableVec3d()
        private val decomposedRotation = MutableQuatD()
        private val decomposedScale = MutableVec3d()

        init {
            translationOverlay.isPickable = false
            rotationOverlay.isPickable = false
            scaleOverlay.isPickable = false
            lightVisual.addNode(pointLightMesh)
            lightVisual.addNode(spotOuterMesh)
            lightVisual.addNode(spotInnerMesh)
            gizmo.gizmoListeners += translationOverlay
            gizmo.gizmoListeners += rotationOverlay
            gizmo.gizmoListeners += scaleOverlay
            configureMode(TransformGizmoEditor.mode)
            gizmo.gizmoListeners += object : GizmoListener {
                override fun onManipulationStart(startTransform: TrsTransformD) {
                    TransformGizmoEditor.activeKey = entryId
                    TransformGizmoEditor.draggingKey = entryId
                }

                override fun onManipulationFinished(startTransform: TrsTransformD, endTransform: TrsTransformD) {
                    TransformGizmoEditor.draggingKey = null
                    TransformGizmoEditor.refreshInspectorIfSelected(entryId)
                }

                override fun onManipulationCanceled(startTransform: TrsTransformD) {
                    TransformGizmoEditor.draggingKey = null
                    refreshFromRuntime()
                    TransformGizmoEditor.refreshInspectorIfSelected(entryId)
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
            refreshGizmoTransformForMode()
        }

        fun refreshFromRuntime() {
            val level = Minecraft.getInstance().level ?: return
            val snapshot = NodeRuntimeState.service(level).snapshot(snapshotId) ?: return
            val nodeSnapshot = snapshot.nodeByIdOrNull(nodeId) ?: return
            val transform = nodeSnapshot.components.filterIsInstance<TransformComponent>().firstOrNull() ?: TransformComponent()
            val model = nodeSnapshot.components.filterIsInstance<Model>().firstOrNull()
            val light = nodeSnapshot.components.filterIsInstance<LightComponent>().firstOrNull()
            val target = TransformGizmoEditor.resolveTarget(model, light)
            val hostEntityUuid = snapshot.hostEntityUuidOrNull() ?: this.hostEntityUuid
            val resolved = resolveNodeTransform(level, hostEntityUuid, transform, TickHandler.partialTick) ?: return

            this.hostEntityUuid = hostEntityUuid
            this.snapshot = snapshot
            this.target = target
            this.modelComponent = model
            this.lightComponent = light
            lastAppliedTransform = transform
            updateFromResolved(resolved, TransformGizmoEditor.computeTargetBounds(target, resolved, model))
        }

        fun updateFromResolved(
            resolved: ResolvedNodeTransform,
            bounds: AABB,
        ) {
            lastResolved = resolved
            if (!gizmo.isManipulating) {
                refreshGizmoTransformForMode(resolved)
            }
            rebuildBounds(bounds)
            updateLightVisual(
                resolved = resolved,
                isHovered = TransformGizmoEditor.hoveredKey == entryId,
                isActive = TransformGizmoEditor.activeKey == entryId,
            )
        }

        fun refreshGizmoTransformForMode(resolved: ResolvedNodeTransform? = lastResolved) {
            val current = resolved ?: return
            if (gizmo.isManipulating) return
            val gizmoRotation = when (TransformGizmoEditor.mode) {
                GizmoEditMode.TRANSLATE -> quatFToGizmoRotation(QuatF.IDENTITY)
                GizmoEditMode.ROTATE,
                GizmoEditMode.SCALE,
                -> quatFToGizmoRotation(current.transform.rotation)
            }
            val gizmoScale = when (TransformGizmoEditor.mode) {
                GizmoEditMode.SCALE -> Vec3d(
                    current.transform.scale.x.toDouble(),
                    current.transform.scale.y.toDouble(),
                    current.transform.scale.z.toDouble(),
                )
                GizmoEditMode.TRANSLATE,
                GizmoEditMode.ROTATE,
                -> Vec3d(1.0, 1.0, 1.0)
            }

            gizmo.gizmoTransform.setCompositionOf(
                Vec3d(
                    current.transform.translation.x.toDouble(),
                    current.transform.translation.y.toDouble(),
                    current.transform.translation.z.toDouble(),
                ),
                gizmoRotation,
                gizmoScale,
            )
            gizmo.updateModelMatRecursiveDown()
        }

        fun updatePresentation(isHovered: Boolean, isActive: Boolean) {
            val isGizmoVisible = isActive || gizmo.isManipulating
            gizmo.isVisible = isGizmoVisible

            val color = when {
                isActive -> ACTIVE_BOUNDS_COLOR
                isHovered -> HOVER_BOUNDS_COLOR
                else -> BOUNDS_COLOR
            }
            if (lastBounds != null && color != lastBoundsColor) {
                rebuildBounds(lastBounds!!, color)
            }
            lastResolved?.let { updateLightVisual(it, isHovered, isActive) }
        }

        private fun rebuildBounds(bounds: AABB, overrideColor: Color? = null) {
            val color = overrideColor ?: when {
                TransformGizmoEditor.activeKey == entryId -> ACTIVE_BOUNDS_COLOR
                TransformGizmoEditor.hoveredKey == entryId -> HOVER_BOUNDS_COLOR
                else -> BOUNDS_COLOR
            }
            if (bounds == lastBounds && color == lastBoundsColor) return

            val sizeX = max((bounds.maxX - bounds.minX).toFloat(), 0.05f)
            val sizeY = max((bounds.maxY - bounds.minY).toFloat(), 0.05f)
            val sizeZ = max((bounds.maxZ - bounds.minZ).toFloat(), 0.05f)
            val centerX = ((bounds.minX + bounds.maxX) * 0.5).toFloat()
            val centerY = ((bounds.minY + bounds.maxY) * 0.5).toFloat()
            val centerZ = ((bounds.minZ + bounds.maxZ) * 0.5).toFloat()

            boundsTransform.setCompositionOf(
                Vec3f(centerX, centerY, centerZ),
                QuatF.IDENTITY,
                Vec3f(sizeX, sizeY, sizeZ),
            )
            boundsShader.color = color
            boundsMesh.updateModelMatRecursiveDown()
            lastBounds = bounds
            lastBoundsColor = color
        }

        private fun createBoundsMesh(): LineMesh =
            LineMesh("transform-bounds-$snapshotId").apply {
                isCastingShadow = false
                transform = TrsTransformF()
                shader = boundsShader
                addBoundingBox(
                    BoundingBoxF(Vec3f(-0.5f, -0.5f, -0.5f), Vec3f(0.5f, 0.5f, 0.5f)),
                    color = BOUNDS_COLOR,
                )
            }

        private fun updateLightVisual(
            resolved: ResolvedNodeTransform,
            isHovered: Boolean,
            isActive: Boolean,
        ) {
            val light = lightComponent
            if (light == null || !node.isVisible) {
                lightVisual.isVisible = false
                return
            }

            val color = when {
                isActive -> ACTIVE_LIGHT_VISUAL_COLOR
                isHovered -> HOVER_LIGHT_VISUAL_COLOR
                else -> DEFAULT_LIGHT_VISUAL_COLOR
            }

            when (light) {
                is PointLightComponent -> {
                    if (!isActive) {
                        pointLightMesh.isVisible = false
                        spotOuterMesh.isVisible = false
                        spotInnerMesh.isVisible = false
                        lightVisual.isVisible = false
                        return
                    }
                    val visualSize = pointLightVisualSize(light)
                    lightVisualTransform.setCompositionOf(
                        resolved.transform.translation,
                        QuatF.IDENTITY,
                        Vec3f(1f, 1f, 1f),
                    )
                    pointLightTransform.setCompositionOf(
                        Vec3f.ZERO,
                        QuatF.IDENTITY,
                        Vec3f(visualSize, visualSize, visualSize),
                    )
                    pointLightMesh.visualShader.color = color
                    pointLightMesh.isVisible = true
                    spotOuterMesh.isVisible = false
                    spotInnerMesh.isVisible = false
                    lightVisual.isVisible = true
                    lightVisual.updateModelMatRecursiveDown()
                }

                is SpotLightComponent -> {
                    val previewDistance = spotLightPreviewDistance(light)
                    val outerRadius = max((tan(Math.toRadians((light.outerAngle * 0.5f).toDouble())) * previewDistance).toFloat(), 0.035f)
                    val innerRadius = max((tan(Math.toRadians((light.innerAngle * 0.5f).toDouble())) * previewDistance).toFloat(), 0.015f)
                    lightVisualTransform.setCompositionOf(
                        resolved.transform.translation,
                        resolved.transform.rotation,
                        Vec3f(1f, 1f, 1f),
                    )
                    spotOuterTransform.setCompositionOf(
                        Vec3f.ZERO,
                        QuatF.IDENTITY,
                        Vec3f(outerRadius, outerRadius, previewDistance),
                    )
                    spotInnerTransform.setCompositionOf(
                        Vec3f.ZERO,
                        QuatF.IDENTITY,
                        Vec3f(innerRadius, innerRadius, previewDistance),
                    )
                    spotOuterMesh.visualShader.color = color
                    spotInnerMesh.visualShader.color = color.withAlpha(color.a * 0.45f)
                    pointLightMesh.isVisible = false
                    spotOuterMesh.isVisible = true
                    spotInnerMesh.isVisible = light.innerAngle > 0.01f
                    lightVisual.isVisible = true
                    lightVisual.updateModelMatRecursiveDown()
                }
            }
        }

        private fun createPointLightMesh(): LightVisualMesh =
            LightVisualMesh("point-light-visual-$snapshotId").apply {
                transform = TrsTransformF()
                addCircleLines(Vec3f.X_AXIS)
                addCircleLines(Vec3f.Y_AXIS)
                addCircleLines(Vec3f.Z_AXIS)
                addAxisRays()
            }

        private fun createSpotLightMesh(name: String): LightVisualMesh =
            LightVisualMesh("$name-$snapshotId").apply {
                transform = TrsTransformF()
                addConeLines()
            }

        fun currentLabelState(): OverlayLabelState? {
            if (!gizmo.isManipulating) return null
            return when {
                translationOverlay.isVisible && translationOverlay.isLabelValid ->
                    OverlayLabelState(translationOverlay.labelPosition, translationOverlay.labelValue)

                rotationOverlay.isVisible && rotationOverlay.isLabelValid ->
                    OverlayLabelState(rotationOverlay.labelPosition, rotationOverlay.labelValue)

                scaleOverlay.isVisible && scaleOverlay.isLabelValid ->
                    OverlayLabelState(scaleOverlay.labelPosition, scaleOverlay.labelValue)

                gizmo.isManipulating -> fallbackLabelState()
                else -> null
            }
        }

        fun boundsHitDistance(ray: de.fabmax.kool.math.RayD): Double? {
            val bounds = lastBounds ?: return null
            val pickBounds = inflatePickBounds(bounds, target?.type)
            return intersectRayAabb(
                ray.origin.x,
                ray.origin.y,
                ray.origin.z,
                ray.direction.x,
                ray.direction.y,
                ray.direction.z,
                pickBounds,
            )
        }

        private fun applyFromGizmo() {
            val level = Minecraft.getInstance().level ?: return
            gizmoWorldMatrix.setIdentity()
                .mul(gizmo.gizmoTransform.matrixD)
            gizmoWorldMatrix.decompose(decomposedTranslation, decomposedRotation, decomposedScale)
            val resolved = lastResolved ?: return
            val worldPosition = Vec3(decomposedTranslation.x, decomposedTranslation.y, decomposedTranslation.z)
            val worldRotation = when (TransformGizmoEditor.mode) {
                GizmoEditMode.TRANSLATE -> resolved.transform.rotation
                GizmoEditMode.ROTATE,
                GizmoEditMode.SCALE,
                -> gizmoRotationToQuatF(decomposedRotation)
            }
            val worldScale = when (TransformGizmoEditor.mode) {
                GizmoEditMode.TRANSLATE,
                GizmoEditMode.ROTATE,
                -> Vec3f(resolved.transform.scale)
                GizmoEditMode.SCALE -> Vec3f(
                    decomposedScale.x.toFloat(),
                    decomposedScale.y.toFloat(),
                    decomposedScale.z.toFloat(),
                )
            }
            val updatedTransform = worldTransformToComponent(
                level = level,
                hostEntityUuid = hostEntityUuid,
                worldPosition = worldPosition,
                worldRotation = worldRotation,
                worldScale = worldScale,
                partialTick = TickHandler.partialTick,
            ) ?: return

            if (updatedTransform == lastAppliedTransform) return
            lastAppliedTransform = updatedTransform

            val service = NodeRuntimeState.service(level)
            val snapshot = service.snapshot(snapshotId) ?: return
            val updatedSnapshot = when (snapshot) {
                is LevelSnapshot -> snapshot
                    .withWorldBinding(
                        Vec3(
                            updatedTransform.translation.x.toDouble(),
                            updatedTransform.translation.y.toDouble(),
                            updatedTransform.translation.z.toDouble(),
                        ),
                    )
                    .withOrReplace(updatedTransform, nodeId)
                else -> snapshot.withOrReplace(updatedTransform, nodeId)
            }

            this.snapshot = updatedSnapshot
            service.materialize(updatedSnapshot)
            NodeTransformUpdatePacket(snapshotId, nodeId, updatedTransform).send()
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

    private data class GizmoEntryId(val snapshotId: java.util.UUID, val nodeId: java.util.UUID)
    private data class PickResult(val entry: GizmoEntry, val target: PickTarget)
    private data class ContextMenuState(val entryId: GizmoEntryId, val position: Vec2f)
    private data class OverlayLabelState(val position: Vec2f, val value: Double)

    private enum class PickTarget {
        HANDLE,
        BOUNDS,
    }

    private val BOUNDS_COLOR = Color(0.68f, 0.70f, 0.74f, 0.52f)
    private val HOVER_BOUNDS_COLOR = Color(0.4f, 0.9f, 1f, 0.75f)
    private val ACTIVE_BOUNDS_COLOR = Color(1f, 0.8f, 0.25f, 0.95f)
    private val DEFAULT_LIGHT_VISUAL_COLOR = Color(0.82f, 0.84f, 0.88f, 0.92f)
    private val HOVER_LIGHT_VISUAL_COLOR = HOVER_BOUNDS_COLOR.withAlpha(0.95f)
    private val ACTIVE_LIGHT_VISUAL_COLOR = ACTIVE_BOUNDS_COLOR
}

enum class GizmoEditMode {
    TRANSLATE,
    ROTATE,
    SCALE,
}

private fun buildTranslationHandles(): List<GizmoHandle> = listOf(
    AxisHandle(color = MdColor.RED, axis = GizmoHandle.Axis.POS_X, handleShape = AxisHandle.HandleType.ARROW, name = "axis-POS_X"),
    AxisHandle(color = MdColor.LIGHT_GREEN, axis = GizmoHandle.Axis.POS_Y, handleShape = AxisHandle.HandleType.ARROW, name = "axis-POS_Y"),
    AxisHandle(color = MdColor.BLUE, axis = GizmoHandle.Axis.POS_Z, handleShape = AxisHandle.HandleType.ARROW, name = "axis-POS_Z"),
    PlaneHandle(color = MdColor.RED, axis = GizmoHandle.Axis.POS_X, name = "plane-POS_X"),
    PlaneHandle(color = MdColor.LIGHT_GREEN, axis = GizmoHandle.Axis.POS_Y, name = "plane-POS_Y"),
    PlaneHandle(color = MdColor.BLUE, axis = GizmoHandle.Axis.POS_Z, name = "plane-POS_Z"),
    CenterCircleHandle(color = Color.WHITE, radius = 0.28f),
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
    val absValue = abs(value)
    val precision = when {
        absValue >= 1000.0 -> 0
        absValue >= 100.0 -> 1
        absValue >= 1.0 -> 2
        absValue >= 0.01 -> 3
        else -> 4
    }
    return "%.${precision}f".format(java.util.Locale.ROOT, value)
}

private fun buildLightEditorBounds(position: Vec3f): AABB {
    val radius = 0.12
    return AABB(
        position.x - radius,
        position.y - radius,
        position.z - radius,
        position.x + radius,
        position.y + radius,
        position.z + radius,
    )
}

private fun buildGenericBounds(transform: TrsTransformF): AABB {
    val position = transform.translation
    val scale = transform.scale
    val radius = max(max(abs(scale.x), abs(scale.y)), abs(scale.z)).coerceAtLeast(0.5f).toDouble() * 0.125
    return AABB(
        position.x - radius,
        position.y - radius,
        position.z - radius,
        position.x + radius,
        position.y + radius,
        position.z + radius,
    )
}

private fun pointLightVisualSize(light: PointLightComponent): Float =
    (light.radius * 0.08f).coerceIn(0.22f, 1.35f)

private fun spotLightPreviewDistance(light: SpotLightComponent): Float =
    (light.distance * 0.14f).coerceIn(0.45f, 2.4f)

private class LightVisualMesh(name: String) : CustomLineMesh<VertexLayouts.PositionColor>(
    IndexedVertexList(VertexLayouts.PositionColor, primitiveType = PrimitiveType.LINES),
    name,
) {
    val visualShader = KslUnlitShader {
        color { uniformColor(Color.WHITE) }
        pipeline { lineWidth = 2.25f }
    }

    init {
        isCastingShadow = false
        shader = visualShader
    }
}

private fun LightVisualMesh.addAxisRays(rayLength: Float = 1.25f) {
    addLine(Vec3f(-rayLength, 0f, 0f), Vec3f(rayLength, 0f, 0f))
    addLine(Vec3f(0f, -rayLength, 0f), Vec3f(0f, rayLength, 0f))
    addLine(Vec3f(0f, 0f, -rayLength), Vec3f(0f, 0f, rayLength))
}

private fun LightVisualMesh.addCircleLines(
    axis: Vec3f,
    radius: Float = 1f,
    segments: Int = 24,
) {
    fun point(angle: Double): Vec3f = when (axis) {
        Vec3f.X_AXIS -> Vec3f(0f, (cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
        Vec3f.Y_AXIS -> Vec3f((cos(angle) * radius).toFloat(), 0f, (sin(angle) * radius).toFloat())
        else -> Vec3f((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat(), 0f)
    }

    for (i in 0 until segments) {
        val a0 = i / segments.toDouble() * PI * 2.0
        val a1 = (i + 1) / segments.toDouble() * PI * 2.0
        addLine(point(a0), point(a1))
    }
}

private fun LightVisualMesh.addConeLines(segments: Int = 72) {
    val tip = Vec3f(0f, 0f, 0f)
    val center = Vec3f(0f, 0f, 1f)
    addLine(tip, center)

    for (i in 0 until segments) {
        val a0 = i / segments.toDouble() * PI * 2.0
        val a1 = (i + 1) / segments.toDouble() * PI * 2.0
        val p0 = Vec3f(cos(a0).toFloat(), sin(a0).toFloat(), 1f)
        val p1 = Vec3f(cos(a1).toFloat(), sin(a1).toFloat(), 1f)
        addLine(p0, p1)
    }

    addLine(tip, Vec3f(1f, 0f, 1f))
    addLine(tip, Vec3f(-1f, 0f, 1f))
    addLine(tip, Vec3f(0f, 1f, 1f))
    addLine(tip, Vec3f(0f, -1f, 1f))
}

private fun inflatePickBounds(bounds: AABB, targetType: TransformGizmoTargetType?): AABB {
    val sizeX = bounds.maxX - bounds.minX
    val sizeY = bounds.maxY - bounds.minY
    val sizeZ = bounds.maxZ - bounds.minZ
    val basePadding = max(sizeX, max(sizeY, sizeZ)) * 0.15
    val padding = when (targetType) {
        TransformGizmoTargetType.POINT_LIGHT,
        TransformGizmoTargetType.SPOT_LIGHT,
        -> max(basePadding, 0.18)

        else -> basePadding.coerceIn(0.05, 0.35)
    }
    return bounds.inflate(padding)
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
        if (abs(dir) < 1.0e-9) return origin in min..max
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
