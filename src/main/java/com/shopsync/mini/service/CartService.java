package com.shopsync.mini.service;

import com.shopsync.mini.data.SerialStore;
import com.shopsync.mini.exception.BudgetExceededException;
import com.shopsync.mini.exception.ProductNotFoundException;
import com.shopsync.mini.model.Budget;
import com.shopsync.mini.model.BudgetReport;
import com.shopsync.mini.model.Cart;
import com.shopsync.mini.model.CartItem;
import com.shopsync.mini.model.Offer;

import java.util.logging.Logger;

/**
 * Everything the user can do with the cart and budget. Both UIs call this class.
 * After every change the cart/budget is serialized to disk.
 *
 * Concepts: method overloading (addToCart x3), checked exceptions passed up with
 * "throws", generics (SerialStore<Cart>), logging.
 */
public class CartService {

    private static final Logger LOG = Logger.getLogger(CartService.class.getName());

    private final PriceComparisonService prices;
    private final BudgetService budgetService;
    private final SerialStore<Cart> cartStore;
    private final SerialStore<Budget> budgetStore;
    private final Cart cart;
    private final Budget budget;

    public CartService(PriceComparisonService prices, BudgetService budgetService,
                       SerialStore<Cart> cartStore, SerialStore<Budget> budgetStore) {
        this.prices = prices;
        this.budgetService = budgetService;
        this.cartStore = cartStore;
        this.budgetStore = budgetStore;
        this.cart = cartStore.load(new Cart());
        this.budget = budgetStore.load(new Budget());
    }

    public Cart getCart()     { return cart; }
    public Budget getBudget() { return budget; }

    // ------------------------------------------------------------ adding (overloaded)

    /** Add one of this offer; stops if it breaks the budget. */
    public CartItem addToCart(Offer offer) throws BudgetExceededException {
        return addToCart(offer, 1, false);
    }

    /** Add from an offer. allowOverBudget = true means "add it anyway". */
    public CartItem addToCart(Offer offer, int quantity, boolean allowOverBudget) throws BudgetExceededException {
        CartItem item = new CartItem(offer, quantity);
        if (!allowOverBudget) {
            budgetService.ensureFits(cart, budget, item);      // may throw
        }
        CartItem saved = cart.add(item);
        cartStore.save(cart);
        LOG.info("Added to cart: " + item);
        return saved;
    }

    /** Add by product id + store name (what the website sends). */
    public CartItem addToCart(String productId, String storeName, int quantity, boolean allowOverBudget)
            throws ProductNotFoundException, BudgetExceededException {
        for (Offer offer : prices.compareById(productId, false).getOffers()) {
            if (offer.getStore().getName().equalsIgnoreCase(storeName)) {
                return addToCart(offer, quantity, allowOverBudget);
            }
        }
        throw new IllegalArgumentException(storeName + " does not sell this product");
    }

    // ------------------------------------------------------------ changing the cart

    public void changeQuantity(String key, int quantity) {
        cart.updateQuantity(key, quantity);
        cartStore.save(cart);
    }

    public CartItem remove(String key) {
        CartItem removed = cart.remove(key);
        cartStore.save(cart);
        LOG.info("Removed from cart: " + removed.getProductName());
        return removed;
    }

    public void clearCart() {
        cart.clear();
        cartStore.save(cart);
    }

    /** Swap an item to a cheaper store (used by the budget suggestions). */
    public CartItem switchStore(String key, String newStore) throws ProductNotFoundException {
        CartItem old = cart.find(key);
        try {
            CartItem added = addToCart(old.getProductId(), newStore, old.getQuantity(), true);
            remove(key);
            return added;
        } catch (BudgetExceededException e) {
            throw new IllegalStateException("Not possible: budget check was skipped", e);   // never happens
        }
    }

    // ------------------------------------------------------------ budget

    public void setMonthlyBudget(double amount) {
        budget.setMonthlyLimit(amount);
        budgetStore.save(budget);
        LOG.info("Monthly budget set to Rs." + amount);
    }

    public void setCategoryLimit(String category, double amount) {
        budget.setCategoryLimit(category, amount);
        budgetStore.save(budget);
    }

    public BudgetReport checkBudget() {
        return budgetService.check(cart, budget);
    }
}
