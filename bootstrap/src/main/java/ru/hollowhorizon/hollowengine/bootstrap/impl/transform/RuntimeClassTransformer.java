package ru.hollowhorizon.hollowengine.bootstrap.impl.transform;

public interface RuntimeClassTransformer {
    boolean supports(String className);

    byte[] transform(String className, byte[] originalBytes);
}
