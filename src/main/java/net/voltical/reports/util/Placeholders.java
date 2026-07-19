package net.voltical.reports.util;

/** Tiny {key}->value replacement helper. */
public final class Placeholders {

    private Placeholders() {}

    public static String apply(String input, String... keyValuePairs) {
        if (input == null) return "";
        String out = input;
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            out = out.replace(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return out;
    }
}
