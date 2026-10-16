package com.nedap.archie.adl14.down;

import com.nedap.archie.adl14.ADL14ConversionConfiguration;
import com.nedap.archie.adl14.ADL14Parser;
import com.nedap.archie.adlparser.ADLParser;
import com.nedap.archie.aom.Archetype;
import org.junit.jupiter.api.Test;
import org.openehr.referencemodels.BuiltinReferenceModels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ADL2ToADL14DownConverterTest {

    private static final String MINIMAL_ADL2 = ""
            + "archetype (adl_version=2.0.6; rm_release=1.0.4)\n"
            + "\topenEHR-EHR-CLUSTER.down_probe.v1.0.0\n"
            + "\n"
            + "language\n"
            + "\toriginal_language = <[ISO_639-1::en]>\n"
            + "\n"
            + "description\n"
            + "\toriginal_author = <\n"
            + "\t\t[\"name\"] = <\"test\">\n"
            + "\t>\n"
            + "\tlifecycle_state = <\"unmanaged\">\n"
            + "\tdetails = <\n"
            + "\t\t[\"en\"] = <\n"
            + "\t\t\tlanguage = <[ISO_639-1::en]>\n"
            + "\t\t\tpurpose = <\"probe\">\n"
            + "\t\t>\n"
            + "\t>\n"
            + "\n"
            + "definition\n"
            + "\tCLUSTER[id1] matches {\n"
            + "\t\titems matches {\n"
            + "\t\t\tELEMENT[id2] matches {\n"
            + "\t\t\t\tvalue matches {\n"
            + "\t\t\t\t\tDV_TEXT[id3]\n"
            + "\t\t\t\t}\n"
            + "\t\t\t}\n"
            + "\t\t}\n"
            + "\t}\n"
            + "\n"
            + "terminology\n"
            + "\tterm_definitions = <\n"
            + "\t\t[\"en\"] = <\n"
            + "\t\t\t[\"id1\"] = <\n"
            + "\t\t\t\ttext = <\"Probe\">\n"
            + "\t\t\t\tdescription = <\"Probe cluster\">\n"
            + "\t\t\t>\n"
            + "\t\t\t[\"id2\"] = <\n"
            + "\t\t\t\ttext = <\"Item\">\n"
            + "\t\t\t\tdescription = <\"Item\">\n"
            + "\t\t\t>\n"
            + "\t\t\t[\"id3\"] = <\n"
            + "\t\t\t\ttext = <\"Value\">\n"
            + "\t\t\t\tdescription = <\"Value\">\n"
            + "\t\t\t>\n"
            + "\t\t>\n"
            + "\t>\n";

    @Test
    public void idMapperPadsSimpleIds() {
        // ADL2 spec: [at0000] ([id1]), [at0001] ([id2]), [at0000.1] ([id1.1])
        assertEquals("at0000", IdToAtCodeMapper.toAtCode("id1"));
        assertEquals("at0001", IdToAtCodeMapper.toAtCode("id2"));
        assertEquals("at0011", IdToAtCodeMapper.toAtCode("id12"));
        assertEquals("at0000.1", IdToAtCodeMapper.toAtCode("id1.1"));
        assertEquals("at0.1", IdToAtCodeMapper.toAtCode("id0.1"));
        assertEquals("at0003", IdToAtCodeMapper.toAtCode("at4"));
        assertEquals("at0003", IdToAtCodeMapper.toAtCode("at0003"));
        // inverse of Archie's own ADL 1.4 → 2 converter
        for (String old : new String[] {"at0000", "at0001", "at0011", "at0000.1", "at0004.2", "at0.1"}) {
            String adl2 = com.nedap.archie.adl14.ADL14NodeIDConverter.convertCode(old, "id");
            assertEquals(old, IdToAtCodeMapper.toAtCode(adl2));
        }
        assertEquals("ac1", IdToAtCodeMapper.toAtCode("ac1"));
    }

    @Test
    public void downConvertMinimal_isParseableAsAdl14() throws Exception {
        ADLParser p2 = new ADLParser(BuiltinReferenceModels.getMetaModelProvider());
        Archetype a2 = p2.parse(MINIMAL_ADL2);
        assertTrue(p2.getErrors().hasNoErrors(), "ADL2 parse: " + p2.getErrors());

        ADL14DownConversionResult result = new ADL2ToADL14DownConverter().convert(a2);
        String adl14 = result.getAdl14Text();
        assertTrue(adl14.contains("adl_version=1.4"));
        assertTrue(adl14.contains("concept"));
        assertTrue(adl14.contains("ontology"));
        assertFalse(adl14.contains("undefined[undefined]"), "must not emit undefined[undefined]");
        assertTrue(adl14.contains("CLUSTER[at0000]"));
        assertTrue(adl14.contains("openEHR-EHR-CLUSTER.down_probe.v1"));

        ADL14Parser p14 = new ADL14Parser(BuiltinReferenceModels.getMetaModelProvider());
        Archetype parsed14 = p14.parse(adl14, new ADL14ConversionConfiguration());
        assertTrue(p14.getErrors().hasNoErrors(),
                "ADL14 re-parse errors: " + p14.getErrors() + "\n---\n" + adl14);
        assertEquals("CLUSTER", parsed14.getDefinition().getRmTypeName());
    }

    @Test
    public void refusesEmptyDefinition() {
        final Archetype empty = new Archetype();
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                new ADL2ToADL14DownConverter().convert(empty);
            }
        });
    }
}
