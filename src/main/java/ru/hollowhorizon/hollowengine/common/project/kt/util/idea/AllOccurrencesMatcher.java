package ru.hollowhorizon.hollowengine.common.project.kt.util.idea;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange;
import org.jetbrains.kotlin.com.intellij.util.containers.FList;

import java.util.ArrayList;
import java.util.List;

public final class AllOccurrencesMatcher extends MinusculeMatcher {
    private final MinusculeMatcher delegate;

    private AllOccurrencesMatcher(@NotNull String pattern,
                                  @NotNull NameUtil.MatchingCaseSensitivity options,
                                  String hardSeparators) {
        delegate = new FixingLayoutMatcher(pattern, options, hardSeparators);
    }

    @Override
    public @NotNull String getPattern() {
        return delegate.getPattern();
    }

    @Override
    public int matchingDegree(@NotNull String name, boolean valueStartCaseMatch, @Nullable FList<? extends TextRange> fragments) {
        return delegate.matchingDegree(name, valueStartCaseMatch, fragments);
    }

    @Override
    public @Nullable FList<TextRange> matchingFragments(@NotNull String name) {
        FList<TextRange> match = delegate.matchingFragments(name);
        if (match != null && !match.isEmpty()) {
            List<FList<TextRange>> allMatchesReversed = new ArrayList<>();
            int lastOffset = 0;
            while (match != null && !match.isEmpty()) {
                FList<TextRange> reversedWithAbsoluteOffsets = FList.emptyList();
                for (TextRange r : match) {
                    reversedWithAbsoluteOffsets = reversedWithAbsoluteOffsets.prepend(r.shiftRight(lastOffset));
                }
                allMatchesReversed.add(reversedWithAbsoluteOffsets);
                lastOffset = reversedWithAbsoluteOffsets.get(0).getEndOffset();
                match = delegate.matchingFragments(name.substring(lastOffset));
            }
            match = FList.emptyList();
            for (int i = allMatchesReversed.size() - 1; i >= 0; i--) {
                for (TextRange range : allMatchesReversed.get(i)) {
                    match = match.prepend(range);
                }
            }
        }
        return match;
    }

    @Override
    public String toString() {
        return "AllOccurrencesMatcher{" +
                "delegate=" + delegate +
                '}';
    }

    public static MinusculeMatcher create(@NotNull String pattern, @NotNull NameUtil.MatchingCaseSensitivity options, String hardSeparators) {
        return new AllOccurrencesMatcher(pattern, options, hardSeparators);
    }
}
