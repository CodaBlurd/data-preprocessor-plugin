package com.datapreprocessor.engine;

import com.datapreprocessor.model.RegexRule;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class RegexRuleCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RegexRuleCodec() {}

    /**
     * Encode a regex rule to JSON.
     * @param rule the rule to encode
     * @return the JSON representation of the rule
     */
    public static String encodeToJson(RegexRule rule) {

        try {
            return MAPPER.writeValueAsString(rule);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not encode regex rule", e);
        }
    }

    /**
     * Decode a regex rule from JSON.
     * @param rawJson the JSON representation of the rule
     * @return the decoded rule
     */
    public static RegexRule decodeFromJson(String rawJson) {
        try {
            return MAPPER.readValue(rawJson, RegexRule.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not decode regex rule", e);
        }
    }
}
