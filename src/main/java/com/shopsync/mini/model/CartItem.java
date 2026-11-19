package com.shopsync.mini.model;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * One line in the cart: a product, the store you chose, and how many.
 *
 * Concepts: Serializable (only simple fields, so the whole object can be saved),
 * constructor overloading, encapsulation (quantity can only change through a checked setter).
 */
public class CartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String productId;
    private final String productName;
    private final String category;
    private final String storeName;
    private final double unitPrice;       // final price at that store, saved at the time of adding
    private final LocalDate addedOn;
    private int quantity;

    public CartItem(Offer offer, int quantity) {
        this(offer.getProduct().getId(), offer.getProduct().getName(), offer.getProduct().getCategory(),
                offer.getStore().getName(), offer.getFinalPrice(), quantity);
    }

    public CartItem(String productId, String productName, String category,
                    String storeName, double unitPrice, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.category = category;
        this.storeName = storeName;
        this.unitPrice = unitPrice;
        this.addedOn = LocalDate.now();
        setQuantity(quantity);
    }

    /** Same product from the same store = same key, so it is stored only once. */
    public String getKey() { return productId + "@" + storeName; }

    public String getProductId()   { return productId; }
    public String getProductName() { return productName; }
    public String getCategory()    { return category; }
    public String getStoreName()   { return storeName; }
    public double getUnitPrice()   { return unitPrice; }
    public LocalDate getAddedOn()  { return addedOn; }
    public int getQuantity()       { return quantity; }
    public double getLineTotal()   { return unitPrice * quantity; }

    public void setQuantity(int quantity) {
        if (quantity < 1 || quantity > 20) {
            throw new IllegalArgumentException("Quantity must be between 1 and 20");
        }
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return String.format("%s (%s) x%d = Rs.%,.0f", productName, storeName, quantity, getLineTotal());
    }
}
