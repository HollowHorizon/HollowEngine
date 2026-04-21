package ru.hollowhorizon.hollowengine.bootstrap.impl.transform;

import org.objectweb.asm.tree.ClassNode;

final class KoolPlatformInputTransformer extends AbstractAsmClassTransformer {
    private static final String HOOK_OWNER = "ru/hollowhorizon/hollowengine/runtime/transform/KoolRuntimeHooks";

    KoolPlatformInputTransformer() {
        super("de.fabmax.kool.input.PlatformInput_desktopKt");
    }

    @Override
    protected void transform(ClassNode classNode) {
        replaceWithStaticCall(
                requireMethod(classNode, "PlatformInput", "()Lde/fabmax/kool/input/PlatformInput;"),
                HOOK_OWNER,
                "platformInput",
                "()Lde/fabmax/kool/input/PlatformInput;"
        );
    }
}
