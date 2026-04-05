package ru.hollowhorizon.hollowengine.runtime.transform.kool;

import de.fabmax.kool.math.MutableVec4f;
import de.fabmax.kool.modules.ui2.PointerEvent;

public interface UiDockableAccessor {
    float hollowcore$getFloatingWidthPx();

    float hollowcore$getFloatingHeightPx();

    MutableVec4f hollowcore$getDragStartItemBounds();

    void hollowcore$setDragStartItemBounds(MutableVec4f bounds);

    void hollowcore$moveUndockBoundsUnderPointer(MutableVec4f itemBounds, PointerEvent ptrEv);
}
