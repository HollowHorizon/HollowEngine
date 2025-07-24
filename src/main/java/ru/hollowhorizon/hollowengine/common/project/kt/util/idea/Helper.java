package ru.hollowhorizon.hollowengine.common.project.kt.util.idea;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange;
import org.jetbrains.kotlin.com.intellij.util.containers.FList;
import ru.hollowhorizon.hc.common.utils.JavaHacks;
import ru.hollowhorizon.hollowengine.mixins.scripting.FListAccessor;

public class Helper {
    public static <E> FList<E> singleton(@NotNull E elem) {
        return FList.<E>emptyList().prepend(elem);
    }

    public static <E> E head(FList<? extends TextRange> fragments) {
        FListAccessor accessor = JavaHacks.forceCast(fragments);
        return (E) accessor.head();
    }
}
