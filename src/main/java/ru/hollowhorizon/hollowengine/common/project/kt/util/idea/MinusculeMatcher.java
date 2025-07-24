package ru.hollowhorizon.hollowengine.common.project.kt.util.idea;

import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange;
import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.kotlin.com.intellij.util.containers.FList;

import java.util.Iterator;

public abstract class MinusculeMatcher implements Matcher {

    protected MinusculeMatcher() {}

    public abstract String getPattern();

    @Override
    public boolean matches(String name) {
        return matchingFragments(name) != null;
    }

    public FList<TextRange> matchingFragments(String name) {
        throw new UnsupportedOperationException();
    }

    public int matchingDegree(String name, boolean valueStartCaseMatch, FList<? extends TextRange> fragments) {
        throw new UnsupportedOperationException();
    }

    public int matchingDegree(String name, boolean valueStartCaseMatch) {
        return matchingDegree(name, valueStartCaseMatch, matchingFragments(name));
    }

    public int matchingDegree(String name) {
        return matchingDegree(name, false);
    }

    public boolean isStartMatch(String name) {
        FList<TextRange> fragments = matchingFragments(name);
        return fragments != null && isStartMatch(fragments);
    }

    public static boolean isStartMatch(Iterable<? extends TextRange> fragments) {
        Iterator<? extends TextRange> iterator = fragments.iterator();
        return !iterator.hasNext() || iterator.next().getStartOffset() == 0;
    }
}