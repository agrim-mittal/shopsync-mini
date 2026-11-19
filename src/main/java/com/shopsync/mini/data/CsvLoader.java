package com.shopsync.mini.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Reads the price list from a CSV text file.
 *
 * Concepts: File handling (Lecture 8) with FileReader + BufferedReader,
 * try-with-resources (the file closes itself), String.split into an array,
 * skipping bad lines with try-catch (NumberFormatException).
 */
public class CsvLoader {

    private static final Logger LOG = Logger.getLogger(CsvLoader.class.getName());

    /** One line of the CSV, kept as plain fields. */
    public static class Row {               // static nested class
        public final String productId, name, category, store;
        public final double mrp, listedPrice;

        Row(String[] cols) {
            productId   = cols[0].trim();
            name        = cols[1].trim();
            category    = cols[2].trim();
            mrp         = Double.parseDouble(cols[3].trim());
            store       = cols[4].trim();
            listedPrice = Double.parseDouble(cols[5].trim());
        }
    }

    public List<Row> load(File file) throws IOException {
        List<Row> rows = new ArrayList<>();
        int lineNo = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;                                   // comment or empty line
                }
                String[] cols = line.split(",");
                if (cols.length != 6) {
                    LOG.warning("Line " + lineNo + " skipped: expected 6 columns");
                    continue;
                }
                try {
                    rows.add(new Row(cols));
                } catch (NumberFormatException e) {
                    LOG.warning("Line " + lineNo + " skipped: bad number (" + e.getMessage() + ")");
                }
            }
        }
        LOG.info("Read " + rows.size() + " price rows from " + file.getName());
        return rows;
    }
}
