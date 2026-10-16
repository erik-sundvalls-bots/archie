package com.nedap.archie.adl14.down;

import com.nedap.archie.aom.Archetype;
import com.nedap.archie.aom.CAttribute;
import com.nedap.archie.aom.CComplexObject;
import com.nedap.archie.aom.CObject;
import com.nedap.archie.aom.CPrimitiveObject;
import com.nedap.archie.aom.primitives.CTerminologyCode;
import com.nedap.archie.aom.terminology.ArchetypeTerm;
import com.nedap.archie.aom.terminology.ArchetypeTerminology;
import com.nedap.archie.aom.terminology.ValueSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collision-free ADL2 → ADL 1.4 code map for one archetype
 * (see https://github.com/ErikSundvall/ehrtslib/issues/127).
 * <p>
 * id-coded ADL2 keeps node ids ({@code idN}) and value codes ({@code atN}) in separate
 * spaces; ADL 1.4 has one at-code space. Aligned with the parallel examples in the ADL2
 * spec (master09.09: {@code DV_CODED_TEXT[id4]} ↔ {@code [at9000]}, value {@code at4} ↔
 * {@code at0003}):
 * <ol>
 *   <li>Value codes keep the shifted code ({@link IdToAtCodeMapper#toAtCode}).</li>
 *   <li>A node whose shifted code equals a value code's shares it if both have identical
 *   terms in every language; otherwise the node moves to the next free {@code at9000+}
 *   code (warning). Root codes {@code id1{.1}*} never move; a value code that would take
 *   the root code is renumbered instead.</li>
 *   <li>Synthetic node ids ({@code id9000+} without a term) are dropped ({@link #DROPPED}).</li>
 * </ol>
 */
public final class Adl14CodeMap {

    /** Marker for a node id that has no ADL 1.4 equivalent. */
    public static final String DROPPED = "";

    private static final Pattern NUM = Pattern.compile("^(id|at)(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROOT = Pattern.compile("^id1(\\.1)*$");

    private final Map<String, String> map = new HashMap<>();

    private Adl14CodeMap() {}

    public String get(String code) {
        if (code == null) return null;
        String m = map.get(code);
        return m != null ? m : IdToAtCodeMapper.toAtCode(code);
    }

    public Map<String, String> asMap() {
        return map;
    }

    public static Adl14CodeMap build(Archetype archetype, List<String> warnings) {
        Set<String> ids = new LinkedHashSet<>();
        Set<String> ats = new LinkedHashSet<>();
        ArchetypeTerminology terminology = archetype.getTerminology();
        if (archetype.getDefinition() != null) collect(archetype.getDefinition(), ids, ats);
        if (terminology != null) {
            if (terminology.getTermDefinitions() != null) {
                for (Map<String, ArchetypeTerm> bag : terminology.getTermDefinitions().values()) {
                    for (String c : bag.keySet()) add(c, ids, ats);
                }
            }
            if (terminology.getValueSets() != null) {
                for (ValueSet vs : terminology.getValueSets().values()) {
                    if (vs.getMembers() != null) for (String c : vs.getMembers()) add(c, ids, ats);
                }
            }
        }
        return build(new ArrayList<>(ids), new ArrayList<>(ats),
                terminology == null ? null : terminology.getTermDefinitions(), warnings);
    }

    /** Core rule, usable without an AOM (tests). */
    public static Adl14CodeMap build(List<String> ids, List<String> ats,
                                     Map<String, Map<String, ArchetypeTerm>> terms,
                                     List<String> warnings) {
        Comparator<String> byNum = Comparator.comparing((String c) -> top(c)).thenComparing(c -> c);
        ids = new ArrayList<>(ids);
        ats = new ArrayList<>(ats);
        ids.sort(byNum);
        ats.sort(byNum);
        Adl14CodeMap result = new Adl14CodeMap();
        Set<String> used = new HashSet<>();
        Set<String> rootTargets = new HashSet<>();
        Map<String, String> nodeShift = new LinkedHashMap<>();
        for (String c : ids) {
            String t = IdToAtCodeMapper.toAtCode(c);
            nodeShift.put(c, t);
            if (!isSynthetic(c, terms)) used.add(t);
            if (ROOT.matcher(c).matches()) rootTargets.add(t);
        }
        for (String c : ats) used.add(IdToAtCodeMapper.toAtCode(c));
        int[] next = {9000};
        java.util.function.Supplier<String> nextFree = () -> {
            while (used.contains("at" + next[0])) next[0]++;
            String t = "at" + next[0]++;
            used.add(t);
            return t;
        };
        Map<String, String> valueSource = new HashMap<>();
        for (String c : ats) {
            String t = IdToAtCodeMapper.toAtCode(c);
            if (rootTargets.contains(t)) {
                t = nextFree.get();
                warnings.add("value code " + c + " would collide with the root node code; renumbered to " + t);
            }
            result.map.put(c, t);
            valueSource.putIfAbsent(t, c);
        }
        for (String c : ids) {
            if (isSynthetic(c, terms)) {
                result.map.put(c, DROPPED);
                continue;
            }
            String t = nodeShift.get(c);
            String v = valueSource.get(t);
            if (v == null || ROOT.matcher(c).matches()) {
                result.map.put(c, t);
                continue;
            }
            String nk = termKey(c, terms);
            if (nk != null && nk.equals(termKey(v, terms))) {
                result.map.put(c, t);
                continue;
            }
            String r = nextFree.get();
            result.map.put(c, r);
            warnings.add("node id " + c + " → " + t + " would collide with value code " + v
                    + " → " + t + "; node renumbered to " + r);
        }
        return result;
    }

    private static void collect(CObject object, Set<String> ids, Set<String> ats) {
        if (!(object instanceof CPrimitiveObject) && object.getNodeId() != null) add(object.getNodeId(), ids, ats);
        if (object instanceof CTerminologyCode) {
            for (String c : ((CTerminologyCode) object).getConstraint()) add(c, ids, ats);
        }
        if (object instanceof CComplexObject && ((CComplexObject) object).getAttributes() != null) {
            for (CAttribute attr : ((CComplexObject) object).getAttributes()) {
                if (attr.getChildren() != null) for (CObject child : attr.getChildren()) collect(child, ids, ats);
            }
        }
    }

    private static void add(String c, Set<String> ids, Set<String> ats) {
        if (c == null) return;
        if (c.matches("^id\\d.*")) ids.add(c);
        else if (c.matches("^at\\d.*") && !c.matches("^at0\\d{3}.*")) ats.add(c);
    }

    private static int top(String c) {
        Matcher m = NUM.matcher(c);
        return m.find() ? Integer.parseInt(m.group(2)) : 0;
    }

    private static boolean isSynthetic(String c, Map<String, Map<String, ArchetypeTerm>> terms) {
        return c.startsWith("id") && top(c) >= 9000 && termKey(c, terms) == null;
    }

    private static String termKey(String code, Map<String, Map<String, ArchetypeTerm>> terms) {
        if (terms == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (String lang : new java.util.TreeSet<>(terms.keySet())) {
            ArchetypeTerm t = terms.get(lang) == null ? null : terms.get(lang).get(code);
            if (t != null) any = true;
            sb.append(lang).append('=')
                    .append(t == null ? "-" : Objects.toString(t.getText()) + "|" + Objects.toString(t.getDescription()))
                    .append(';');
        }
        return any ? sb.toString() : null;
    }
}
