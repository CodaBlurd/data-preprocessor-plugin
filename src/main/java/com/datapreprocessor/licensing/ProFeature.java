package com.datapreprocessor.licensing;

public enum ProFeature {
    R_CODE_GENERATION("R code generation"),
    SQL_CODE_GENERATION("SQL code generation"),
    VISUALISATIONS("Column visualisations"),
    REGEX_CLEANING("Regex cleaning rules"),
    PIPELINE_FILES("Pipeline import/export"),
    BATCH_PROCESSING("Multi-file batch processing");

    private final String label;

    ProFeature(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
