package com.shopsync.mini.store;

/** Croma: always free delivery, flat Rs.500 off electronics above Rs.20,000. */
public class Croma extends Store {

    public Croma() {
        super("Croma", 0, 0);
    }

    @Override
    public double extraDiscount(double listedPrice, String category) {
        boolean bigElectronics = category.equalsIgnoreCase("Electronics") && listedPrice > 20000;
        return bigElectronics ? 500 : 0;
    }

    @Override
    public String offerNote() { return "Free delivery, Rs.500 off electronics above Rs.20,000"; }
}
