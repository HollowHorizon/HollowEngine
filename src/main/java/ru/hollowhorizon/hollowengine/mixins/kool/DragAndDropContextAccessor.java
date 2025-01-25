package ru.hollowhorizon.hollowengine.mixins.kool;

import de.fabmax.kool.modules.ui2.DragAndDropContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DragAndDropContext.class, remap = false)
public interface DragAndDropContextAccessor {
    @Accessor("dragItem")
    Object getDragItem();
}
