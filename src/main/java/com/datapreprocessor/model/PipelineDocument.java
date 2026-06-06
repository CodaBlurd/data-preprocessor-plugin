package com.datapreprocessor.model;

import java.util.List;

public class PipelineDocument {
    public int schemaVersion = 1;
    public String createdBy = "Data Preprocessor";
    public List<StepDto> steps;

    public static class StepDto {
        public String operation;
        public String column;
        public String param;

        public StepDto() {}

        public StepDto(String operation, String column, String param) {
            this.operation = operation;
            this.column = column;
            this.param = param;
        }
    }

}
