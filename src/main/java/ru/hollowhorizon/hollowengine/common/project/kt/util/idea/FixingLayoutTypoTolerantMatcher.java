package ru.hollowhorizon.hollowengine.common.project.kt.util.idea;

final class FixingLayoutTypoTolerantMatcher {
    static MinusculeMatcher create(String pattern, NameUtil.MatchingCaseSensitivity options, String hardSeparators) {
        TypoTolerantMatcher mainMatcher = new TypoTolerantMatcher(pattern, options, hardSeparators);
        String s = FixingLayoutMatcher.fixLayout(pattern);

        if (s != null && !s.equals(pattern)) {
            TypoTolerantMatcher fallbackMatcher = new TypoTolerantMatcher(s, options, hardSeparators);
            return new MatcherWithFallback(mainMatcher, fallbackMatcher);
        } else {
            return mainMatcher;
        }
    }
}