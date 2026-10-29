package holorecorder.update;

import java.util.regex.Pattern;

/** Mod version numbers: only the dotted numbers before any "+" or "-" count, so "1.13.0+mc26.2" is "1.13.0". */
public final class UpdateVersion {
    private static final Pattern NUMBERS = Pattern.compile("\\d{1,9}(\\.\\d{1,9}){0,3}");

    private UpdateVersion() {
    }

    /** The numeric parts, or null when the text is not a version. */
    static int[] parse(String version) {
        if (version == null) return null;
        String base = base(version);
        if (!NUMBERS.matcher(base).matches()) return null;
        String[] parts = base.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i]);
        return out;
    }

    /** The text before the first "+" or "-". */
    public static String base(String version) {
        int cut = version.length();
        for (char c : new char[] {'+', '-'}) {
            int i = version.indexOf(c);
            if (i >= 0 && i < cut) cut = i;
        }
        return version.substring(0, cut).strip();
    }

    /** Negative, zero or positive like compareTo; null when either side is not a version. */
    static Integer compare(String a, String b) {
        int[] x = parse(a), y = parse(b);
        if (x == null || y == null) return null;
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    /** True only when both are versions and {@code candidate} is higher than {@code running}. */
    public static boolean isNewer(String candidate, String running) {
        Integer c = compare(candidate, running);
        return c != null && c > 0;
    }
}
