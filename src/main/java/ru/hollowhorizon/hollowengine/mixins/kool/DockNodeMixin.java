package ru.hollowhorizon.hollowengine.mixins.kool;

import de.fabmax.kool.modules.ui2.DragAndDropHandler;
import de.fabmax.kool.modules.ui2.PointerEvent;
import de.fabmax.kool.modules.ui2.docking.DockNode;
import de.fabmax.kool.modules.ui2.docking.Dockable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.client.gui.kool.DockLock;

@Mixin(value = DockNode.class, remap = false)
public class DockNodeMixin {

    @Inject(
            method = "receive(Lde/fabmax/kool/modules/ui2/docking/Dockable;Lde/fabmax/kool/modules/ui2/PointerEvent;Lde/fabmax/kool/modules/ui2/DragAndDropHandler;)Z",
            at = @At(value = "INVOKE", target = "Lde/fabmax/kool/modules/ui2/docking/DockNode;insertItem(Lde/fabmax/kool/modules/ui2/docking/Dockable;Lde/fabmax/kool/modules/ui2/docking/DockNode$SlotPosition;)V"),
            remap = false,
            cancellable = true
    )
    private void canInsert(Dockable dragItem, PointerEvent dragPointer, DragAndDropHandler<Dockable> source, CallbackInfoReturnable<Boolean> cir) {
        var sourceNode = (DockNode) (Object) this;
        if (sourceNode instanceof DockLock dockLock) {
            if (!dockLock.canInsert(dragItem, dragPointer)) {
                cir.setReturnValue(false);
                cir.cancel();
            }
        }
    }
}
