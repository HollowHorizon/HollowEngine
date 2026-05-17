package ru.hollowhorizon.hollowengine.client.ui.render

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL30
import kotlin.math.max
import kotlin.math.min

internal class UiFramebufferPool {
    private val scratchFramebuffers = mutableListOf<UiFramebuffer>()
    private val retiredAtlases = mutableListOf<RetiredUiFramebuffer>()
    private var atlas: UiFramebuffer? = null
    private var allocator = UiAtlasAllocator(1, 1)
    private var atlasBaseWidth = 0
    private var atlasBaseHeight = 0

    fun beginFrame(layerRequests: List<UiLayerRequest>, minimumWidth: Int, minimumHeight: Int) {
        retireOldAtlases()
        if (layerRequests.isEmpty()) {
            allocator = UiAtlasAllocator(1, 1)
            return
        }
        val width = minimumWidth.coerceAtLeast(1)
        val height = minimumHeight.coerceAtLeast(1)
        val current = atlas
        val windowChanged = width != atlasBaseWidth || height != atlasBaseHeight
        val size = if (current != null && !windowChanged && fits(layerRequests, current.width, current.height)) {
            UiAtlasSize(current.width, current.height)
        } else {
            atlasSizeFor(layerRequests, width, height, if (windowChanged) null else current)
        }
        if (current == null || current.width != size.width || current.height != size.height) {
            current?.let { retiredAtlases += RetiredUiFramebuffer(it) }
            atlas = UiFramebuffer(size.width, size.height)
        }
        atlasBaseWidth = width
        atlasBaseHeight = height
        allocator = UiAtlasAllocator(size.width, size.height)
    }

    fun acquireLayer(width: Int, height: Int): UiLayerFramebuffer {
        if (atlas == null) {
            val atlasWidth = width + UiAtlasGuardPixels * 2
            val atlasHeight = height + UiAtlasGuardPixels * 2
            atlas = UiFramebuffer(atlasWidth, atlasHeight)
            allocator = UiAtlasAllocator(atlasWidth, atlasHeight)
        }
        val framebuffer = atlas ?: error("UI framebuffer atlas was not initialized")
        val allocation = allocator.allocate(
            width + UiAtlasGuardPixels * 2,
            height + UiAtlasGuardPixels * 2,
        ) ?: error("UI framebuffer atlas is too small for ${width}x$height layer")
        return UiLayerFramebuffer(
            framebuffer,
            UiAtlasRegion(
                x = allocation.x + UiAtlasGuardPixels,
                y = allocation.y + UiAtlasGuardPixels,
                width = width,
                height = height,
                clearX = allocation.x,
                clearY = allocation.y,
                clearWidth = allocation.width,
                clearHeight = allocation.height,
            )
        )
    }

    fun acquire(width: Int, height: Int, exclude: UiFramebuffer? = null): UiFramebuffer {
        val framebuffer = scratchFramebuffers.firstOrNull {
            it !== exclude && !it.inUse && it.width == width && it.height == height
        } ?: UiFramebuffer(width, height).also {
            scratchFramebuffers += it
        }
        framebuffer.inUse = true
        return framebuffer
    }

    fun release(framebuffer: UiFramebuffer) {
        framebuffer.inUse = false
    }

    fun close() {
        atlas?.close()
        atlas = null
        atlasBaseWidth = 0
        atlasBaseHeight = 0
        retiredAtlases.forEach { it.framebuffer.close() }
        retiredAtlases.clear()
        scratchFramebuffers.forEach(UiFramebuffer::close)
        scratchFramebuffers.clear()
    }

    private fun retireOldAtlases() {
        val iterator = retiredAtlases.iterator()
        while (iterator.hasNext()) {
            val retired = iterator.next()
            retired.framesLeft--
            if (retired.framesLeft <= 0) {
                retired.framebuffer.close()
                iterator.remove()
            }
        }
    }

    private fun atlasSizeFor(
        layerRequests: List<UiLayerRequest>,
        minimumWidth: Int,
        minimumHeight: Int,
        current: UiFramebuffer?,
    ): UiAtlasSize {
        val maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE).coerceAtLeast(1)
        var width = max(minimumWidth, current?.width ?: 0).coerceAtMost(maxTextureSize)
        var height = max(minimumHeight, current?.height ?: 0).coerceAtMost(maxTextureSize)

        layerRequests.forEach {
            width = max(width, it.width + UiAtlasGuardPixels * 2).coerceAtMost(maxTextureSize)
            height = max(height, it.height + UiAtlasGuardPixels * 2).coerceAtMost(maxTextureSize)
        }

        while (!fits(layerRequests, width, height)) {
            val canGrowWidth = width < maxTextureSize
            val canGrowHeight = height < maxTextureSize
            if (!canGrowWidth && !canGrowHeight) break
            if ((width <= height && canGrowWidth) || !canGrowHeight) {
                width = min(width * 2, maxTextureSize)
            } else {
                height = min(height * 2, maxTextureSize)
            }
        }

        return UiAtlasSize(reserveAtlasAxis(width, maxTextureSize), reserveAtlasAxis(height, maxTextureSize))
    }

    private fun fits(layerRequests: List<UiLayerRequest>, width: Int, height: Int): Boolean {
        val testAllocator = UiAtlasAllocator(width, height)
        return layerRequests.all {
            testAllocator.allocate(
                it.width + UiAtlasGuardPixels * 2,
                it.height + UiAtlasGuardPixels * 2,
            ) != null
        }
    }
}

private class RetiredUiFramebuffer(
    val framebuffer: UiFramebuffer,
    var framesLeft: Int = RetiredAtlasFrames,
)

internal data class UiLayerRequest(
    val width: Int,
    val height: Int,
)

internal data class UiAtlasRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val clearX: Int,
    val clearY: Int,
    val clearWidth: Int,
    val clearHeight: Int,
)

internal class UiLayerFramebuffer(
    val atlas: UiFramebuffer,
    val region: UiAtlasRegion,
) {
    val framebuffer: Int get() = atlas.framebuffer
    val texture: Int get() = atlas.texture
    val width: Int get() = region.width
    val height: Int get() = region.height

    fun bind() {
        atlas.bind()
        GL11.glViewport(region.x, region.y, region.width, region.height)
    }

    fun clear() {
        atlas.bind()
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(region.clearX, region.clearY, region.clearWidth, region.clearHeight)
        GL11.glClearColor(0f, 0f, 0f, 0f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)
        disableScissor()
    }

    fun u0(): Float = region.x / atlas.width.toFloat()

    fun v0(): Float = region.y / atlas.height.toFloat()

    fun u1(): Float = (region.x + region.width) / atlas.width.toFloat()

    fun v1(): Float = (region.y + region.height) / atlas.height.toFloat()
}

internal class UiFramebuffer(
    val width: Int,
    val height: Int,
) {
    val framebuffer: Int = GL30.glGenFramebuffers()
    val texture: Int = GL11.glGenTextures()
    private val depth: Int = GL30.glGenRenderbuffers()
    var inUse: Boolean = false

    init {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L)
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0)
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depth)
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, width, height)
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depth)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer)
        GL11.glViewport(0, 0, width, height)
    }

    fun close() {
        GL30.glDeleteFramebuffers(framebuffer)
        GL11.glDeleteTextures(texture)
        GL30.glDeleteRenderbuffers(depth)
    }
}

private class UiAtlasAllocator(width: Int, height: Int) {
    private val freeRects = mutableListOf(UiPackedRect(0, 0, width, height))

    fun allocate(width: Int, height: Int): UiPackedRect? {
        val index = freeRects.indices
            .filter { freeRects[it].fits(width, height) }
            .minByOrNull { freeRects[it].waste(width, height) }
            ?: return null
        val free = freeRects.removeAt(index)
        val allocated = UiPackedRect(free.x, free.y, width, height)
        val rightWidth = free.width - width
        val bottomHeight = free.height - height
        if (rightWidth > 0) freeRects += UiPackedRect(free.x + width, free.y, rightWidth, height)
        if (bottomHeight > 0) freeRects += UiPackedRect(free.x, free.y + height, free.width, bottomHeight)
        pruneContainedRects()
        return allocated
    }

    private fun pruneContainedRects() {
        var index = 0
        while (index < freeRects.size) {
            val rect = freeRects[index]
            val contained = freeRects.indices.any { other ->
                other != index && freeRects[other].contains(rect)
            }
            if (contained) {
                freeRects.removeAt(index)
            } else {
                index++
            }
        }
    }
}

private data class UiPackedRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    fun fits(width: Int, height: Int): Boolean = this.width >= width && this.height >= height

    fun waste(width: Int, height: Int): Int = this.width * this.height - width * height

    fun contains(other: UiPackedRect): Boolean =
        other.x >= x &&
                other.y >= y &&
                other.x + other.width <= x + width &&
                other.y + other.height <= y + height
}

private data class UiAtlasSize(
    val width: Int,
    val height: Int,
)

private fun reserveAtlasAxis(value: Int, maxTextureSize: Int): Int =
    min(max(value * UiAtlasGrowthMultiplier, value + UiAtlasGrowthReservePixels), maxTextureSize)

private const val UiAtlasGuardPixels = 2
private const val UiAtlasGrowthMultiplier = 2
private const val UiAtlasGrowthReservePixels = 512
private const val RetiredAtlasFrames = 3
