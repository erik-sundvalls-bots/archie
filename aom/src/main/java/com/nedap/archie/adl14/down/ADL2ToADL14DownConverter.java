package com.nedap.archie.adl14.down;

import com.nedap.archie.aom.Archetype;
import com.nedap.archie.aom.ArchetypeHRID;
import com.nedap.archie.aom.terminology.ArchetypeTerm;
import com.nedap.archie.serializer.adl.ADLArchetypeSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Down-convert an in-memory ADL 2 {@link Archetype} to ADL 1.4 text.
 * <p>
 * Pipeline (comparable to Ehrtslib {@code parseAdl} + {@code ADL14Serializer}):
 * <ol>
 *   <li>Rewrite id-codes → at-codes on the AOM; expand ac value-sets to {@code local::…}</li>
 *   <li>Serialize with the existing ADL 2 serializer</li>
 *   <li>Post-process the text into ADL 1.4 shape (header, concept, ontology, HRID)</li>
 * </ol>
 * Mutates the given archetype in place (clone before calling if you need the ADL2 form).
 */
public class ADL2ToADL14DownConverter {

    private static final Pattern HEAD_ATTRS = Pattern.compile(
            "^archetype\\s*\\(([^)]*)\\)", Pattern.MULTILINE);
    private static final Pattern HRID_LINE = Pattern.compile(
            "(?m)^(\\s+)(openEHR[^\\s]+)\\.v(\\d+)\\.(\\d+)\\.(\\d+)(\\S*)?");

    public ADL14DownConversionResult convert(Archetype archetype) {
        List<String> warnings = new ArrayList<>();
        if (archetype == null) {
            throw new IllegalArgumentException("archetype is null");
        }
        if (archetype.getDefinition() == null
                || archetype.getDefinition().getRmTypeName() == null
                || archetype.getDefinition().getNodeId() == null) {
            throw new IllegalArgumentException(
                    "Refuse to down-convert archetype with empty/incomplete definition "
                            + "(missing rm_type_name/node_id).");
        }

        AomIdRewriter rewriter = new AomIdRewriter(warnings);
        rewriter.rewrite(archetype);
        Adl14CodeMap codes = rewriter.getCodeMap();

        String adl2 = ADLArchetypeSerializer.serialize(archetype);
        String conceptCode = conceptCodeOf(archetype);
        String conceptText = conceptTextOf(archetype, conceptCode);
        String adl14 = toAdl14Text(adl2, conceptCode, conceptText, archetype, warnings, codes);
        String id14 = archetypeId14(archetype);
        return new ADL14DownConversionResult(adl14, id14, warnings);
    }

    private static String conceptCodeOf(Archetype archetype) {
        if (archetype.getTerminology() != null && archetype.getTerminology().getConceptCode() != null) {
            return IdToAtCodeMapper.toAtCode(archetype.getTerminology().getConceptCode());
        }
        if (archetype.getDefinition() != null && archetype.getDefinition().getNodeId() != null) {
            return IdToAtCodeMapper.toAtCode(archetype.getDefinition().getNodeId());
        }
        return "at0000";
    }

    private static String conceptTextOf(Archetype archetype, String conceptCode) {
        if (archetype.getTerminology() == null || archetype.getOriginalLanguage() == null) {
            return conceptCode;
        }
        String lang = archetype.getOriginalLanguage().getCodeString();
        Map<String, ArchetypeTerm> terms = archetype.getTerminology().getTermDefinitions().get(lang);
        if (terms != null && terms.containsKey(conceptCode) && terms.get(conceptCode).getText() != null) {
            return terms.get(conceptCode).getText();
        }
        return conceptCode;
    }

    private static String archetypeId14(Archetype archetype) {
        ArchetypeHRID id = archetype.getArchetypeId();
        if (id == null) {
            return null;
        }
        String full = id.getFullId();
        // openEHR-EHR-CLUSTER.x.v1.0.0 → openEHR-EHR-CLUSTER.x.v1
        return full.replaceFirst("\\.v(\\d+)\\.\\d+\\.\\d+", ".v$1");
    }

    static String toAdl14Text(String adl2, String conceptCode, String conceptText,
                              Archetype archetype, List<String> warnings) {
        return toAdl14Text(adl2, conceptCode, conceptText, archetype, warnings, null);
    }

    static String toAdl14Text(String adl2, String conceptCode, String conceptText,
                              Archetype archetype, List<String> warnings, Adl14CodeMap codes) {
        String text = adl2;

        // Header: force adl_version=1.4; keep rm_release if present; mark generated
        Matcher head = HEAD_ATTRS.matcher(text);
        if (head.find()) {
            String rm = archetype.getRmRelease() != null ? archetype.getRmRelease() : "1.0.2";
            String replacement = "archetype (adl_version=1.4; rm_release=" + rm + "; generated)";
            text = head.replaceFirst(Matcher.quoteReplacement(replacement));
        } else {
            text = text.replaceFirst("^archetype\\b",
                    "archetype (adl_version=1.4; generated)");
        }

        // Shorten HRID on the id line
        text = HRID_LINE.matcher(text).replaceAll("$1$2.v$3");

        // Insert concept AFTER specialize (ADL 1.4 order), else after archetype id.
        // Always end the concept line with a newline so comments cannot swallow specialize.
        if (!Pattern.compile("(?m)^concept\\b").matcher(text).find()) {
            String conceptBlock = "concept\n    [" + conceptCode + "]    -- "
                    + sanitizeComment(conceptText) + "\n\n";
            if (Pattern.compile("(?m)^specialize\\b").matcher(text).find()) {
                text = text.replaceFirst(
                        "(?m)^(specialize\n\\s+\\S+\\s*\n)",
                        "$1\n" + Matcher.quoteReplacement(conceptBlock));
            } else {
                text = text.replaceFirst(
                        "(?m)^(\\s+\\S+\\.v\\d+\\s*)$",
                        "$1\n\n" + Matcher.quoteReplacement(conceptBlock));
            }
        }

        // specialize spelling: ADL 1.4 fixtures in Archie use "specialize" already

        // terminology → ontology
        text = text.replaceAll("(?m)^terminology\\b", "ontology");

        // Wrap each language bag under term_definitions with items = < … > when missing
        text = wrapTermDefinitionItems(text);

        // Rewrite residual [idN] / idN inside definition paths (e.g. /items[id6]/value)
        if (codes != null) {
            Matcher ids = Pattern.compile("\\bid(\\d+(?:\\.\\d+)*)\\b").matcher(text);
            StringBuffer sb = new StringBuffer();
            while (ids.find()) {
                ids.appendReplacement(sb, Matcher.quoteReplacement(codes.get("id" + ids.group(1))));
            }
            ids.appendTail(sb);
            text = sb.toString();
        } else {
            text = IdToAtCodeMapper.rewriteEmbeddedIds(text);
        }
        // Dropped synthetic node ids: TYPE[] → TYPE (ADL 1.4 needs no id there)
        text = text.replaceAll("(?<=[\\w>])\\[\\]", "");

        // Drop value_sets block if serializer still emitted one
        text = text.replaceAll("(?ms)^([ \\t]*)value_sets\\s*=\\s*<.*?\n\\1>", "");

        // term_bindings in ADL2 → constraint_bindings is NOT always correct for 1.4
        // (1.4 uses term_bindings under ontology too in many CKM files). Leave as term_bindings.
        warnings.add("Down-conversion is best-effort: ODIN quoting and some specialised "
                + "code forms may still differ from CKM ADL 1.4 style.");

        return text.trim() + "\n";
    }

    private static String sanitizeComment(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\r\\n]+", " ").trim();
    }

    /**
     * ADL 1.4 ontology often uses {@code ["en"] = < items = < ... > >};
     * ADL 2 terminology omits the {@code items} wrapper. Add it when absent.
     */
    static String wrapTermDefinitionItems(String adl) {
        Pattern langBag = Pattern.compile(
                "(?m)^([ \\t]*)\\[\"([^\"]+)\"\\]\\s*=\\s*<\\s*$");
        // Only operate inside ontology/term_definitions — heuristic: after "term_definitions"
        int td = adl.indexOf("term_definitions");
        if (td < 0) {
            return adl;
        }
        String head = adl.substring(0, td);
        String rest = adl.substring(td);
        if (rest.contains("items = <")) {
            return adl; // already wrapped or mixed; don't double-wrap
        }
        // Simple approach: after each ["lang"] = < under term_definitions, insert items = <
        // and before the matching close of that lang bag. This is fragile for nested ODIN;
        // good enough for Archie-serialized terminology.
        StringBuilder out = new StringBuilder();
        String[] lines = rest.split("\n", -1);
        boolean inTermDefs = false;
        int langDepth = -1;
        String langIndent = null;
        boolean openedItems = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.matches("\\s*term_definitions\\s*=\\s*<\\s*")) {
                inTermDefs = true;
                out.append(line).append("\n");
                continue;
            }
            if (inTermDefs) {
                Matcher lm = Pattern.compile("^([ \\t]*)\\[\"([^\"]+)\"\\]\\s*=\\s*<\\s*$").matcher(line);
                if (lm.matches() && !openedItems) {
                    langIndent = lm.group(1);
                    out.append(line).append("\n");
                    out.append(langIndent).append("    items = <").append("\n");
                    openedItems = true;
                    langDepth = 1;
                    continue;
                }
                if (openedItems && langIndent != null) {
                    // closing of language bag: same indent as langIndent + ">"
                    if (line.matches("^" + Pattern.quote(langIndent) + ">\\s*$")) {
                        out.append(langIndent).append("    >").append("\n");
                        out.append(line).append("\n");
                        openedItems = false;
                        langIndent = null;
                        continue;
                    }
                    // indent content one more level inside items
                    if (line.startsWith(langIndent + "    ") || line.trim().isEmpty()) {
                        out.append(line).append("\n");
                        continue;
                    }
                }
                // end of term_definitions top-level
                if (line.matches("^\\s*>\\s*$") && !openedItems) {
                    // could be closing term_definitions
                    inTermDefs = false;
                }
            }
            out.append(line).append("\n");
        }
        String rebuilt = out.toString();
        // drop trailing extra newline from split join
        if (rebuilt.endsWith("\n") && !rest.endsWith("\n")) {
            rebuilt = rebuilt.substring(0, rebuilt.length() - 1);
        }
        return head + rebuilt;
    }
}
