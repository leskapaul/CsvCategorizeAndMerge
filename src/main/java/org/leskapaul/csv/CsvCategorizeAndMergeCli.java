package org.leskapaul.csv;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class CsvCategorizeAndMergeCli {

    private static final Logger LOG = LogManager.getLogger(CsvCategorizeAndMergeCli.class);

    public static void printCsv(CsvCategorizeAndMerge.CsvOrganizerConfig config,
                                List<CsvCategorizeAndMerge.CategoryCsvLines> lines) {
        StringBuilder sb = new StringBuilder();

        List<String> columnNamesInOrder = new ArrayList(config.getColumnNameToAliases().keySet());
        columnNamesInOrder.forEach(columnName -> {
           if (sb.length() > 0) { sb.append(", "); }
           sb.append(columnName);
        });
        sb.append('\n');

        lines.forEach(categoryCsvLines -> {
            categoryCsvLines.getCsvLines().forEach(csvRowAsMap -> {
                StringBuilder sbForRow = new StringBuilder();
                columnNamesInOrder.forEach(columnName -> {
                    String cellValue = csvRowAsMap.get(columnName);
                    if (sbForRow.length() > 0) { sbForRow.append(", "); }
                    sbForRow.append(cellValue);
                });
                sb.append(sbForRow).append('\n');
            });
            sb.append("\n");
        });

        LOG.info("csv result ->\n{}", sb);
    }

    public static CsvCategorizeAndMerge.CsvOrganizerConfig loadConfig(String resourceToLoadAsStream) {
        Yaml yaml = new Yaml();
        Map configAsMap = yaml.load(CsvCategorizeAndMergeCli.class.getResourceAsStream(resourceToLoadAsStream));

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
}
