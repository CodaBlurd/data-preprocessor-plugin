package com.datapreprocessor.engine;

import com.datapreprocessor.engine.CodeGenerator.Operation;
import com.datapreprocessor.engine.CodeGenerator.PreprocessingStep;
import com.datapreprocessor.model.DataSet;
import com.datapreprocessor.model.RegexRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegexRuleTest {

    @Test
    void regexReplaceUpdatesOnlyTargetColumnValues() {
        DataSet dataSet = dataSet(
                List.of("email", "status"),
                List.of(
                        List.of("ada@example.com", "active"),
                        List.of("grace@sample.org", "inactive"),
                        List.of("", "pending")
                )
        );

        new DataCleaner().regexReplace(dataSet, "email", "@.*$", "@redacted.test");

        assertEquals("ada@redacted.test", dataSet.getValue(0, 0));
        assertEquals("grace@redacted.test", dataSet.getValue(1, 0));
        assertEquals("", dataSet.getValue(2, 0));
        assertEquals("active", dataSet.getValue(0, 1));
    }

    @Test
    void regexReplaceRejectsInvalidPatterns() {
        DataSet dataSet = dataSet(
                List.of("text"),
                List.of(List.of("value"))
        );

        assertThrows(IllegalArgumentException.class,
                () -> new DataCleaner().regexReplace(dataSet, "text", "[", ""));
    }

    @Test
    void regexRuleCodecRoundTripsJsonParam() {
        RegexRule rule = new RegexRule("\\s+", "_");

        String encoded = RegexRuleCodec.encodeToJson(rule);
        RegexRule decoded = RegexRuleCodec.decodeFromJson(encoded);

        assertEquals(rule, decoded);
    }

    @Test
    void codeGeneratorEmitsRegexReplaceForSupportedLanguages() {
        String param = RegexRuleCodec.encodeToJson(new RegexRule("^(\\w+)\\s+(\\w+)$", "$2, $1"));
        List<PreprocessingStep> steps = List.of(new PreprocessingStep(Operation.REGEX_REPLACE, "name", param));
        DataSet dataSet = dataSet(
                List.of("name"),
                List.of(List.of("Ada Lovelace"))
        );
        dataSet.setFilePath("/tmp/input.csv");

        CodeGenerator generator = new CodeGenerator();
        String python = generator.generate("/tmp/input.csv", steps);
        String r = generator.generateR("/tmp/input.csv", steps);
        String sql = generator.generateSql("/tmp/input.csv", steps, dataSet);

        assertTrue(python.contains(".str.replace(\"^(\\\\w+)\\\\s+(\\\\w+)$\", \"\\\\2, \\\\1\", regex=True)"));
        assertTrue(r.contains("gsub(\"^(\\\\w+)\\\\s+(\\\\w+)$\", \"\\\\2, \\\\1\", as.character(df$`name`[.idx]), perl = TRUE)"));
        assertTrue(sql.contains("REGEXP_REPLACE(CAST(\"name\" AS TEXT), '^(\\w+)\\s+(\\w+)$', '\\2, \\1', 'g')"));
    }

    private DataSet dataSet(List<String> headers, List<List<String>> rows) {
        DataSet dataSet = new DataSet();
        dataSet.setHeaders(new ArrayList<>(headers));
        List<List<String>> copiedRows = new ArrayList<>();
        for (List<String> row : rows) {
            copiedRows.add(new ArrayList<>(row));
        }
        dataSet.setRows(copiedRows);
        return dataSet;
    }
}
