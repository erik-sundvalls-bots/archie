package com.nedap.archie.adl14.down;

import com.nedap.archie.aom.Archetype;
import com.nedap.archie.aom.ArchetypeSlot;
import com.nedap.archie.aom.CArchetypeRoot;
import com.nedap.archie.aom.CAttribute;
import com.nedap.archie.aom.CComplexObject;
import com.nedap.archie.aom.CObject;
import com.nedap.archie.aom.CPrimitiveObject;
import com.nedap.archie.aom.primitives.CTerminologyCode;
import com.nedap.archie.aom.terminology.ArchetypeTerm;
import com.nedap.archie.aom.terminology.ArchetypeTerminology;
import com.nedap.archie.aom.terminology.ValueSet;
import com.nedap.archie.aom.utils.AOMUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * In-place AOM rewrite: ADL2 id-codes → ADL 1.4 at-codes, and expand ac value-set
 * constraints to inline {@code local::…} lists when members are known.
 */
public final class AomIdRewriter {

    private final List<String> warnings;

    public AomIdRewriter(List<String> warnings) {
        this.warnings = warnings;
    }

    public void rewrite(Archetype archetype) {
        if (archetype.getDefinition() != null) {
            rewriteObject(archetype.getDefinition(), archetype.getTerminology());
        }
        if (archetype.getTerminology() != null) {
            rewriteTerminology(archetype.getTerminology());
        }
        if (archetype.getRules() != null && archetype.getRules().getRules() != null
                && !archetype.getRules().getRules().isEmpty()) {
            warnings.add("Rules section left as-is; embedded id-codes in rule text are not rewritten.");
        }
    }

    private void rewriteObject(CObject object, ArchetypeTerminology terminology) {
        // CPrimitiveObject always uses synthetic id9999 and rejects setNodeId.
        if (!(object instanceof CPrimitiveObject) && object.getNodeId() != null) {
            object.setNodeId(IdToAtCodeMapper.toAtCode(object.getNodeId()));
        }
        if (object instanceof CComplexObject) {
            CComplexObject complex = (CComplexObject) object;
            if (complex.getAttributes() != null) {
                for (CAttribute attr : complex.getAttributes()) {
                    if (attr.getChildren() != null) {
                        for (CObject child : attr.getChildren()) {
                            rewriteObject(child, terminology);
                        }
                    }
                }
            }
        }
        if (object instanceof CPrimitiveObject) {
            rewritePrimitive((CPrimitiveObject<?, ?>) object, terminology);
        }
        if (object instanceof ArchetypeSlot) {
            // slot assertions use regex on archetype_id/value — leave unchanged
        }
        if (object instanceof CArchetypeRoot) {
            // archetype_ref stays
        }
    }

    private void rewritePrimitive(CPrimitiveObject<?, ?> prim, ArchetypeTerminology terminology) {
        if (!(prim instanceof CTerminologyCode)) {
            return;
        }
        CTerminologyCode term = (CTerminologyCode) prim;
        List<String> rewritten = new ArrayList<>();
        for (String constraint : term.getConstraint()) {
            if (AOMUtils.isValueSetCode(constraint) && terminology != null
                    && terminology.getValueSets() != null
                    && terminology.getValueSets().containsKey(constraint)) {
                ValueSet vs = terminology.getValueSets().get(constraint);
                List<String> members = vs.getMembers().stream()
                        .map(IdToAtCodeMapper::toAtCode)
                        .collect(Collectors.toList());
                if (members.isEmpty()) {
                    warnings.add("Value set " + constraint + " has no members; left as ac-code.");
                    rewritten.add(constraint);
                } else {
                    rewritten.add("local::" + String.join(", ", members));
                }
            } else if (constraint.startsWith("id") || constraint.startsWith("at")) {
                rewritten.add(IdToAtCodeMapper.toAtCode(constraint));
            } else {
                rewritten.add(constraint);
            }
        }
        term.setConstraint(rewritten);
    }

    private void rewriteTerminology(ArchetypeTerminology terminology) {
        if (terminology.getConceptCode() != null) {
            terminology.setConceptCode(IdToAtCodeMapper.toAtCode(terminology.getConceptCode()));
        }

        Map<String, Map<String, ArchetypeTerm>> newDefs = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, ArchetypeTerm>> langEntry : terminology.getTermDefinitions().entrySet()) {
            Map<String, ArchetypeTerm> newLang = new LinkedHashMap<>();
            for (Map.Entry<String, ArchetypeTerm> codeEntry : langEntry.getValue().entrySet()) {
                newLang.put(IdToAtCodeMapper.toAtCode(codeEntry.getKey()), codeEntry.getValue());
            }
            newDefs.put(langEntry.getKey(), newLang);
        }
        terminology.setTermDefinitions(newDefs);

        if (terminology.getTermBindings() != null && !terminology.getTermBindings().isEmpty()) {
            Map<String, Map<String, URI>> newBindings = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, URI>> byTerminologiy : terminology.getTermBindings().entrySet()) {
                Map<String, URI> newInner = new LinkedHashMap<>();
                for (Map.Entry<String, URI> e : byTerminologiy.getValue().entrySet()) {
                    newInner.put(IdToAtCodeMapper.toAtCode(e.getKey()), e.getValue());
                }
                newBindings.put(byTerminologiy.getKey(), newInner);
            }
            terminology.setTermBindings(newBindings);
        }

        // After expanding ac uses in the definition, drop value_sets (ADL 1.4 has no equivalent section).
        if (terminology.getValueSets() != null && !terminology.getValueSets().isEmpty()) {
            warnings.add("Removed " + terminology.getValueSets().size()
                    + " value_sets after expanding ac-constraints to local:: lists where possible.");
            terminology.setValueSets(new LinkedHashMap<>());
        }
    }
}
