package ru.hollowhorizon.hollowengine.client.ui.graph

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.hollowhorizon.hollowengine.common.config.HollowEngineConfig

/**
 * How the author likes graphs to behave, the same in every graph editor and kept in the config: it is
 * a matter of taste, not part of the file.
 */
object GraphPreferences {
    private var snapping by mutableStateOf(HollowEngineConfig.graphSnapToGrid)

    /** Whether dragged nodes land on the grid, on every half of a [GRID_STEP]. */
    var snapToGrid: Boolean
        get() = snapping
        set(value) {
            snapping = value
            HollowEngineConfig.graphSnapToGrid = value
        }

    /** Where a node dragged to [value] lands, on either axis. */
    fun place(value: Float): Float = if (snapToGrid) GraphArrange.snap(value, SNAP_STEP) else value

    const val SNAP_STEP = GRID_STEP / 2f
}
