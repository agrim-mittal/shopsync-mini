package com.shopsync.mini.store;

/** Inheritance: Amazon IS-A Store. Free delivery above Rs.499; 5% off Books. */
public class Amazon extends Store {

    public Amazon() {
        super("Amazon", 499, 40);       // calls the Store constructor
    }

    @Override
    public double extraDiscount(double listedPrice, String category) {
        return category.equalsIgnoreCase("Books") ? listedPrice * 0.05 : 0;
    }

    @Override
    public String offerNote() { return "5% off on books, free delivery above Rs.499"; }
}
