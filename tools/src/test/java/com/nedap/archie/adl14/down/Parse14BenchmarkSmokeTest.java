package com.nedap.archie.adl14.down;

import com.nedap.archie.adl14.ADL14ConversionConfiguration;
import com.nedap.archie.adl14.ADL14Parser;
import com.nedap.archie.aom.Archetype;
import org.junit.jupiter.api.Test;
import org.openehr.referencemodels.BuiltinReferenceModels;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Parse14BenchmarkSmokeTest {
    @Test
    public void parseEhrtslibAndArchieOutputs() throws Exception {
        Path root = Paths.get("/workspace/openehr-genomik-templates/build");
        ADL14Parser parser = new ADL14Parser(BuiltinReferenceModels.getMetaModelProvider());
        int ok = 0;
        int total = 0;
        StringBuilder log = new StringBuilder();
        String[] dirs = new String[]{"ehrtslib-adl14", "archie-adl14"};
        for (String dir : dirs) {
            Path d = root.resolve(dir);
            if (!Files.isDirectory(d)) continue;
            DirectoryStream<Path> stream = Files.newDirectoryStream(d, "*.adl");
            try {
                for (Path f : stream) {
                    total++;
                    byte[] bytes = Files.readAllBytes(f);
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    try {
                        Archetype a = parser.parse(text, new ADL14ConversionConfiguration());
                        boolean clean = parser.getErrors().hasNoErrors();
                        if (a != null && clean) {
                            log.append("OK ").append(dir).append("/").append(f.getFileName()).append("\n");
                            ok++;
                        } else {
                            log.append("FAIL ").append(dir).append("/").append(f.getFileName())
                                    .append(": ").append(parser.getErrors()).append("\n");
                        }
                    } catch (Exception e) {
                        log.append("FAIL ").append(dir).append("/").append(f.getFileName())
                                .append(": ").append(e.getMessage()).append("\n");
                    }
                }
            } finally {
                stream.close();
            }
        }
        System.out.println(log.toString());
        System.out.println("parse14 summary ok=" + ok + "/" + total);
        assertTrue(ok > 0, "expected at least one successful parse14; log:\n" + log);
    }
}
