package ru.hollowhorizon.hollowengine.common.slots

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** How a zone takes part in the quick-move ring. */
enum class SlotZoneRole {
    /** Items both leave and arrive here; the default for a plain inventory. */
    BOTH,

    /** Items only leave: a result slot a player takes from but cannot shift-click into. */
    SOURCE,

    /** Items only arrive. */
    TARGET,

    /** Never involved in quick-move; only direct clicks touch it. */
    NONE,
    ;

    val canSend: Boolean get() = this == BOTH || this == SOURCE
    val canReceive: Boolean get() = this == BOTH || this == TARGET
}

/** What a single slot allows. Zones supply the defaults; individual slots may override them. */
@Serializable
class SlotRules(
    val canInsert: SlotFilter = SlotFilter.Any,
    val canExtract: SlotFilter = SlotFilter.Any,
    val stackLimit: Int = DEFAULT_STACK_LIMIT,
) {
    val isPredictable: Boolean get() = canInsert.isPredictable && canExtract.isPredictable

    internal fun differsFrom(other: SlotRules): Boolean =
        stackLimit != other.stackLimit || canInsert !== other.canInsert || canExtract !== other.canExtract
}

/** Both filters must pass. Collapses when either side accepts everything, keeping layouts small. */
internal infix fun SlotFilter.and(other: SlotFilter): SlotFilter = when {
    this === other -> this
    kind == SlotFilterKind.ANY -> other
    other.kind == SlotFilterKind.ANY -> this
    else -> SlotFilter.allOf(this, other)
}

/** A per-slot override of its zone's rules, addressed by index within the zone. */
@Serializable
class SlotRuleOverride(val index: Int, val rules: SlotRules)

/**
 * One zone's place in the flat slot space.
 *
 * Zones are named because that is what a screen refers to (`SlotGrid("player")`), and offset-based
 * because the wire and the click logic want a single integer per slot. Both sides derive the mapping
 * from this same declaration, so a client can only ever address slots the server actually opened.
 */
@Serializable
class SlotZoneLayout(
    val name: String,
    val offset: Int,
    val size: Int,
    val role: SlotZoneRole = SlotZoneRole.BOTH,
    val rules: SlotRules = SlotRules(),
    val overrides: List<SlotRuleOverride> = emptyList(),
    /**
     * False when a server-only rule or a cancelling handler governs this zone. It travels so the
     * client knows its prediction would be a guess.
     */
    val predictable: Boolean = true,
    /**
     * An editing zone: a click writes a copy of the carried stack into the slot and leaves the cursor
     * holding it, and an empty-handed click clears the slot instead of picking it up.
     */
    val copyOnClick: Boolean = false,
) {
    val flatIndices: IntRange get() = offset until offset + size

    fun rulesAt(local: Int): SlotRules =
        overrides.firstOrNull { it.index == local }?.rules ?: rules

    fun contains(flat: Int): Boolean = flat in flatIndices
}

/**
 * The full slot structure of one open UI: which zones exist, what each slot allows, and how quick-move
 * walks between them.
 *
 * Built on the server when the UI opens and sent to the client as-is. Nothing here refers to a world
 * object, which is what lets the click logic be a pure function of layout plus state, and therefore run
 * identically on both sides and be unit-testable on its own.
 */
@Serializable
class SlotLayout(
    val zones: List<SlotZoneLayout> = emptyList(),
    /** Zone names in declaration order; quick-move walks this cyclically. */
    val ring: List<String> = emptyList(),
    /** Zone name -> forced first quick-move target, overriding the ring for that zone. */
    val overrides: Map<String, String> = emptyMap(),
) {
    // Derived, not carried: marked transient so the wire format stays exactly the three declared fields
    // and these are recomputed from them on the receiving side.
    @Transient
    val totalSize: Int = zones.sumOf { it.size }

    /** False when any zone is unpredictable; the client then never applies a click optimistically. */
    @Transient
    val isPredictable: Boolean = zones.all { it.predictable && it.rules.isPredictable }

    fun zone(name: String): SlotZoneLayout? = zones.firstOrNull { it.name == name }

    fun zoneOf(flat: Int): SlotZoneLayout? = zones.firstOrNull { it.contains(flat) }

    fun rulesAt(flat: Int): SlotRules {
        val zone = zoneOf(flat) ?: return EmptyRules
        return zone.rulesAt(flat - zone.offset)
    }

    /** Whether this slot edits by copying rather than by moving; see [SlotZoneLayout.copyOnClick]. */
    fun copiesAt(flat: Int): Boolean = zoneOf(flat)?.copyOnClick ?: false

    /** Flat index of [local] within [name], or -1 when the zone or index does not exist. */
    fun flatIndex(name: String, local: Int): Int {
        val zone = zone(name) ?: return -1
        if (local !in 0 until zone.size) return -1
        return zone.offset + local
    }

    /**
     * Zones a quick-move out of [from] may target, nearest first: the ring continues after [from] and
     * wraps around, with [overrides] promoting one zone to the front.
     */
    fun quickMoveTargets(from: String): List<SlotZoneLayout> {
        val start = ring.indexOf(from)
        if (start < 0) return emptyList()
        val ordered = ArrayList<SlotZoneLayout>(ring.size)
        // A zone is never its own target: merging a stack into the zone it came from and then writing the
        // leftover back over the source slot would destroy the items that just moved.
        overrides[from]?.takeIf { it != from }?.let { forced -> zone(forced)?.let(ordered::add) }
        for (step in 1..ring.size) {
            val candidate = zone(ring[(start + step) % ring.size]) ?: continue
            if (candidate.name == from || ordered.any { it.name == candidate.name }) continue
            ordered += candidate
        }
        return ordered.filter { it.role.canReceive }
    }

    private companion object {
        val EmptyRules = SlotRules(canInsert = SlotFilter.None, canExtract = SlotFilter.None)
    }
}

/** Vanilla's cap on a container slot; the default any source without a tighter limit reports. */
const val DEFAULT_STACK_LIMIT = 64
