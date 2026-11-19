package com.shopsync.mini.store;

/**
 * Abstract class (Lecture 4): common data for every store, but each real store
 * (Amazon, Flipkart, ...) decides its own delivery and discount rules.
 * You cannot write {@code new Store(...)}; you create a subclass instead.
 */
public abstract class Store implements PriceCalculator {

    private final String name;
    protected final double freeDeliveryAbove;   // protected: subclasses can read it
    protected final double deliveryCharge;

    protected Store(String name, double freeDeliveryAbove, double deliveryCharge) {
        this.name = name;
        this.freeDeliveryAbove = freeDeliveryAbove;
        this.deliveryCharge = deliveryCharge;
    }

    public String getName() { return name; }

    /** Same rule for all stores, so it is written once here (inheritance). */
    @Override
    public double deliveryFee(double listedPrice) {
        return listedPrice >= freeDeliveryAbove ? 0 : deliveryCharge;
    }

    /** Each store must describe its offer in its own words. */
    public abstract String offerNote();

    // extraDiscount(...) is still abstract (from the interface): subclasses must write it.
}
