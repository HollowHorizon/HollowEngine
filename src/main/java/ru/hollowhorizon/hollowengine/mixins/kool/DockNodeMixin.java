package ru.hollowhorizon.hollowengine.mixins.kool;

import de.fabmax.kool.modules.ui2.DragAndDropHandler;
import de.fabmax.kool.modules.ui2.PointerEvent;
import de.fabmax.kool.modules.ui2.UiScope;
import de.fabmax.kool.modules.ui2.docking.DockNode;
import de.fabmax.kool.modules.ui2.docking.DockNodeLeaf;
import de.fabmax.kool.modules.ui2.docking.Dockable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent;
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader;

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
        if (!(sourceNode instanceof DockNodeLeaf leaf)) return;

        var isFile = IdeContent.INSTANCE.getFiles().values().stream().anyMatch((file) -> file.getDockable() == dragItem);
        if (isFile && leaf.getDockedItems().stream().anyMatch(LayoutLoader::contains)) {
            cir.setReturnValue(false);
            return;
        }
        var isPanel = LayoutLoader.INSTANCE.getLAYOUTS().values().stream().anyMatch(panel -> panel.getDockable() == dragItem);
        if (isPanel && leaf.getDockedItems().stream().anyMatch(IdeContent::contains)) {
            cir.setReturnValue(false);
        }
    }
}
