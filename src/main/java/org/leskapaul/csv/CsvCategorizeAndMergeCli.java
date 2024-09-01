package org.leskapaul.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.leskapaul.csv.DateTransformerConfig.extractDateTransformerConfig;

public class CsvCategorizeAndMergeCli {

    private static final Logger LOG = LogManager.getLogger(CsvCategorizeAndMergeCli.class);

    public static void main(String[] args) {
        LOG.info("This program expects the following arguments: <path to yaml config> <one or more input csv files, separated by a space>");
        LOG.debug("called with args: {}", Stream.of(args).collect(Collectors.toList()));

        if (args.length < 2) {
            LOG.error("this program requires at least two arguments");
            return;
        }

        CsvCategorizeAndMerge.CsvOrganizerConfig config;
        try {
            config = loadConfig(new FileInputStream(args[0]));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("failed to load config file " + args[0], e);
        }

        List<CSVParser> csvParsers = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            try {
                CSVParser csvParser = CSVParser.parse(new FileInputStream(args[i]),
                        StandardCharsets.UTF_8, CSVFormat.DEFAULT.withFirstRecordAsHeader());
                csvParsers.add(csvParser);
            } catch (IOException e) {
                throw new RuntimeException("failed to load csv file " + args[i], e);
            }
        }

        try {
            List<CsvCategorizeAndMerge.CategoryCsvLines> lines =
                    new CsvCategorizeAndMerge().organizeCsvLines(csvParsers, config);
            printCsv(config, lines);
        } finally {
            for (CSVParser parser: csvParsers) {
                try {
                    parser.close();
                } catch (IOException e) {
                    LOG.warn("failed to close csv file", e);
                }
            }
        }
    }

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
                    sbForRow.append(cellValue == null ? "" : cellValue);
                });
                sb.append(sbForRow).append('\n');
            });
            sb.append("\n");
        });

        LOG.info("csv result ->\n{}", sb);
    }

    public static CsvCategorizeAndMerge.CsvOrganizerConfig loadConfig(InputStream inputStreamForConfig) {
        Yaml yaml = new Yaml();
        Map configAsMap;
        try {
            configAsMap = yaml.load(inputStreamForConfig);
        } catch (RuntimeException e) {
            throw new RuntimeException("failed to load yaml config", e);
        } finally {
            try {
                inputStreamForConfig.close();
            } catch (IOException e) {
                throw new RuntimeException("failed to close input stream for yaml config", e);
            }
        }

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
                Map.Entry categoryConfigAsMapEntry = (Map.Entry) ((Map) map).entrySet().stream().findFirst().get();
                if (categoryConfigAsMapEntry != null) {
                    String columnName = (String) categoryConfigAsMapEntry.getKey();

                    List<Map> categoryConfigsForColumn = (List<Map>) categoryConfigAsMapEntry.getValue();
                    categoryConfigsForColumn.forEach(categoryConfigAsMap -> {
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
                    });
                }
            });
        }

        List transformerConfigs = (List) configAsMap.get("columnNameToTransformer");
        if (transformerConfigs != null) {
            transformerConfigs.forEach(map -> {
                Map.Entry categoryConfigAsMapEntry = (Map.Entry) ((Map) map).entrySet().stream().findFirst().get();
                if (categoryConfigAsMapEntry != null) {
                    String columnName = (String) categoryConfigAsMapEntry.getKey();
                    Map<String, Object> dateTransformerConfigAsMap = (Map<String, Object>) categoryConfigAsMapEntry.getValue();
                    DateTransformerConfig dateTransformerConfig = extractDateTransformerConfig(dateTransformerConfigAsMap);
                    if (dateTransformerConfig != null) {
                        config.getColumnNameToDateTransformer().put(columnName, dateTransformerConfig);
                    }
                }
            });
        }

        return config;
    }
}
