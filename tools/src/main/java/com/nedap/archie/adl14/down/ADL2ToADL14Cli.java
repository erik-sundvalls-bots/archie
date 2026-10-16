package com.nedap.archie.adl14.down;

import com.nedap.archie.adlparser.ADLParser;
import com.nedap.archie.aom.Archetype;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI: ADL 2 → ADL 1.4 down-conversion (Archie counterpart to Ehrtslib ADL14Serializer).
 *
 * <pre>
 *   ./gradlew :tools:runADL2ToADL14 -PcliArgs="input.adls output.adl"
 * </pre>
 */
public final class ADL2ToADL14Cli {

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: ADL2ToADL14Cli <input.adls|adl> [output.adl]");
            System.err.println("  Parse ADL 2, down-convert to ADL 1.4 text, write to output or stdout.");
            System.exit(2);
        }
        Path in = Paths.get(args[0]);
        byte[] bytes = Files.readAllBytes(in);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }

        ADLParser parser = new ADLParser();
        Archetype archetype = parser.parse(text);
        if (!parser.getErrors().hasNoErrors()) {
            System.err.println("ADL2 parse errors:\n" + parser.getErrors());
            System.exit(1);
        }

        ADL14DownConversionResult result = new ADL2ToADL14DownConverter().convert(archetype);
        for (String w : result.getWarnings()) {
            System.err.println("WARN: " + w);
        }
        if (args.length == 2) {
            Path out = Paths.get(args[1]);
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            Files.write(out, result.getAdl14Text().getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote ADL 1.4 " + result.getArchetypeId14() + " -> " + out
                    + " (" + result.getAdl14Text().length() + " chars, "
                    + result.getWarnings().size() + " warnings)");
        } else {
            System.out.print(result.getAdl14Text());
        }
    }

    private ADL2ToADL14Cli() {}
}
