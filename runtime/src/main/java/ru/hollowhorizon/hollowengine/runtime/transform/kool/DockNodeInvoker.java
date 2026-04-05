package ru.hollowhorizon.hollowengine.runtime.transform.kool;

import de.fabmax.kool.modules.ui2.docking.DockNode;
import de.fabmax.kool.modules.ui2.docking.Dockable;

public interface DockNodeInvoker {
    void callInsertItem(Dockable dockable, DockNode.SlotPosition slotPosition);
}
