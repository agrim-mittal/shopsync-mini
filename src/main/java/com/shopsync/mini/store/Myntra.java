package com.shopsync.mini.store;

/** Myntra: extra 10% off Fashion; delivery Rs.79 below Rs.799. */
public class Myntra extends Store {

    public Myntra() {
        super("Myntra", 799, 79);
    }

    @Override
    public double extraDiscount(double listedPrice, String category) {
        return category.equalsIgnoreCase("Fashion") ? listedPrice * 0.10 : 0;
    }

    @Override
    public String offerNote() { return "Extra 10% off on fashion"; }
}
