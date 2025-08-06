package ru.hollowhorizon.hollowengine.mixins.kool;

import de.fabmax.kool.math.MutableVec4f;
import de.fabmax.kool.modules.ui2.PointerEvent;
import de.fabmax.kool.modules.ui2.docking.UiDockable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(UiDockable.class)
public interface UiDockableAccessor {
    @Accessor("floatingWidthPx")
    float hollowcore$getFloatingWidthPx();

    @Accessor("floatingHeightPx")
    float hollowcore$getFloatingHeightPx();

    @Accessor("dragStartItemBounds")
    MutableVec4f hollowcore$getDragStartItemBounds();
    @Accessor("dragStartItemBounds")
    void hollowcore$setDragStartItemBounds(MutableVec4f bounds);

    @Invoker("moveUndockBoundsUnderPointer")
    void hollowcore$moveUndockBoundsUnderPointer(MutableVec4f itemBounds, PointerEvent ptrEv);
}
