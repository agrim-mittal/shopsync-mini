package com.shopsync.mini.model;

import com.shopsync.mini.store.Store;

/**
 * The price of one product at one store.
 *
 * Concepts: has-a relationship (an Offer has a Product and a Store),
 * implementing the built-in interface {@code Comparable} so that
 * {@code Collections.sort(list)} orders offers cheapest first.
 */
public class Offer implements Comparable<Offer> {

    private final Product product;
    private final Store store;
    private final double listedPrice;   // price shown on the store page

    public Offer(Product product, Store store, double listedPrice) {
        this.product = product;
        this.store = store;
        this.listedPrice = listedPrice;
    }

    /** Final amount you pay: the store decides delivery and extra discounts (polymorphism). */
    public double getFinalPrice()  { return store.finalPrice(listedPrice, product.getCategory()); }
    public double getDeliveryFee() { return store.deliveryFee(listedPrice); }
    public double getExtraDiscount() { return store.extraDiscount(listedPrice, product.getCategory()); }

    public Product getProduct()    { return product; }
    public Store getStore()        { return store; }
    public double getListedPrice() { return listedPrice; }

    /** Natural ordering = cheapest final price first. */
    @Override
    public int compareTo(Offer other) {
        return Double.compare(this.getFinalPrice(), other.getFinalPrice());
    }

    @Override
    public String toString() {
        return String.format("%-9s listed Rs.%,.0f  ->  you pay Rs.%,.0f",
                store.getName(), listedPrice, getFinalPrice());
    }
}
