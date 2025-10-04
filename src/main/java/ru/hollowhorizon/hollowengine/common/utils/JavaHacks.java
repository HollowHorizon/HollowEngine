package ru.hollowhorizon.hollowengine.common.utils;

/**
 * Utility class for performing unsafe type casts.
 */
public class JavaHacks {
    /**
     * Forces a type cast from one type to another.
     *
     * @param original The original object.
     * @param <R> The original type.
     * @param <K> The target type.
     * @return The object cast to the target type.
     * @throws ClassCastException if the cast is invalid at runtime.
     */
    @SuppressWarnings("unchecked")
    public static <R, K> K forceCast(R original) {
        return (K) original;
    }
}
