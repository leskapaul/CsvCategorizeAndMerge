package org.leskapaul.csv;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DateTransformerConfig {
    private Set<String> inputFormats;
    private String outputFormat;

    public static DateTransformerConfig extractDateTransformerConfig(Map configAsMap) {
        Map<String, Object> dateTransformerAsMap = (Map<String, Object>) configAsMap.get("dateTransformer");
        if (dateTransformerAsMap != null || !dateTransformerAsMap.isEmpty()) {
            Set<String> inputFormats = new HashSet((List) dateTransformerAsMap.get("inputFormats"));
            return new DateTransformerConfig(inputFormats,
                    (String) dateTransformerAsMap.get("outputFormat"));
        }
        return null;
    }

    public DateTransformerConfig(Set<String> inputFormats, String outputFormat) {
        this.inputFormats = inputFormats;
        this.outputFormat = outputFormat;
    }

    public Set<String> getInputFormats() {
        return inputFormats;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    @Override
    public String toString() {
        return "DateTransformerConfig{" +
                "inputFormats=" + getInputFormats() +
                ", outputFormat='" + getOutputFormat() + '\'' +
                '}';
    }
}
