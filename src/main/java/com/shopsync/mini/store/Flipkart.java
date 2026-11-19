package com.shopsync.mini.store;

/** Flipkart: 2% bank offer on Electronics (max Rs.1,000); free delivery above Rs.500. */
public class Flipkart extends Store {

    private static final double BANK_OFFER_CAP = 1000;

    public Flipkart() {
        super("Flipkart", 500, 40);
    }

    @Override
    public double extraDiscount(double listedPrice, String category) {
        if (!category.equalsIgnoreCase("Electronics")) {
            return 0;
        }
        return Math.min(listedPrice * 0.02, BANK_OFFER_CAP);
    }

    @Override
    public String offerNote() { return "2% bank offer on electronics (up to Rs.1,000)"; }
}
