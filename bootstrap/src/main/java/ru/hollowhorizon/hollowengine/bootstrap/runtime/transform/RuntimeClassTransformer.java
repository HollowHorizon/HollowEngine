package ru.hollowhorizon.hollowengine.bootstrap.runtime.transform;

public interface RuntimeClassTransformer {
    boolean supports(String className);

    byte[] transform(String className, byte[] originalBytes);
}
