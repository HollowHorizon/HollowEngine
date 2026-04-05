package ru.hollowhorizon.hollowengine.bootstrap.runtime.transform;

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
