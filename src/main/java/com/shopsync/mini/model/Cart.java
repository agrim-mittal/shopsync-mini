package com.shopsync.mini.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The shopping cart. Saved to data/cart.ser so it is still there next time.
 *
 * Concepts: Serializable, LinkedHashMap (keeps the order items were added),
 * TreeMap (category totals sorted A-Z), Map.getOrDefault, for-each loops.
 */
public class Cart implements Serializable {

    private static final long serialVersionUID = 1L;

    private final LinkedHashMap<String, CartItem> items = new LinkedHashMap<>();

    /** Adds the item, or increases the quantity if it is already in the cart. */
    public CartItem add(CartItem item) {
        CartItem existing = items.get(item.getKey());
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + item.getQuantity());
            return existing;
        }
        items.put(item.getKey(), item);
        return item;
    }

    public void updateQuantity(String key, int quantity) {
        find(key).setQuantity(quantity);
    }

    public CartItem remove(String key) {
        CartItem removed = items.remove(key);
        if (removed == null) {
            throw new IllegalArgumentException("That item is not in the cart");
        }
        return removed;
    }

    public CartItem find(String key) {
        CartItem item = items.get(key);
        if (item == null) {
            throw new IllegalArgumentException("That item is not in the cart");
        }
        return item;
    }

    public void clear()          { items.clear(); }
    public boolean isEmpty()     { return items.isEmpty(); }
    public List<CartItem> getItems() { return new ArrayList<>(items.values()); }

    public int getItemCount() {
        int count = 0;
        for (CartItem item : items.values()) {
            count += item.getQuantity();
        }
        return count;
    }

    public double getTotal() {
        double total = 0;
        for (CartItem item : items.values()) {
            total += item.getLineTotal();
        }
        return total;
    }

    /** Category name -> money in the cart for that category. */
    public Map<String, Double> totalsByCategory() {
        Map<String, Double> totals = new TreeMap<>();
        for (CartItem item : items.values()) {
            double soFar = totals.getOrDefault(item.getCategory(), 0.0);
            totals.put(item.getCategory(), soFar + item.getLineTotal());
        }
        return totals;
    }
}
