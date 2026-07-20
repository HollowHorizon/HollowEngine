package ru.hollowhorizon.hollowengine.bootstrap.impl.transform;

import java.util.List;

public final class RuntimeClassTransformers {
    private RuntimeClassTransformers() {
    }

    public static List<RuntimeClassTransformer> createDefault() {
        return List.of();
    }
}
