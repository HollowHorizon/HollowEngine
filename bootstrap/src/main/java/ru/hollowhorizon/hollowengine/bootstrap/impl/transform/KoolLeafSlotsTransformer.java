package ru.hollowhorizon.hollowengine.bootstrap.impl.transform;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

final class KoolLeafSlotsTransformer extends AbstractAsmClassTransformer {
    KoolLeafSlotsTransformer() {
        super("de.fabmax.kool.modules.ui2.docking.DockNodeLeaf$LeafSlots");
    }

    @Override
    protected void transform(ClassNode classNode) {
        MethodNode objectCompose = findMethod(classNode, "compose", "(Lde/fabmax/kool/modules/ui2/UiScope;)Ljava/lang/Object;");
        if (objectCompose != null) {
            replaceWithNullReturn(objectCompose);
        }

        MethodNode rowCompose = findMethod(classNode, "compose", "(Lde/fabmax/kool/modules/ui2/UiScope;)Lde/fabmax/kool/modules/ui2/RowScope;");
        if (rowCompose != null) {
            replaceWithNullReturn(rowCompose);
        }
    }
}
