package de.tuefteltyp.infinitechest;

import java.util.Locale;

/** Pure, deterministic input validation shared by the registry and tests. */
public final class NameRules {
    private NameRules() { }

    public static String normalize(String requested) {
        if (requested == null) return "";
        String result = requested.strip().replaceAll("\\s+", " ");
        if (result.isBlank() || result.length() > 24) return "";
        for (int i = 0; i < result.length(); i++) {
            char ch = result.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != ' ' && ch != '-' && ch != '_') return "";
        }
        return result;
    }

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
