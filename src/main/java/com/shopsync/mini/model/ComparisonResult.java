package com.shopsync.mini.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of comparing one product across stores.
 *
 * Concepts: static nested class ({@link Summary}), ArrayList,
 * Collections.sort / Collections.unmodifiableList.
 */
public class ComparisonResult {

    private final Product product;
    private final List<Offer> offers;

    public ComparisonResult(Product product, List<Offer> offers) {
        this.product = product;
        this.offers = new ArrayList<>(offers);
        Collections.sort(this.offers);              // uses Offer.compareTo -> cheapest first
    }

    public Product getProduct()   { return product; }
    public List<Offer> getOffers() { return Collections.unmodifiableList(offers); }
    public Offer getCheapest()    { return offers.get(0); }
    public Offer getCostliest()   { return offers.get(offers.size() - 1); }

    public Summary summarise() {
        return new Summary(getCheapest(), getCostliest());
    }

    /**
     * Static nested class (Lecture 4): it belongs with ComparisonResult,
     * but does not need an object of the outer class to be created.
     */
    public static class Summary {
        private final String bestStore;
        private final double bestPrice;
        private final double youSave;      // vs the costliest store

        Summary(Offer cheapest, Offer costliest) {
            this.bestStore = cheapest.getStore().getName();
            this.bestPrice = cheapest.getFinalPrice();
            this.youSave = costliest.getFinalPrice() - cheapest.getFinalPrice();
        }

        public String getBestStore() { return bestStore; }
        public double getBestPrice() { return bestPrice; }
        public double getYouSave()   { return youSave; }

        @Override
        public String toString() {
            return String.format("Best deal: %s at Rs.%,.0f (save Rs.%,.0f vs the costliest store)",
                    bestStore, bestPrice, youSave);
        }
    }
}
