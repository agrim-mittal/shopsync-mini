package com.shopsync.mini.model;

/**
 * One product that can be sold by several stores.
 *
 * Concepts: class, private fields (encapsulation), constructor overloading,
 * the {@code this(...)} call, getters and overriding {@code toString()}.
 */
public class Product {

    private final String id;
    private final String name;
    private final String category;
    private final double mrp;

    // Constructor overloading (Lecture 3): full version...
    public Product(String id, String name, String category, double mrp) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.mrp = mrp;
    }

    // ...and a shorter version that calls the first one with this(...)
    public Product(String id, String name, double mrp) {
        this(id, name, "General", mrp);
    }

    public String getId()       { return id; }
    public String getName()     { return name; }
    public String getCategory() { return category; }
    public double getMrp()      { return mrp; }

    @Override
    public String toString() {
        return name + " [" + category + "] MRP Rs." + (long) mrp;
    }
}
