package com.nedap.archie.adl14.down;

import com.nedap.archie.adl14.ADL14ConversionConfiguration;
import com.nedap.archie.adl14.ADL14Parser;
import com.nedap.archie.adlparser.ADLParser;
import com.nedap.archie.aom.Archetype;
import com.nedap.archie.aom.terminology.ArchetypeTerm;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.openehr.referencemodels.BuiltinReferenceModels;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * id-coded ADL2 → ADL 1.4 node/value code collisions, see
 * https://github.com/ErikSundvall/ehrtslib/issues/127.
 * ADL2 spec master09.09-spec_terminology.adoc gives the same archetype in both coding systems:
 * id-coded {@code DV_CODED_TEXT[id4]} / value {@code at4} (l.103, l.124) correspond to at-coded
 * {@code DV_CODED_TEXT[at9000]} / {@code at0003} (l.31, l.52).
 */
public class CodeCollision127Test {

    private static String resource(String name) throws Exception {
        try (InputStream in = CodeCollision127Test.class.getResourceAsStream(name)) {
            java.util.Scanner sc = new java.util.Scanner(in, "UTF-8").useDelimiter("\\A");
            return sc.hasNext() ? sc.next() : "";
        }
    }

    private static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static ArchetypeTerm term(String text) {
        ArchetypeTerm t = new ArchetypeTerm();
        t.setText(text);
        t.setDescription(text);
        return t;
    }

    private static TreeSet<String> atCodes(String s) {
        TreeSet<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("\\[(at[0-9.]+)\\]").matcher(s);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test
    public void specExample_valueKeepsShift_nodeMovesTo9000() throws Exception {
        ADLParser p2 = new ADLParser(BuiltinReferenceModels.getMetaModelProvider());
        Archetype a2 = p2.parse(resource("openEHR-EHR-EVALUATION.code_list_parent.v1.0.0.adls"));
        assertTrue(p2.getErrors().hasNoErrors(), "ADL2 parse: " + p2.getErrors());
        ADL14DownConversionResult r = new ADL2ToADL14DownConverter().convert(a2);
        String adl14 = r.getAdl14Text();
        assertTrue(adl14.contains("EVALUATION[at0000]"), adl14);
        assertTrue(adl14.contains("ELEMENT[at0002]"), adl14);
        assertTrue(adl14.contains("DV_CODED_TEXT[at9000]"), adl14);
        assertTrue(adl14.contains("local::at0003, at0004"), adl14);
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("id4") && w.contains("at9000")),
                String.valueOf(r.getWarnings()));
        ADL14Parser p14 = new ADL14Parser(BuiltinReferenceModels.getMetaModelProvider());
        p14.parse(adl14, new ADL14ConversionConfiguration());
        assertTrue(p14.getErrors().hasNoErrors(), "ADL14 re-parse: " + p14.getErrors() + "\n" + adl14);
    }

    @Test
    public void identicalTerms_share_differentTerms_nodeMoves() {
        Map<String, Map<String, ArchetypeTerm>> terms = new LinkedHashMap<>();
        Map<String, ArchetypeTerm> en = new LinkedHashMap<>();
        en.put("id1", term("Root"));
        en.put("id5", term("Present"));
        en.put("at5", term("Present"));
        en.put("id2", term("Node"));
        en.put("at2", term("Value"));
        terms.put("en", en);
        List<String> warnings = new ArrayList<>();
        Adl14CodeMap m = Adl14CodeMap.build(Arrays.asList("id1", "id2", "id5"),
                Arrays.asList("at2", "at5"), terms, warnings);
        assertEquals("at0000", m.get("id1"));
        assertEquals("at0004", m.get("id5"));
        assertEquals("at0004", m.get("at5"));
        assertEquals("at0001", m.get("at2"));
        assertEquals("at9000", m.get("id2"));
        assertEquals(1, warnings.size(), String.valueOf(warnings));
    }

    @Test
    public void specialisedNodeMovesToSameLevel() {
        Map<String, Map<String, ArchetypeTerm>> terms = new LinkedHashMap<>();
        Map<String, ArchetypeTerm> en = new LinkedHashMap<>();
        en.put("id0.3", term("N3"));
        en.put("at0.3", term("V3"));
        en.put("id0.10", term("N10"));
        en.put("at0.10", term("V10"));
        terms.put("en", en);
        Adl14CodeMap m = Adl14CodeMap.build(Arrays.asList("id1.1", "id0.10", "id0.3"),
                Arrays.asList("at0.10", "at0.3"), terms, new ArrayList<>());
        assertEquals("at0.3", m.get("at0.3"));
        assertEquals("at0.9000", m.get("id0.3"));
        assertEquals("at0.9001", m.get("id0.10"));
        assertEquals("at0000.1", m.get("id1.1"));
    }

    @Test
    public void syntheticIdsAreDropped() {
        Map<String, Map<String, ArchetypeTerm>> terms = new LinkedHashMap<>();
        Map<String, ArchetypeTerm> en = new LinkedHashMap<>();
        en.put("id1", term("Root"));
        terms.put("en", en);
        Adl14CodeMap m = Adl14CodeMap.build(Arrays.asList("id1", "id9000"), new ArrayList<>(), terms,
                new ArrayList<>());
        assertEquals(Adl14CodeMap.DROPPED, m.get("id9000"));
    }

    @Test
    public void ckmOrganisationRoundTripKeepsCkmCodes() throws Exception {
        Path g = java.nio.file.Paths.get("/workspace/openehr-genomik-templates/archetypes");
        Path ckm = g.resolve("ckm/openEHR-EHR-CLUSTER.organisation.v1.adl");
        Path adl2 = g.resolve("ckm-adl2/openEHR-EHR-CLUSTER.organisation.v1.0.4.adls");
        Assumptions.assumeTrue(Files.exists(ckm) && Files.exists(adl2), "genomics workspace not present");
        ADLParser p2 = new ADLParser(BuiltinReferenceModels.getMetaModelProvider());
        Archetype a2 = p2.parse(read(adl2));
        String adl14 = new ADL2ToADL14DownConverter().convert(a2).getAdl14Text();
        assertEquals(atCodes(read(ckm)), atCodes(adl14));
        assertFalse(adl14.contains("at8999"), adl14);
    }
}
