package com.elicitsoftware.service;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-015 / UC-043 BR-002: the translatable-field whitelist is canonical here, and Author carries a
 * copy. The two must agree.
 * <p>
 * A field one end accepts and the other rejects loses a translation silently: Author would let an
 * author write it and the console would refuse the record at apply time, or the reverse. There is
 * no runtime link between the two classes, so this test is the link -- it reads Author's source
 * from the sibling checkout and compares the parsed sets.
 * <p>
 * Skipped, not failed, when the sibling checkout is absent: Admin builds alone in CI, and a missing
 * sibling is not a defect in Admin. The Elicit umbrella checkout has it.
 */
class TranslatableFieldsParityTest {

    private static final Path AUTHOR_COPY = Path.of(
            "../Author/src/main/java/com/elicitsoftware/author/survey/TranslatableFields.java");
    private static final Pattern ENTRY = Pattern.compile(
            "\"([a-z_]+)\",\\s*(?:java\\.util\\.)?Set\\.of\\(([^)]*)\\)");
    private static final Pattern FIELD = Pattern.compile("\"([a-z_]+)\"");

    @Test
    void authorsCopyMatchesTheCanonicalWhitelist() throws IOException {
        if (!Files.isRegularFile(AUTHOR_COPY)) {
            System.out.println("Author checkout not present at " + AUTHOR_COPY.toAbsolutePath()
                    + "; parity with TranslatableFields not checked");
            return;
        }
        Map<String, Set<String>> authors = parse(Files.readString(AUTHOR_COPY, StandardCharsets.UTF_8));
        Map<String, Set<String>> canonical = new TreeMap<>();
        SurveyDefinitionFileFields.TRANSLATABLE_FIELDS.forEach((k, v) -> canonical.put(k, new TreeSet<>(v)));

        assertTrue(!authors.isEmpty(), "could not parse any entry out of " + AUTHOR_COPY);
        assertEquals(canonical, authors,
                "Author's TranslatableFields has drifted from Admin's canonical whitelist; "
                        + "update Author/src/main/java/com/elicitsoftware/author/survey/TranslatableFields.java");
    }

    @Test
    void everyWhitelistedElementTypeIsOneTheSchemaAllows() {
        // The same seven the translations_element_type_ck check constraint names (Survey V019).
        Set<String> allowed = Set.of("surveys", "steps", "sections", "questions", "select_items",
                "relationships", "reports");
        assertEquals(allowed, new TreeSet<>(SurveyDefinitionFileFields.TRANSLATABLE_FIELDS.keySet()));
    }

    @Test
    void fieldsThatWouldForkStoredDataAreNotTranslatable() {
        // Each of these was considered and rejected; a regression here is a data defect, not a typo.
        assertTrue(!SurveyDefinitionFileFields.isTranslatable("questions", "default_value"),
                "default_value is written into answers.text_value and analysed");
        assertTrue(!SurveyDefinitionFileFields.isTranslatable("select_items", "coded_value"),
                "coded_value is the stored answer");
        assertTrue(!SurveyDefinitionFileFields.isTranslatable("surveys", "name"),
                "a survey's name is an identifier used in file names and console lists");
        assertTrue(!SurveyDefinitionFileFields.isTranslatable("steps", "dimension_name"),
                "dimension_name is a reporting identifier");
        assertTrue(!SurveyDefinitionFileFields.isTranslatable("relationships", "token"),
                "a token key is substituted at runtime, not read");
        assertTrue(!SurveyDefinitionFileFields.isTranslatable("select_groups", "name"),
                "select groups are author-facing only");
    }

    private static Map<String, Set<String>> parse(String source) {
        String body = source.substring(source.indexOf("BY_ELEMENT_TYPE"));
        body = body.substring(0, body.indexOf(");") + 1);
        Map<String, Set<String>> parsed = new TreeMap<>();
        Matcher m = ENTRY.matcher(body);
        while (m.find()) {
            Set<String> fields = new TreeSet<>();
            Matcher f = FIELD.matcher(m.group(2));
            while (f.find()) {
                fields.add(f.group(1));
            }
            parsed.put(m.group(1), fields);
        }
        return parsed;
    }
}
