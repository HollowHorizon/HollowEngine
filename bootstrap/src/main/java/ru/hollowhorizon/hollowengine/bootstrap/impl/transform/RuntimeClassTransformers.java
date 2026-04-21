package ru.hollowhorizon.hollowengine.bootstrap.impl.transform;

import java.util.List;

public final class RuntimeClassTransformers {
    private RuntimeClassTransformers() {
    }

    public static List<RuntimeClassTransformer> createDefault() {
        return List.of(
                new KoolClipboardTransformer(),
                new KoolDockNodeBridgeTransformer(),
                new KoolDockNodeBehaviorTransformer(),
                new KoolDragAndDropContextBridgeTransformer(),
                new KoolUiDockableBridgeTransformer(),
                new KoolUiNodeBridgeTransformer(),
                new KoolPlatformInputTransformer(),
                new KoolLeafSlotsTransformer()
        );
    }
}
