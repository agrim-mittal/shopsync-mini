package com.shopsync.mini.data;

import com.shopsync.mini.model.ComparisonResult;
import com.shopsync.mini.model.Offer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Writes a comparison to a .txt report (Lecture 8: writing files).
 * Concepts: FileWriter + BufferedWriter + PrintWriter, creating folders, "throws".
 */
public class ReportWriter {

    private final File folder;

    public ReportWriter(File folder) {
        this.folder = folder;
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    public File write(ComparisonResult result) throws IOException {
        File out = new File(folder, "comparison-" + result.getProduct().getId() + ".txt");

        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(out)))) {
            pw.println("ShopSync Mini - Price comparison report");
            pw.println("=======================================");
            pw.println(result.getProduct());
            pw.println();
            pw.printf("%-10s %12s %10s %10s %12s%n", "Store", "Listed", "Discount", "Delivery", "You pay");
            for (Offer o : result.getOffers()) {
                pw.printf("%-10s %12.0f %10.0f %10.0f %12.0f%n", o.getStore().getName(),
                        o.getListedPrice(), o.getExtraDiscount(), o.getDeliveryFee(), o.getFinalPrice());
            }
            pw.println();
            pw.println(result.summarise());
        }
        return out;
    }
}
