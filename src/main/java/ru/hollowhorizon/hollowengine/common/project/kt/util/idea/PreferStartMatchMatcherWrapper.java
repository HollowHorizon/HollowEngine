package ru.hollowhorizon.hollowengine.common.project.kt.util.idea;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange;
import org.jetbrains.kotlin.com.intellij.util.containers.FList;
import ru.hollowhorizon.hc.common.utils.JavaHacks;
import ru.hollowhorizon.hollowengine.mixins.scripting.FListAccessor;

public final class PreferStartMatchMatcherWrapper extends MinusculeMatcher {
    public static final int START_MATCH_WEIGHT = 10000;
    private final @NotNull MinusculeMatcher myDelegateMatcher;

    public PreferStartMatchMatcherWrapper(@NotNull MinusculeMatcher matcher) {
        myDelegateMatcher = matcher;
    }

    @Override
    public @NotNull String getPattern() {
        return myDelegateMatcher.getPattern();
    }

    @Override
    public FList<TextRange> matchingFragments(@NotNull String name) {
        return myDelegateMatcher.matchingFragments(name);
    }

    @Override
    public int matchingDegree(@NotNull String name,
                              boolean valueStartCaseMatch,
                              @Nullable FList<? extends TextRange> fragments) {
        int degree = myDelegateMatcher.matchingDegree(name, valueStartCaseMatch, fragments);
        if (fragments == null || fragments.isEmpty()) return degree;

        FListAccessor accessor = JavaHacks.forceCast(fragments);
        if (((TextRange)accessor.head()).getStartOffset() == 0) degree += START_MATCH_WEIGHT;
        return degree;
    }
}
