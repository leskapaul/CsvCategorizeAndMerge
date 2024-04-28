package org.leskapaul.csv;

import org.apache.commons.csv.CSVParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * This class is intended to take the following input:
 * - multiple csv files
 * - a mapping of column names to column name aliases (e.g. "Description" -> "Description","Desc")
 * - a mapping of categories to [column name, column value regex] pairs (e.g.  -> "Groceries" -> "Transaction Name"="*ShopRite*")
 * - a specification of aggregrations to add at end of category (e.g. "sheet-sum" -> "Groceries Total = $SUM(C17-C41)")
 *
 * ...and produce the following output:
 * - a single csv file in which input csv lines are organized into category sections
 */
public class CsvCategorizeAndMerge {

    private static final Logger LOG = LogManager.getLogger(CsvCategorizeAndMerge.class.getSimpleName());
    public List<CategoryCsvLines> organizeCsvLines(List<CSVParser> inputCsvs,
                                                   CsvOrganizerConfig csvOrganizerConfig) {
        LOG.info("organizing csv from {} files with config: {}", inputCsvs.size(), csvOrganizerConfig);
        final Map<String, CategoryCsvLines> allCategoriesToLines = new HashMap<>();
        for (CSVParser inputCsv : inputCsvs) {
            LOG.debug("processing inputCsv with headerMap={}", inputCsv.getHeaderMap());

            Map<String, CategoryCsvLines> categoryToLines = getCategoryToLines(inputCsv, csvOrganizerConfig);
            if (categoryToLines == null || categoryToLines.isEmpty()) {
                LOG.warn("organizing returned nothing, check the configuration you provided");
            } else {
                categoryToLines.entrySet().forEach(singleOrganizedCsvEntry -> {
                    CategoryCsvLines categoryCsvLines = allCategoriesToLines.get(singleOrganizedCsvEntry.getKey());
                    if (categoryCsvLines == null) {
                        allCategoriesToLines.put(singleOrganizedCsvEntry.getKey(), singleOrganizedCsvEntry.getValue());
                    } else {
                        categoryCsvLines.getCsvLines().addAll(singleOrganizedCsvEntry.getValue().getCsvLines());
                    }
                });
            }
        }

        // return them in the order specified by config, with each category's lines sorted as specified
        final List<CategoryCsvLines> organizedCsvs = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        categories.add(csvOrganizerConfig.getDefaultCategoryName());
        categories.addAll(csvOrganizerConfig.getCategoryConfigs().stream()
                .map(CsvOrganizerCategoryConfig::getCategory).toList());
        categories.forEach(category -> {
            CategoryCsvLines categoryCsvLines = allCategoriesToLines.get(category);
            sortCategoryCsvLines(categoryCsvLines, csvOrganizerConfig);
            organizedCsvs.add(categoryCsvLines);
        });

        return organizedCsvs;
    }

    private void sortCategoryCsvLines(CategoryCsvLines categoryCsvLines, CsvOrganizerConfig csvOrganizerConfig) {
        categoryCsvLines.getCsvLines().sort((cellMap1, cellMap2) -> {
            if (SortType.DESC.equals(csvOrganizerConfig.getSortType())) {
                Map<String, String> temp = cellMap2;
                cellMap2 = cellMap1;
                cellMap1 = temp;
            }
            String safeStr1 = ensureNotNull(csvOrganizerConfig, cellMap1);
            String safeStr2 = ensureNotNull(csvOrganizerConfig, cellMap2);
            return safeStr1.compareTo(safeStr2);
        });
    }

    private static String ensureNotNull(CsvOrganizerConfig csvOrganizerConfig, Map<String, String> cellMap) {
        return cellMap.get(csvOrganizerConfig.getSortColumnName()) == null ?
                "" : cellMap.get(csvOrganizerConfig.getSortColumnName());
    }

    private Map<String, CategoryCsvLines> getCategoryToLines(CSVParser inputCsv,
                                                      CsvOrganizerConfig csvOrganizerConfig) {

        Map<String, CategoryCsvLines> categoryToLines = new HashMap<>();
        try {
            inputCsv.forEach(csvRecord -> {
                Map<String, String> csvLineAsMap = csvRecord.toMap();

                CategoryCsvLines categorizedCsvLine = categorizeCsvLine(csvLineAsMap, csvOrganizerConfig);
                LOG.debug("resolved categorizedCsvLine: {}\n", categorizedCsvLine);

                CategoryCsvLines existingLines = categoryToLines.get(categorizedCsvLine.getCategoryName());
                if (existingLines == null) {
                    categoryToLines.put(categorizedCsvLine.getCategoryName(), categorizedCsvLine);
                } else {
                    existingLines.getCsvLines().addAll(categorizedCsvLine.getCsvLines());
                }
            });
        } finally {
            try {
                inputCsv.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return categoryToLines;
    }

    private CategoryCsvLines categorizeCsvLine(Map<String, String> csvLineAsMap, CsvOrganizerConfig csvOrganizerConfig) {
        CategoryCsvLines csvLine = null;
        Map<String, String> normalizedColumnToValue = new HashMap<>();
        LOG.debug("processing csvLine: {}", csvLineAsMap);

        String category = null;
        for (Map.Entry<String, String> csvCell : csvLineAsMap.entrySet()) {
            String rawColumnName = csvCell.getKey() == null ?  null : csvCell.getKey().trim();
            String normalizedColumnName = extractNormalizedColumnName(rawColumnName, csvOrganizerConfig);
            if (normalizedColumnName == null) {
                LOG.warn("skipping column unspecified by input config: {}", rawColumnName);
                continue;
            }
            LOG.trace("determined normalized column name {} for cell with column {}", normalizedColumnName, csvCell.getKey());

            String safeCellValue =  csvCell.getValue() == null ? "" : csvCell.getValue().trim();
            if (category == null) {
                category = extractCategory(normalizedColumnName, safeCellValue, csvOrganizerConfig);
                if (category != null) {
                    LOG.debug("resolved category={} for column={} with value={}",
                            category, normalizedColumnName, safeCellValue);
                }
            }

            normalizedColumnToValue.put(normalizedColumnName, safeCellValue);
        }

        if (category == null) {
            category = csvOrganizerConfig.getDefaultCategoryName();
            LOG.debug("no category resolved, so using default={}", category);
        }

        List<Map<String,String>> modifiableList = new ArrayList<>();
        modifiableList.add(normalizedColumnToValue);
        csvLine = new CategoryCsvLines(category, modifiableList);
        return csvLine;
    }

    private static String extractCategory(String normalizedColumnName,
                                          String cellValue,
                                          CsvOrganizerConfig csvOrganizerConfig) {
        Set<CsvOrganizerCategoryConfig> categoryConfigs = csvOrganizerConfig.getColumnNameToCategoryRegexes()
                .get(normalizedColumnName);
        String category = null;
        if (categoryConfigs == null) {
            LOG.debug("no category config found for column={}", normalizedColumnName);
        } else {
            outer:
            for (CsvOrganizerCategoryConfig categoryConfig : categoryConfigs) {
                Set<String> regexList = categoryConfig.getRegexes();
                if (regexList != null) {
                    for (String regex : regexList) {
                        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                        LOG.debug("comparing cellValue={} against regex={}", cellValue, regex);

                        if (pattern.matcher(cellValue).matches()) {
                            category = categoryConfig.getCategory();
                            LOG.debug("found category={} for column={} value={}",
                                    category, normalizedColumnName, cellValue);
                            break outer;
                        }
                    }
                }
            }
        }
        return category;
    }

    private String extractNormalizedColumnName(String cellColumn, CsvOrganizerConfig csvOrganizerConfig) {
        for (Map.Entry<String,Set<String>> columnNameToAliasEntry :
                csvOrganizerConfig.getColumnNameToAliases().entrySet()) {
            if (cellColumn.equalsIgnoreCase(columnNameToAliasEntry.getKey())) {
                return columnNameToAliasEntry.getKey();
            } else {
                for (String alias : columnNameToAliasEntry.getValue()) {
                    if (cellColumn.equalsIgnoreCase(alias)) {
                        return columnNameToAliasEntry.getKey();
                    }
                }
            }
        }
        return null;
    }

    public static class CategoryCsvLines {
        private String categoryName;
        private List<Map<String, String>> csvLines;

        public CategoryCsvLines(String categoryName, List<Map<String, String>> csvLines) {
            this.categoryName = categoryName;
            this.csvLines = csvLines;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public List<Map<String, String>> getCsvLines() {
            return csvLines;
        }

        @Override
        public String toString() {
            return "CategoryCsvLines{" +
                    "categoryName='" + categoryName + '\'' +
                    ", csvLines=" + csvLines +
                    '}';
        }
    }

    public static class CsvOrganizerConfig {
        private LinkedHashMap<String, Set<String>> columnNameToAliases = new LinkedHashMap<>();
        private List<CsvOrganizerCategoryConfig> categoryConfigs = new ArrayList<>();
        private String defaultCategoryName;
        private String sortColumnName;
        private SortType sortType;

        public CsvOrganizerConfig(String sortCategoryName, SortType sortType) {
            this(sortCategoryName, sortType, "Other");
        }

        public CsvOrganizerConfig(String sortColumnName, SortType sortType, String defaultCategoryName) {
            this.sortColumnName = sortColumnName;
            this.sortType = sortType;
            if (defaultCategoryName == null) {
                throw new IllegalStateException("defaultCategoryName must be non-null");
            }
            this.defaultCategoryName = defaultCategoryName;
        }

        public String getDefaultCategoryName() {
            return defaultCategoryName;
        }

        public String getSortColumnName() {
            return sortColumnName;
        }

        public SortType getSortType() {
            return sortType;
        }

        public Map<String, Set<String>> getColumnNameToAliases() {
            return columnNameToAliases;
        }

        public LinkedHashMap<String, Set<CsvOrganizerCategoryConfig>> getColumnNameToCategoryRegexes() {
            LinkedHashMap<String, Set<CsvOrganizerCategoryConfig>> indexByColumnName = new LinkedHashMap<>();
            for (CsvOrganizerCategoryConfig categoryConfig : categoryConfigs) {
                indexByColumnName.computeIfAbsent(categoryConfig.getColumnName(), key -> new HashSet<>());
                indexByColumnName.get(categoryConfig.getColumnName()).add(categoryConfig);
            }
            return indexByColumnName;
        }

        public List<CsvOrganizerCategoryConfig> getCategoryConfigs() {
            return categoryConfigs;
        }

        @Override
        public String toString() {
            return "CsvOrganizerConfig{" +
                    "columnNameToAliases=" + columnNameToAliases +
                    ", categoryConfigs=" + categoryConfigs +
                    ", defaultCategoryName='" + defaultCategoryName + '\'' +
                    ", sortColumnName='" + sortColumnName + '\'' +
                    ", sortType=" + sortType +
                    '}';
        }
    }

    public static class CsvOrganizerCategoryConfig {
        private String category;

        private String columnName;
        private Set<String> regexes;

        public CsvOrganizerCategoryConfig(String category, String columnName, Set<String> regexes) {
            this.category = category;
            this.columnName = columnName;
            this.regexes = regexes;
        }

        public String getCategory() {
            return category;
        }

        public String getColumnName() {
            return columnName;
        }

        public Set<String> getRegexes() {
            return regexes;
        }
    }

    public enum SortType{
        ASC, DESC;
    }

}
