package com.shopsync.mini.store;

/**
 * Interface (Lecture 4): a contract saying "anything that can work out a final price".
 * Every Store must provide these methods.
 */
public interface PriceCalculator {

    double deliveryFee(double listedPrice);

    double extraDiscount(double listedPrice, String category);

    /** Default method: the same formula for every store, built from the two methods above. */
    default double finalPrice(double listedPrice, String category) {
        return listedPrice - extraDiscount(listedPrice, category) + deliveryFee(listedPrice);
    }
}
