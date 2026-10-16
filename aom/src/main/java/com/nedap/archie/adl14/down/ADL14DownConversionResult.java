package com.nedap.archie.adl14.down;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Outcome of ADL 2 → ADL 1.4 down-conversion. */
public class ADL14DownConversionResult {
    private final String adl14Text;
    private final List<String> warnings;
    private final String archetypeId14;

    public ADL14DownConversionResult(String adl14Text, String archetypeId14, List<String> warnings) {
        this.adl14Text = adl14Text;
        this.archetypeId14 = archetypeId14;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public String getAdl14Text() {
        return adl14Text;
    }

    public String getArchetypeId14() {
        return archetypeId14;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
