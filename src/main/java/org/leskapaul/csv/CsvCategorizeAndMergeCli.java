package org.leskapaul.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.leskapaul.csv.DateTransformerConfig.extractDateTransformerConfig;

public class CsvCategorizeAndMergeCli {

    private static final Logger LOG = LogManager.getLogger(CsvCategorizeAndMergeCli.class);

    public static void main(String[] args) {
        LOG.info("This program expects the following arguments: <path to yaml config> <one or more input csv files, separated by a space> [--output-file <output file path>] [--web-chart]");
        LOG.debug("called with args: {}", Stream.of(args).collect(Collectors.toList()));

        // Parse arguments to identify flags and their values
        Map<String, String> flagsWithValues = new HashMap<>();
        List<String> positionalArgs = new ArrayList<>();
        
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                // This is a flag
                String flag = args[i];
                
                // Check if this flag expects a value
                if ("--output-file".equals(flag) && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    flagsWithValues.put(flag, args[i + 1]);
                    i++; // Skip the next argument as it's the value for this flag
                } else {
                    // Flag without value
                    flagsWithValues.put(flag, "");
                }
            } else {
                // This is a positional argument
                positionalArgs.add(args[i]);
            }
        }
        
        // Check for minimum required arguments
        if (positionalArgs.size() < 2) {
            LOG.error("This program requires at least a config file and one input CSV file");
            return;
        }
        
        // Extract flags
        boolean includeWebChartHeaders = flagsWithValues.containsKey("--web-chart");
        String outputFilePath = flagsWithValues.get("--output-file");
        
        // Load config
        CsvCategorizeAndMerge.CsvOrganizerConfig config;
        try {
            config = loadConfig(new FileInputStream(positionalArgs.get(0)));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("failed to load config file " + positionalArgs.get(0), e);
        }
        
        // Parse input CSV files (all positional args except the first one, which is the config)
        List<CSVParser> csvParsers = new ArrayList<>();
        for (int i = 1; i < positionalArgs.size(); i++) {
            try {
                CSVParser csvParser = CSVParser.parse(new FileInputStream(positionalArgs.get(i)),
                        StandardCharsets.UTF_8, CSVFormat.DEFAULT.withFirstRecordAsHeader());
                csvParsers.add(csvParser);
            } catch (IOException e) {
                throw new RuntimeException("failed to load csv file " + positionalArgs.get(i), e);
            }
        }
        
        try {
            // Process the input files
            List<CsvCategorizeAndMerge.CategoryCsvLines> lines =
                    new CsvCategorizeAndMerge().organizeCsvLines(csvParsers, config);
            
            // If an output file is specified and exists, merge with it
            if (outputFilePath != null) {
                File outputFile = new File(outputFilePath);
                if (outputFile.exists() && outputFile.length() > 0) {
                    LOG.info("Merging with existing output file: {}", outputFilePath);
                    lines = mergeWithExistingFile(outputFilePath, lines, config);
                }
                
                // Write to the output file
                writeToFile(outputFilePath, config, lines, includeWebChartHeaders);
                LOG.info("Output written to file: {}", outputFilePath);
            } else {
                // Print to console as before
                printCsv(config, lines, includeWebChartHeaders);
            }
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
    
    /**
     * Merges new transaction data with existing data from a file, removing duplicates
     */
    private static List<CsvCategorizeAndMerge.CategoryCsvLines> mergeWithExistingFile(
            String filePath, 
            List<CsvCategorizeAndMerge.CategoryCsvLines> newLines,
            CsvCategorizeAndMerge.CsvOrganizerConfig config) {
        
        try {
            // Read all lines from the file
            List<String> allLines = Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8);
            
            // Create a map to track unique transactions
            Set<String> uniqueTransactionKeys = new HashSet<>();
            Map<String, List<Map<String, String>>> categoryToTransactions = new HashMap<>();
            
            // Process new lines first and add them to our tracking structures
            for (CsvCategorizeAndMerge.CategoryCsvLines categoryLines : newLines) {
                String category = categoryLines.getCategoryName();
                
                // Initialize category in map if needed
                categoryToTransactions.computeIfAbsent(category, k -> new ArrayList<>());
                
                // Add each transaction with deduplication
                for (Map<String, String> transaction : categoryLines.getCsvLines()) {
                    String transactionKey = generateTransactionKey(transaction);
                    if (!uniqueTransactionKeys.contains(transactionKey)) {
                        uniqueTransactionKeys.add(transactionKey);
                        categoryToTransactions.get(category).add(transaction);
                    }
                }
            }
            
            // Extract header and data lines, removing category markers
            if (allLines.isEmpty()) {
                return newLines; // Empty file, just return new lines
            }
            
            String headerLine = allLines.get(0);
            StringBuilder csvContent = new StringBuilder(headerLine).append("\n");
            
            String currentCategory = config.getDefaultCategoryName();
            Map<Integer, String> lineToCategory = new HashMap<>(); // Track which line belongs to which category
            int dataLineIndex = 0;
            
            // Process the remaining lines, skipping category headers
            for (int i = 1; i < allLines.size(); i++) {
                String line = allLines.get(i);
                
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                // Check if this is a category header line
                if (line.trim().startsWith("[category=") && line.trim().endsWith("]")) {
                    // Extract category name
                    currentCategory = line.trim().substring(10, line.trim().length() - 1);
                    continue;
                }
                
                // This is a data line
                csvContent.append(line).append("\n");
                lineToCategory.put(dataLineIndex++, currentCategory);
            }
            
            // Parse the CSV content without category headers
            StringReader reader = new StringReader(csvContent.toString());
            CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader());
            
            // Process existing transactions
            int lineIndex = 0;
            for (CSVRecord record : parser) {
                Map<String, String> transaction = record.toMap();
                String category = lineToCategory.getOrDefault(lineIndex++, config.getDefaultCategoryName());
                
                String transactionKey = generateTransactionKey(transaction);
                if (!uniqueTransactionKeys.contains(transactionKey)) {
                    // Add to the appropriate category
                    categoryToTransactions.computeIfAbsent(category, k -> new ArrayList<>());
                    categoryToTransactions.get(category).add(transaction);
                    uniqueTransactionKeys.add(transactionKey);
                }
            }
            
            // Convert back to the expected format
            List<CsvCategorizeAndMerge.CategoryCsvLines> mergedLines = new ArrayList<>();
            for (String category : categoryToTransactions.keySet()) {
                List<Map<String, String>> transactions = categoryToTransactions.get(category);
                if (!transactions.isEmpty()) {
                    mergedLines.add(new CsvCategorizeAndMerge.CategoryCsvLines(category, transactions));
                }
            }
            
            // Sort the merged data
            for (CsvCategorizeAndMerge.CategoryCsvLines categoryLines : mergedLines) {
                sortCategoryLines(categoryLines, config);
            }
            
            return mergedLines;
            
        } catch (IOException e) {
            LOG.error("Error reading existing file for merge: {}", filePath, e);
            // If there's an error, just return the new lines
            return newLines;
        }
    }
    
    /**
     * Creates an in-memory CSV parser from a list of transactions
     */
    private static List<CSVParser> createInMemoryCsvParser(List<Map<String, String>> transactions, 
                                                          CsvCategorizeAndMerge.CsvOrganizerConfig config) {
        try {
            // Create a temporary CSV string
            StringBuilder csvBuilder = new StringBuilder();
            
            // Add header row
            List<String> columnNames = new ArrayList<>(config.getColumnNameToAliases().keySet());
            for (int i = 0; i < columnNames.size(); i++) {
                if (i > 0) csvBuilder.append(",");
                csvBuilder.append(columnNames.get(i));
            }
            csvBuilder.append("\n");
            
            // Add data rows
            for (Map<String, String> transaction : transactions) {
                for (int i = 0; i < columnNames.size(); i++) {
                    if (i > 0) csvBuilder.append(",");
                    String value = transaction.get(columnNames.get(i));
                    csvBuilder.append(value == null ? "" : value);
                }
                csvBuilder.append("\n");
            }
            
            // Create parser from the string
            StringReader reader = new StringReader(csvBuilder.toString());
            CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader());
            
            return Collections.singletonList(parser);
        } catch (IOException e) {
            LOG.error("Error creating in-memory CSV parser", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Generates a unique key for a transaction to use for deduplication
     */
    private static String generateTransactionKey(Map<String, String> transaction) {
        // Create a key that combines all fields to identify unique transactions
        StringBuilder key = new StringBuilder();
        List<String> sortedKeys = new ArrayList<>(transaction.keySet());
        Collections.sort(sortedKeys);
        
        for (String field : sortedKeys) {
            String value = transaction.get(field);
            if (value != null) {
                key.append(field).append("=").append(value).append("|");
            }
        }
        
        return key.toString();
    }
    
    /**
     * Sorts transactions within a category based on the configuration
     */
    private static void sortCategoryLines(CsvCategorizeAndMerge.CategoryCsvLines categoryLines,
                                         CsvCategorizeAndMerge.CsvOrganizerConfig config) {
        categoryLines.getCsvLines().sort((map1, map2) -> {
            if (CsvCategorizeAndMerge.SortType.DESC.equals(config.getSortType())) {
                Map<String, String> temp = map2;
                map2 = map1;
                map1 = temp;
            }
            
            String val1 = map1.getOrDefault(config.getSortColumnName(), "");
            String val2 = map2.getOrDefault(config.getSortColumnName(), "");
            
            return val1.compareTo(val2);
        });
    }
    
    /**
     * Writes the categorized data to a file
     */
    private static void writeToFile(String filePath, 
                                   CsvCategorizeAndMerge.CsvOrganizerConfig config,
                                   List<CsvCategorizeAndMerge.CategoryCsvLines> lines,
                                   boolean includeWebChartHeaders) {
        try (FileWriter writer = new FileWriter(filePath)) {
            StringBuilder sb = new StringBuilder();
            
            // Write header
            List<String> columnNamesInOrder = new ArrayList<>(config.getColumnNameToAliases().keySet());
            for (int i = 0; i < columnNamesInOrder.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(columnNamesInOrder.get(i));
            }
            sb.append('\n');
            writer.write(sb.toString());
            
            // Write data
            for (CsvCategorizeAndMerge.CategoryCsvLines categoryCsvLines : lines) {
                // Add category header if web chart flag is enabled
                if (includeWebChartHeaders) {
                    writer.write("[category=" + categoryCsvLines.getCategoryName() + "]\n");
                }
                
                for (Map<String, String> csvRowAsMap : categoryCsvLines.getCsvLines()) {
                    sb = new StringBuilder();
                    for (int i = 0; i < columnNamesInOrder.size(); i++) {
                        if (i > 0) sb.append(",");
                        String columnName = columnNamesInOrder.get(i);
                        String cellValue = csvRowAsMap.get(columnName);
                        sb.append(cellValue == null ? "" : cellValue);
                    }
                    sb.append('\n');
                    writer.write(sb.toString());
                }
                writer.write("\n"); // Empty line between categories
            }
            
        } catch (IOException e) {
            LOG.error("Error writing to output file: {}", filePath, e);
            throw new RuntimeException("Failed to write to output file", e);
        }
    }
    
    public static void printCsv(CsvCategorizeAndMerge.CsvOrganizerConfig config,
                                List<CsvCategorizeAndMerge.CategoryCsvLines> lines,
                                boolean includeWebChartHeaders) {
        StringBuilder sb = new StringBuilder();

        List<String> columnNamesInOrder = new ArrayList<>(config.getColumnNameToAliases().keySet());
        columnNamesInOrder.forEach(columnName -> {
           if (sb.length() > 0) { sb.append(", "); }
           sb.append(columnName);
        });
        sb.append('\n');

        lines.forEach(categoryCsvLines -> {
            // Add category header if web chart flag is enabled
            if (includeWebChartHeaders) {
                sb.append("[category=").append(categoryCsvLines.getCategoryName()).append("]\n");
            }
            
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
