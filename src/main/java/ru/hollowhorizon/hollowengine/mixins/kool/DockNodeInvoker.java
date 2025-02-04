package ru.hollowhorizon.hollowengine.mixins.kool;

import de.fabmax.kool.modules.ui2.docking.DockNode;
import de.fabmax.kool.modules.ui2.docking.Dockable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = DockNode.class, remap = false)
public interface DockNodeInvoker {
    @Invoker("insertItem")
    void callInsertItem(Dockable dockable, DockNode.SlotPosition slotPosition);
}
