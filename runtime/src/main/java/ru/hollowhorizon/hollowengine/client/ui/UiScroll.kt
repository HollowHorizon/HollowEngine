package ru.hollowhorizon.hollowengine.client.ui

data class UiScrollOffset(
    val x: Float = 0f,
    val y: Float = 0f,
) {
    companion object {
        val Zero = UiScrollOffset()
    }
}

class UiScrollState {
    private val offsets = mutableMapOf<String, UiScrollOffset>()
    private val targets = mutableMapOf<String, UiScrollOffset>()
    private val starts = mutableMapOf<String, UiScrollOffset>()
    private val startedAt = mutableMapOf<String, Long>()
    private val ranges = mutableMapOf<String, UiScrollOffset>()
    private val durationMillis = 190L

    fun offset(node: UiNode): UiScrollOffset = offsets[UiNodeKeys.key(node)] ?: UiScrollOffset.Zero

    fun range(node: UiNode): UiScrollOffset = ranges[UiNodeKeys.key(node)] ?: UiScrollOffset.Zero

    fun scroll(node: UiNode, deltaX: Float, deltaY: Float): UiScrollOffset {
        val key = UiNodeKeys.key(node)
        val current = targets[key] ?: offset(node)
        val range = range(node)
        val next = UiScrollOffset(
            x = (current.x + deltaX).coerceIn(0f, range.x),
            y = (current.y + deltaY).coerceIn(0f, range.y),
        )
        animateTo(key, next)
        return next
    }

    fun set(node: UiNode, x: Float? = null, y: Float? = null): UiScrollOffset {
        val key = UiNodeKeys.key(node)
        val current = targets[key] ?: offset(node)
        val range = range(node)
        val next = UiScrollOffset(
            x = (x ?: current.x).coerceIn(0f, range.x),
            y = (y ?: current.y).coerceIn(0f, range.y),
        )
        animateTo(key, next)
        return next
    }

    fun setImmediate(node: UiNode, x: Float? = null, y: Float? = null): UiScrollOffset {
        val key = UiNodeKeys.key(node)
        val current = targets[key] ?: offset(node)
        val range = range(node)
        val next = UiScrollOffset(
            x = (x ?: current.x).coerceIn(0f, range.x),
            y = (y ?: current.y).coerceIn(0f, range.y),
        )
        offsets[key] = next
        targets[key] = next
        starts.remove(key)
        startedAt.remove(key)
        return next
    }

    fun clamp(node: UiNode, range: UiScrollOffset): UiScrollOffset {
        val key = UiNodeKeys.key(node)
        ranges[key] = range
        val current = offset(node)
        val clamped = UiScrollOffset(current.x.coerceIn(0f, range.x), current.y.coerceIn(0f, range.y))
        offsets[key] = clamped
        targets[key] = (targets[key] ?: clamped).let { UiScrollOffset(it.x.coerceIn(0f, range.x), it.y.coerceIn(0f, range.y)) }
        return clamped
    }

    fun update(nowMillis: Long) {
        for ((key, target) in targets.toMap()) {
            val startTime = startedAt[key] ?: continue
            val start = starts[key] ?: offsets[key] ?: UiScrollOffset.Zero
            val progress = ((nowMillis - startTime).toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
            val eased = 1f - (1f - progress) * (1f - progress)
            val next = UiScrollOffset(
                x = start.x + (target.x - start.x) * eased,
                y = start.y + (target.y - start.y) * eased,
            )
            offsets[key] = next
            if (progress >= 1f) {
                offsets[key] = target
                starts.remove(key)
                startedAt.remove(key)
            }
        }
    }

    private fun animateTo(key: String, next: UiScrollOffset) {
        starts[key] = offsets[key] ?: UiScrollOffset.Zero
        targets[key] = next
        startedAt[key] = System.currentTimeMillis()
    }
}
