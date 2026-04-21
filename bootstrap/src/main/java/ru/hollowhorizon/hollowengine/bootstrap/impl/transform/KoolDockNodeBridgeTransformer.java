package ru.hollowhorizon.hollowengine.bootstrap.impl.transform;

import org.objectweb.asm.tree.ClassNode;

final class KoolDockNodeBridgeTransformer extends AbstractAsmClassTransformer {
    private static final String BRIDGE = "ru/hollowhorizon/hollowengine/runtime/transform/kool/DockNodeInvoker";

    KoolDockNodeBridgeTransformer() {
        super("de.fabmax.kool.modules.ui2.docking.DockNode");
    }

    @Override
    protected void transform(ClassNode classNode) {
        addInterface(classNode, BRIDGE);
        addInvokerBridge(
                classNode,
                "callInsertItem",
                "(Lde/fabmax/kool/modules/ui2/docking/Dockable;Lde/fabmax/kool/modules/ui2/docking/DockNode$SlotPosition;)V",
                "insertItem",
                "(Lde/fabmax/kool/modules/ui2/docking/Dockable;Lde/fabmax/kool/modules/ui2/docking/DockNode$SlotPosition;)V"
        );
    }
}
