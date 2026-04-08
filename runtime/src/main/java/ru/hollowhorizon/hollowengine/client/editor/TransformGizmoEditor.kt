package ru.hollowhorizon.hollowengine.client.editor

import de.fabmax.kool.KeyValueStore
import de.fabmax.kool.PassData
import de.fabmax.kool.input.InputStack
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.Pointer
import de.fabmax.kool.input.PointerState
import de.fabmax.kool.math.MutableVec3d
import de.fabmax.kool.math.RayTest
import de.fabmax.kool.math.Vec3d
import de.fabmax.kool.modules.gizmo.*
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.pipeline.ClearColorDontCare
import de.fabmax.kool.pipeline.ClearDepthDontCare
import de.fabmax.kool.pipeline.DepthMode
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.TrsTransformD
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.scripting.isMouseOverDock
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.client.kool.KoolInitEvent
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.client.kool.gl.MCRenderBackendGl
import ru.hollowhorizon.hollowengine.client.kool.minecraft.mcCamera
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
    private val pickTest = RayTest()
    private val latePassData = PassData()

    private val entries = linkedMapOf<java.util.UUID, GizmoEntry>()

    private var hoveredKey: java.util.UUID? = null
    private var draggingKey: java.util.UUID? = null
    private var activeKey: java.util.UUID? = null
    private var lastFrustum: Frustum? = null
    private var isInitialized = false

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
                val transform = gearyEntity.get<TransformComponent>() ?: TransformComponent()
                val resolved = resolveAnchoredTransform(level, record.anchor, transform, partialTick) ?: return@forEach
                val visible = frustum?.isVisible(buildAnchoredRenderBounds(model, resolved.position, model.scale * resolved.scale)) ?: true

                val entry = entries.getOrPut(record.stableKey) {
                    GizmoEntry(record.stableKey).also { root.addNode(it.node) }
                }
                entry.anchor = record.anchor
                entry.node.isVisible = visible
                entry.updateFromResolved(resolved)
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
            root.removeNode(entry.node)
            entry.node.release()
            iterator.remove()
        }
    }

    private fun prepareSceneState(): Boolean {
        if (!isInitialized || !isEditorAvailable()) return false
        syncVisibleEntries()
        val backend = KoolManager.context.backend as? MCRenderBackendGl ?: return false
        backend.collectScene(scene, latePassData)
        return true
    }

    private fun renderLateScene() {
        if (!prepareSceneState()) return
        val backend = KoolManager.context.backend as? MCRenderBackendGl ?: return
        backend.renderCollectedScene(latePassData)
    }

    private fun pickEntry(pointer: Pointer): GizmoEntry? {
        if (!prepareSceneState()) return null
        pickTest.clear(camera = scene.camera)
        if (!scene.computePickRay(pointer, pickTest.ray)) return null
        root.rayTest(pickTest)
        val hitNode = pickTest.hitNode ?: return null
        val gizmo = hitNode.findParentOfType<GizmoNode>() ?: return null
        return entries.values.firstOrNull { it.gizmo === gizmo && it.node.isVisible }
    }

    private object PointerRouter : InputStack.PointerListener {
        override fun handlePointer(pointerState: PointerState, ctx: de.fabmax.kool.KoolContext) {
            if (!isInitialized) return
            if (!isEditorAvailable()) {
                hoveredKey = null
                draggingKey = null
                return
            }

            val pointer = pointerState.primaryPointer
            val draggingEntry = draggingKey?.let(entries::get)
            if (draggingEntry != null) {
                draggingEntry.gizmo.applySpeedAndTickRate()
                draggingEntry.gizmo.handlePointer(pointerState, ctx)
                hoveredKey = draggingEntry.stableKey
                activeKey = draggingEntry.stableKey
                if (!pointer.isLeftButtonDown && !draggingEntry.gizmo.isManipulating) {
                    draggingKey = null
                }
                return
            }

            if (isMouseOverDock(pointer.pos.x, pointer.pos.y)) {
                hoveredKey = null
                return
            }

            val hoveredEntry = pickEntry(pointer)
            hoveredKey = hoveredEntry?.stableKey
            activeKey = hoveredEntry?.stableKey ?: activeKey

            if (hoveredEntry != null) {
                hoveredEntry.gizmo.applySpeedAndTickRate()
                hoveredEntry.gizmo.handlePointer(pointerState, ctx)
                if (hoveredEntry.gizmo.isManipulating || pointer.isLeftButtonDown) {
                    draggingKey = hoveredEntry.stableKey
                }
            }
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
        private val translationHandles = buildTranslationHandles()
        private val rotationHandles = buildRotationHandles()
        private val scaleHandles = buildScaleHandles()
        val node = Node("transform-gizmo-root-$stableKey").apply {
            addNode(gizmo)
            addNode(translationOverlay)
            addNode(rotationOverlay)
            addNode(scaleOverlay)
        }

        var anchor: AnchorComponent = WorldAnchor()
        private var lastAppliedTransform: TransformComponent? = null

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
            val resolved = resolveAnchoredTransform(level, anchor, transform, TickHandler.partialTick) ?: return
            updateFromResolved(resolved)
        }

        fun updateFromResolved(resolved: ru.hollowhorizon.hollowengine.client.render.ResolvedAnchorTransform) {
            if (gizmo.isManipulating) return
            gizmo.gizmoTransform.setCompositionOf(
                Vec3d(resolved.position.x, resolved.position.y, resolved.position.z),
                yawPitchToGizmoRotation(resolved.yaw, resolved.pitch),
                MutableVec3d(resolved.scale.toDouble(), resolved.scale.toDouble(), resolved.scale.toDouble()),
            )
            gizmo.updateModelMatRecursive()
        }

        private fun applyFromGizmo() {
            val level = Minecraft.getInstance().level ?: return
            val position = gizmo.gizmoTransform.translation
            val (yaw, pitch) = gizmoRotationToYawPitch(gizmo.gizmoTransform.rotation)
            val updatedTransform = worldTransformToComponent(
                level = level,
                anchor = anchor,
                worldPosition = Vec3(position.x, position.y, position.z),
                worldYaw = yaw,
                worldPitch = pitch,
                worldScale = gizmo.gizmoTransform.scale.x.toFloat(),
                partialTick = TickHandler.partialTick,
            ) ?: return

            if (updatedTransform == lastAppliedTransform) return
            lastAppliedTransform = updatedTransform

            val service = MaterializationRuntimeState.service(level)
            val snapshot = service.snapshot(stableKey) ?: return
            val updatedSnapshot = when (val currentAnchor = snapshot.anchorOrNull()) {
                is WorldAnchor -> snapshot
                    .withIdentity(worldAnchorFor(Vec3(updatedTransform.x.toDouble(), updatedTransform.y.toDouble(), updatedTransform.z.toDouble()), currentAnchor.localId), stableKey)
                    .withOrReplace(updatedTransform)
                is EntityAnchor -> snapshot.withOrReplace(updatedTransform)
                else -> return
            }

            service.materialize(updatedSnapshot)
            AnchoredTransformUpdatePacket(stableKey, updatedTransform).send()
        }
    }
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
        name = "axis-POS_X"
    ),
    AxisHandle(
        color = MdColor.LIGHT_GREEN,
        axis = GizmoHandle.Axis.POS_Y,
        handleShape = AxisHandle.HandleType.ARROW,
        name = "axis-POS_Y"
    ),
    AxisHandle(
        color = MdColor.BLUE,
        axis = GizmoHandle.Axis.POS_Z,
        handleShape = AxisHandle.HandleType.ARROW,
        name = "axis-POS_Z"
    ),
    PlaneHandle(
        color = MdColor.RED,
        axis = GizmoHandle.Axis.POS_X,
        name = "plane-POS_X"
    ),
    PlaneHandle(
        color = MdColor.LIGHT_GREEN,
        axis = GizmoHandle.Axis.POS_Y,
        name = "plane-POS_Y"
    ),
    PlaneHandle(
        color = MdColor.BLUE,
        axis = GizmoHandle.Axis.POS_Z,
        name = "plane-POS_Z"
    ),
    CenterCircleHandle(
        color = Color.WHITE,
        radius = 0.28f
    )
)

private fun buildRotationHandles(): List<GizmoHandle> = listOf(
    AxisRotationHandle(color = MdColor.RED, axis = GizmoHandle.Axis.POS_X, radius = 0.95f),
    AxisRotationHandle(color = MdColor.LIGHT_GREEN, axis = GizmoHandle.Axis.POS_Y, radius = 0.95f)
)

private fun buildScaleHandles(): List<GizmoHandle> = listOf(
    CenterCircleHandle(
        color = Color.WHITE,
        radius = 1.2f,
        innerRadius = 0.28f,
        gizmoOperation = UniformScale(),
        name = "scale-uniform",
    )
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
