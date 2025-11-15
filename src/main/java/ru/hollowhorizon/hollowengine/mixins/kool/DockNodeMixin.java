package ru.hollowhorizon.hollowengine.mixins.kool;

import de.fabmax.kool.modules.ui2.DragAndDropHandler;
import de.fabmax.kool.modules.ui2.PointerEvent;
import de.fabmax.kool.modules.ui2.UiScope;
import de.fabmax.kool.modules.ui2.docking.DockNode;
import de.fabmax.kool.modules.ui2.docking.DockNodeLeaf;
import de.fabmax.kool.modules.ui2.docking.Dockable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent;
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader;

@Mixin(value = DockNode.class, remap = false)
public abstract class DockNodeMixin {

    @Shadow protected abstract void insertItem(@NotNull Dockable dockable, @NotNull DockNode.SlotPosition slotPosition);

    @Redirect(
            method = "receive(Lde/fabmax/kool/modules/ui2/docking/Dockable;Lde/fabmax/kool/modules/ui2/PointerEvent;Lde/fabmax/kool/modules/ui2/DragAndDropHandler;)Z",
            at = @At(value = "INVOKE", target = "Lde/fabmax/kool/modules/ui2/docking/DockNode;insertItem(Lde/fabmax/kool/modules/ui2/docking/Dockable;Lde/fabmax/kool/modules/ui2/docking/DockNode$SlotPosition;)V"),
            remap = false
    )
    private void canInsert(DockNode instance, Dockable dockable, DockNode.SlotPosition slotPosition) {
        var sourceNode = (DockNode) (Object) this;
        if (!(sourceNode instanceof DockNodeLeaf leaf)) {
            insertItem(dockable, slotPosition);
            return;
        }

        var isFile = IdeContent.INSTANCE.getFiles().values().stream().anyMatch((file) -> file.getDockable() == dockable);
        if (isFile && leaf.getDockedItems().stream().anyMatch(LayoutLoader::contains)) {
            if(slotPosition == DockNode.SlotPosition.Center) return;
        }
        var isPanel = LayoutLoader.INSTANCE.getLAYOUTS().values().stream().anyMatch(panel -> panel.getDockable() == dockable);
        if (isPanel && leaf.getDockedItems().stream().anyMatch(IdeContent::contains)) {
            if(slotPosition == DockNode.SlotPosition.Center) return;
        }

        insertItem(dockable, slotPosition);
    }

    @Inject(method = "dockPreview", at = @At(value = "INVOKE", target = "Lde/fabmax/kool/modules/ui2/docking/DockNode;dockPreviewBox(Lde/fabmax/kool/modules/ui2/UiScope;Z)Lde/fabmax/kool/modules/ui2/UiScope;", ordinal = 0), cancellable = true)
    private void onDockPreview(UiScope $this$dockPreview, DockNode.SlotPosition previewPos, CallbackInfo ci) {
        ci.cancel();
    }
}
