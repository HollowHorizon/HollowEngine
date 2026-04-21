package ru.hollowhorizon.hollowengine.bootstrap.impl.transform;

import org.objectweb.asm.tree.ClassNode;

final class KoolUiDockableBridgeTransformer extends AbstractAsmClassTransformer {
    private static final String BRIDGE = "ru/hollowhorizon/hollowengine/runtime/transform/kool/UiDockableAccessor";

    KoolUiDockableBridgeTransformer() {
        super("de.fabmax.kool.modules.ui2.docking.UiDockable");
    }

    @Override
    protected void transform(ClassNode classNode) {
        addInterface(classNode, BRIDGE);
        addGetterBridge(classNode, "hollowcore$getFloatingWidthPx", "()F", "floatingWidthPx", "F");
        addGetterBridge(classNode, "hollowcore$getFloatingHeightPx", "()F", "floatingHeightPx", "F");
        addGetterBridge(
                classNode,
                "hollowcore$getDragStartItemBounds",
                "()Lde/fabmax/kool/math/MutableVec4f;",
                "dragStartItemBounds",
                "Lde/fabmax/kool/math/MutableVec4f;"
        );
        addSetterBridge(
                classNode,
                "hollowcore$setDragStartItemBounds",
                "(Lde/fabmax/kool/math/MutableVec4f;)V",
                "dragStartItemBounds",
                "Lde/fabmax/kool/math/MutableVec4f;"
        );
        addInvokerBridge(
                classNode,
                "hollowcore$moveUndockBoundsUnderPointer",
                "(Lde/fabmax/kool/math/MutableVec4f;Lde/fabmax/kool/modules/ui2/PointerEvent;)V",
                "moveUndockBoundsUnderPointer",
                "(Lde/fabmax/kool/math/MutableVec4f;Lde/fabmax/kool/modules/ui2/PointerEvent;)V"
        );
    }
}
