package org.leskapaul.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

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
        CsvCategorizeAndMerge.CsvOrganizerConfig config = CsvCategorizeAndMergeCli.loadConfig("/testConfig.yaml");

        try (CSVParser csvParser = CSVParser.parse(getClass().getResourceAsStream("/testCsv.csv"),
                StandardCharsets.UTF_8, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            List<CsvCategorizeAndMerge.CategoryCsvLines> lines =
                    csvOrganizingTransformer.organizeCsvLines(Collections.singletonList(csvParser), config);
            CsvCategorizeAndMergeCli.printCsv(config, lines);
            LOG.info("result has lines spanning {} categories: {}", lines.size(), lines);

            assertEquals("expected lines spanning 2 categories", 2, lines.size());
            assertEquals("expected 3 lines for groceries", 3,
                    countLinesForCategory(lines, "Groceries"));
            assertEquals("expected 2 lines for discretionary", 2,
                    countLinesForCategory(lines, "Discretionary"));
        }

    }


    private long countLinesForCategory(List<CsvCategorizeAndMerge.CategoryCsvLines> lines, String category) {
        return lines.stream().filter(categoryCsvLines -> categoryCsvLines.getCategoryName().equals(category))
                .map(CsvCategorizeAndMerge.CategoryCsvLines::getCsvLines)
                .mapToLong(List::size).sum();
    }

}
