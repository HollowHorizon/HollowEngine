package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.MimeType
import de.fabmax.kool.PlatformAssetsImpl
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.KoolDispatchers
import de.fabmax.kool.util.SyncedScope
import de.fabmax.kool.util.Uint8BufferImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import kotlin.math.floor

class ImageFile(path: String, var image: ByteArray) :
    EditorFile(path) {

    private var pixels: IntArray = IntArray(0)
    private var imgW: Int = 0
    private var imgH: Int = 0

    private var texture: Texture2d? = null
    private var textureDirty = true

    private var uploadGen: Long = 0

    private var uploadJob: Job? = null
    private var lastUploadNs: Long = 0
    private val uploadIntervalNs = 30_000_000L // ~33ms

    private var backingData: BufferedImageData2d? = null

    private val zoom = mutableStateOf(16f)
    private val brushSize = mutableStateOf(1)
    private val isEraser = mutableStateOf(false)

    private val hue = mutableStateOf(0f)
    private val sat = mutableStateOf(1f)
    private val value = mutableStateOf(1f)
    private val alpha = mutableStateOf(1f)

    private val brushColorArgb: Int
        get() {
            val c = Color.Hsv(hue.value, sat.value, value.value).toSrgb(a = alpha.value)
            val a = (c.a * 255f).toInt().coerceIn(0, 255)
            val r = (c.r * 255f).toInt().coerceIn(0, 255)
            val g = (c.g * 255f).toInt().coerceIn(0, 255)
            val b = (c.b * 255f).toInt().coerceIn(0, 255)
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

    override fun save() {
        // Intentionally no-op for now (devlog mode): keep edits in memory only.
    }

    override fun UiScope.compose() {
        modifier.backgroundColor(ColorTheme.UI.BackgroundGeneral)

        Column(Grow.Std, Grow.Std) {
            modifier.padding(Dimensions.PaddingLarge)

            Row(Grow.Std) {
                modifier
                    .margin(bottom = Dimensions.PaddingMedium)
                    .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingNormal))
                    .padding(Dimensions.PaddingMedium)

                Button(if (isEraser.use()) "Eraser" else "Brush") {
                    modifier.margin(end = Dimensions.PaddingMedium)
                    modifier.onClick { isEraser.set(!isEraser.value) }
                }

                Box(Dimensions.PaddingHuge, Dimensions.PaddingHuge) {
                    modifier
                        .alignY(AlignmentY.Center)
                        .margin(end = Dimensions.PaddingMedium)
                        .background(RoundRectBackground(Color.Hsv(hue.value, sat.value, value.value).toSrgb(a = alpha.value), Dimensions.PaddingSmall))
                        .border(RoundRectBorder(ColorTheme.UI.WhiteReplacement, Dimensions.PaddingSmall, Dimensions.PaddingSmall))
                }

                Text("Size: ${brushSize.use() + 1}") {
                    modifier.alignY(AlignmentY.Center).margin(end = Dimensions.PaddingMedium)
                        .textColor(ColorTheme.UI.WhiteReplacement)
                }

                Slider(value = brushSize.use().toFloat(), min = 0f, max = 16f) {
                    modifier.width(Dp(160f)).alignY(AlignmentY.Center).margin(end = Dimensions.PaddingMedium)
                    modifier.onChange { brushSize.set(it.toInt().coerceIn(0, 64)) }
                }

                Text("Zoom: ${zoom.use().toInt()}x") {
                    modifier.alignY(AlignmentY.Center).margin(end = Dimensions.PaddingMedium)
                        .textColor(ColorTheme.UI.WhiteReplacement)
                }

                Slider(value = zoom.use(), min = 4f, max = 64f) {
                    modifier.width(Dp(200f)).alignY(AlignmentY.Center).margin(end = Dimensions.PaddingMedium)
                    modifier.onChange { zoom.set(it.coerceIn(1f, 128f)) }
                }

                Button("Save") {
                    modifier.alignY(AlignmentY.Center).margin(start = Dimensions.PaddingMedium)
                    modifier.onClick { save() }
                }
            }

            Row(Grow.Std) {
                modifier.margin(bottom = Dimensions.PaddingMedium)
                    .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingNormal))
                    .padding(Dimensions.PaddingMedium)

                ColorWheel(hue.use(), sat.use(), value.use()) {
                    modifier
                        .size(Dp(125f), Dp(125f))
                        .alignY(AlignmentY.Center)
                        .onChange { h, s, v ->
                            hue.set(h)
                            sat.set(s)
                            value.set(v)
                        }
                }

                Slider(alpha.use(), 0f, 1f) {
                    modifier
                        .alignY(AlignmentY.Center)
                        .width(Dp(220f))
                        .margin(start = Dimensions.PaddingLarge)
                        .onChange { alpha.set(it) }
                }
                Text("A: ${(alpha.use() * 100).toInt()}%") {
                    modifier.alignY(AlignmentY.Center)
                        .margin(start = Dimensions.PaddingMedium)
                        .textColor(ColorTheme.UI.WhiteReplacement)
                }
            }

            Box(Grow.Std, Grow.Std) {
                modifier.background(RectBackground(ColorTheme.UI.BackgroundSecondary))

                val tex = remember { getOrCreateTexture() }

                Image(tex) {
                    modifier
                        .imageSize(ImageSize.FixedScale(zoom.use()))
                        .align(AlignmentX.Center, AlignmentY.Center)
                        .onPointer { evt ->
                            handlePointer(evt, uiNode)
                        }
                }
            }
        }
    }

    private fun getOrCreateTexture(): Texture2d {
        val existing = texture
        if (existing != null) return existing

        val loaded = PlatformAssetsImplRead(image)
        val w = loaded?.width ?: 1
        val h = loaded?.height ?: 1
        val buf = Uint8BufferImpl(w * h * 4)
        if (loaded != null) {
            val src = loaded.data as? Uint8BufferImpl
            if (src != null) {
                src.useRaw { srcBb ->
                    buf.useRaw { dstBb ->
                        srcBb.rewind()
                        dstBb.rewind()
                        dstBb.put(srcBb)
                        dstBb.flip()
                    }
                }
            }
        }

        val data = BufferedImageData2d(
            data = buf,
            width = w,
            height = h,
            format = TexFormat.RGBA,
            id = "ImageEditorData:$filePath"
        )

        backingData = data
        imgW = data.width
        imgH = data.height
        pixels = IntArray(imgW * imgH)
        syncPixelsFromBacking(data)

        return Texture2d(
            format = TexFormat.RGBA,
            mipMapping = MipMapping.Off,
            samplerSettings = SamplerSettings().nearest(),
            name = "ImageEditor:$filePath"
        ) {
            data
        }.also {
            texture = it
        }
    }

    private fun handlePointer(evt: PointerEvent, node: UiNode) {
        if (imgW <= 0 || imgH <= 0) return

        // Only react on move / drag / button down events
        val wantPick = evt.pointer.isRightButtonDown || KeyboardInput.isShiftDown
        val wantDraw = evt.pointer.isLeftButtonDown && !KeyboardInput.isAltDown
        if (!wantPick && !wantDraw) return

        // evt.position is local to the node
        val scale = zoom.value

        // ImageNode centers the texture inside its bounds for FixedScale
        val drawW = imgW * scale
        val drawH = imgH * scale
        val ox = (node.widthPx - drawW) * 0.5f
        val oy = (node.heightPx - drawH) * 0.5f

        val localX = evt.position.x - ox
        val localY = evt.position.y - oy

        val px = floor(localX / scale).toInt()
        val py = floor(localY / scale).toInt()
        if (px !in 0 until imgW || py !in 0 until imgH) return

        if (wantPick) {
            val argb = pixels[py * imgW + px]
            val a = ((argb ushr 24) and 0xFF) / 255f
            val r = ((argb ushr 16) and 0xFF) / 255f
            val g = ((argb ushr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val c = Color(r, g, b, a).toHsv()
            hue.set(c.h)
            sat.set(c.s)
            value.set(c.v)
            alpha.set(a)
            return
        }

        val col = if (isEraser.value) 0x00000000 else brushColorArgb
        paintAt(px, py, brushSize.value, col)
        scheduleUpload()
    }

    private fun paintAt(x: Int, y: Int, size: Int, argb: Int) {
        val r = size.coerceAtLeast(0)
        for (dy in -r..r) {
            for (dx in -r..r) {
                val px = x + dx
                val py = y + dy
                if (px !in 0 until imgW || py !in 0 until imgH) continue
                pixels[py * imgW + px] = argb
            }
        }
        textureDirty = true
    }

    private fun scheduleUpload() {
        if (!textureDirty) return
        val now = System.nanoTime()
        if (now - lastUploadNs < uploadIntervalNs) return
        lastUploadNs = now

        if (uploadJob?.isActive == true) return

        // Buffer rewrite on backend thread, then force actual GPU upload by changing imageData.id
        uploadJob = SyncedScope.launch(KoolDispatchers.Backend) {
            val data = backingData ?: return@launch
            val buf = data.data
            writePixelsToBacking(data)

            val gen = ++uploadGen
            val uploadData = BufferedImageData2d(
                data = buf,
                width = data.width,
                height = data.height,
                format = data.format,
                id = "ImageEditorData:$filePath:$gen"
            )

            SyncedScope.launch(KoolDispatchers.Synced) {
                val tex = texture ?: return@launch
                tex.uploadLazy(uploadData)
                textureDirty = false
            }
        }
    }

    private fun PlatformAssetsImplRead(bytes: ByteArray): BufferedImageData2d? {
        return try {
            PlatformAssetsImpl.readImageData(
                bytes.inputStream(),
                MimeType.IMAGE_PNG,
                TexFormat.RGBA,
                null
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun syncPixelsFromBacking(data: BufferedImageData2d) {
        val buf = data.data as? Uint8BufferImpl ?: return
        buf.useRaw { bb ->
            bb.rewind()
            for (i in 0 until (imgW * imgH)) {
                val r = bb.get().toInt() and 0xFF
                val g = bb.get().toInt() and 0xFF
                val b = bb.get().toInt() and 0xFF
                val a = bb.get().toInt() and 0xFF
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun writePixelsToBacking(data: BufferedImageData2d) {
        val buf = data.data as? Uint8BufferImpl ?: return
        buf.useRaw { bb ->
            bb.rewind()
            for (i in 0 until (imgW * imgH)) {
                val argb = pixels[i]
                val a = (argb ushr 24) and 0xFF
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                bb.put(r.toByte())
                bb.put(g.toByte())
                bb.put(b.toByte())
                bb.put(a.toByte())
            }
            bb.flip()
        }
    }

    private fun encodePngFromBackingData(): ByteArray? = null
}