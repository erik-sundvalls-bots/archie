package com.nedap.archie.adl14.down;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Map ADL2 id/ac-style codes toward ADL 1.4 at-codes for down-conversion.
 * Comparable in spirit to Ehrtslib {@code ADL14Serializer.convertNodeIdsToAtCodes}.
 */
public final class IdToAtCodeMapper {

    private static final Pattern ID_OR_AC = Pattern.compile("^(id|ac)(\\d+(?:\\.\\d+)*)$");

    private IdToAtCodeMapper() {}

    /**
     * Convert a single code. Non-id/ac codes are returned unchanged.
     * <ul>
     *   <li>{@code id1} → {@code at0001}</li>
     *   <li>{@code id12} → {@code at0012}</li>
     *   <li>{@code id0.1} → {@code at0000.1}</li>
     *   <li>{@code ac1} → {@code ac1} (value-set ids kept until expanded)</li>
     * </ul>
     */
    public static String toAtCode(String code) {
        if (code == null) {
            return null;
        }
        Matcher m = ID_OR_AC.matcher(code);
        if (!m.matches()) {
            return code;
        }
        String leader = m.group(1);
        if ("ac".equals(leader)) {
            return code; // keep ac* until value-set expansion replaces uses
        }
        String[] parts = m.group(2).split("\\.");
        parts[0] = String.format("%04d", Integer.parseInt(parts[0]));
        return "at" + String.join(".", parts);
    }

    /** Rewrite {@code [idN]} / bare id codes inside a free-form string (rules, paths). */
    public static String rewriteEmbeddedIds(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher m = Pattern.compile("\\bid(\\d+(?:\\.\\d+)*)\\b").matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(toAtCode("id" + m.group(1))));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
