package org.leskapaul.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CsvOrganizingTransformerTest {

    private static final Logger LOG = LogManager.getLogger(CsvCategorizeAndMerge.class.getSimpleName());

    private CsvCategorizeAndMerge csvOrganizingTransformer;
    @Before
    public void beforeTest() {
        csvOrganizingTransformer = new CsvCategorizeAndMerge();
    }

    @Test
    public void testSimpleExample() throws IOException {
        CsvCategorizeAndMerge.CsvOrganizerConfig config = loadConfig("/testConfig.yaml");

        try (CSVParser csvParser = CSVParser.parse(getClass().getResourceAsStream("/testCsv.csv"),
                StandardCharsets.UTF_8, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            List<CsvCategorizeAndMerge.CategoryCsvLines> lines =
                    csvOrganizingTransformer.organizeCsvLines(Collections.singletonList(csvParser), config);
            LOG.info("result has lines spanning {} categories: {}", lines.size(), lines);

            assertEquals("expected lines spanning 2 categories", 2, lines.size());
            assertEquals("expected 3 lines for groceries", 3,
                    countLinesForCategory(lines, "Groceries"));
            assertEquals("expected 2 lines for discretionary", 2,
                    countLinesForCategory(lines, "Discretionary"));
        }

    }

    private CsvCategorizeAndMerge.CsvOrganizerConfig loadConfig(String resourceToLoadAsStream) {
        Yaml yaml = new Yaml();
        Map configAsMap = yaml.load(getClass().getResourceAsStream(resourceToLoadAsStream));

        String sortColumnName = (String) configAsMap.get("sortColumnName");
        String sortTypeAsStr = (String) configAsMap.get("sortType");
        CsvCategorizeAndMerge.SortType sortType = sortTypeAsStr == null ? null :
                CsvCategorizeAndMerge.SortType.valueOf(sortTypeAsStr);
        String defaultCategoryName = (String) configAsMap.get("defaultCategoryName");

        CsvCategorizeAndMerge.CsvOrganizerConfig config = new CsvCategorizeAndMerge.CsvOrganizerConfig(
                sortColumnName, sortType, defaultCategoryName);

        List columnNameToAliases = (List) configAsMap.get("columnNameToAliases");
        if (columnNameToAliases != null) {
            columnNameToAliases.forEach(entry -> {
                if (entry instanceof String) {
                    config.getColumnNameToAliases().put((String) entry, Collections.emptySet());
                } else if (entry instanceof Map) {
                    Map.Entry mapEntry = (Map.Entry) ((Map) entry).entrySet().stream().findFirst().get();
                    config.getColumnNameToAliases().put((String) mapEntry.getKey(),
                            new HashSet<>((List) mapEntry.getValue()));
                } else {
                    LOG.warn("ignoring unrecognized type for columnNameToAliases");
                }
            });
        }

        List categoryConfigs = (List) configAsMap.get("columnNameToCategoryConfig");
        if (categoryConfigs != null) {
            categoryConfigs.forEach(map -> {
                Map.Entry categoryConfigAsMapEntry = (Map.Entry) ((Map) map).entrySet().stream().findFirst().get(); //(Map) categoryConfigs.get(key);
                if (categoryConfigAsMapEntry != null) {
                    String columnName = (String) categoryConfigAsMapEntry.getKey();
                    Map categoryConfigAsMap = (Map) categoryConfigAsMapEntry.getValue();
                    String category = (String) categoryConfigAsMap.get("category");
                    List<String> regexes = (List<String>) categoryConfigAsMap.get("regexes");
                    if (columnName != null && category != null && regexes != null) {
                        CsvCategorizeAndMerge.CsvOrganizerCategoryConfig categoryConfig =
                                new CsvCategorizeAndMerge.CsvOrganizerCategoryConfig(category, columnName,
                                        new HashSet<>(regexes));
                        config.getCategoryConfigs().add(categoryConfig);
                    } else {
                        LOG.debug("ignoring incomplete config, columnName={}, category={}, regexes={}",
                                columnName, category, regexes);
                    }
                }
            });
        }

        return config;
    }

    private long countLinesForCategory(List<CsvCategorizeAndMerge.CategoryCsvLines> lines, String category) {
        return lines.stream().filter(categoryCsvLines -> categoryCsvLines.getCategoryName().equals(category))
                .map(CsvCategorizeAndMerge.CategoryCsvLines::getCsvLines)
                .mapToLong(List::size).sum();
    }

}
