package ru.hollowhorizon.hollowengine.bootstrap.runtime;

public final class AddonVersions {
    private AddonVersions() {
    }

    public static int compare(String left, String right) {
        String[] leftParts = split(left);
        String[] rightParts = split(right);
        int length = Math.max(leftParts.length, rightParts.length);

        for (int index = 0; index < length; index++) {
            String leftPart = index < leftParts.length ? leftParts[index] : "0";
            String rightPart = index < rightParts.length ? rightParts[index] : "0";
            int result = compareSegment(leftPart, rightPart);
            if (result != 0) return result;
        }

        return 0;
    }

    private static int compareSegment(String left, String right) {
        Long leftNumber = number(left);
        Long rightNumber = number(right);
        if (leftNumber != null && rightNumber != null) return Long.compare(leftNumber, rightNumber);
        if (leftNumber != null) return 1;
        if (rightNumber != null) return -1;
        return left.compareToIgnoreCase(right);
    }

    private static String[] split(String version) {
        String trimmed = version == null ? "" : version.trim();
        if (trimmed.isEmpty()) return new String[]{"0"};
        return trimmed.split("[._\\-+]");
    }

    private static Long number(String segment) {
        if (segment.isEmpty()) return null;
        for (int index = 0; index < segment.length(); index++) {
            if (!Character.isDigit(segment.charAt(index))) return null;
        }
        try {
            return Long.parseLong(segment);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
