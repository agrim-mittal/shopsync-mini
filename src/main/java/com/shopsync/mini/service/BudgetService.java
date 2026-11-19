package com.shopsync.mini.service;

import com.shopsync.mini.exception.BudgetExceededException;
import com.shopsync.mini.exception.ProductNotFoundException;
import com.shopsync.mini.model.Budget;
import com.shopsync.mini.model.BudgetReport;
import com.shopsync.mini.model.Cart;
import com.shopsync.mini.model.CartItem;
import com.shopsync.mini.model.ComparisonResult;
import com.shopsync.mini.model.Offer;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Cross-checks every item in the cart against the monthly budget.
 *
 * Concepts: iterating a Map with entrySet(), throwing a custom checked exception,
 * reusing another service (PriceComparisonService) to look for cheaper stores, logging.
 */
public class BudgetService {

    private static final Logger LOG = Logger.getLogger(BudgetService.class.getName());

    private final PriceComparisonService prices;

    public BudgetService(PriceComparisonService prices) {
        this.prices = prices;
    }

    /** Full check of the cart: totals, each category, and cheaper-store suggestions. */
    public BudgetReport check(Cart cart, Budget budget) {
        BudgetReport report = new BudgetReport(Budget.currentMonthLabel(), budget.getMonthlyLimit(), cart.getTotal());

        // 1. Each category in the cart vs its own limit (if one is set)
        for (Map.Entry<String, Double> entry : cart.totalsByCategory().entrySet()) {
            String category = entry.getKey();
            report.addCategory(new BudgetReport.CategoryLine(category, entry.getValue(),
                    budget.getCategoryLimit(category)));
        }

        // 2. Every cart item: is another store cheaper right now?
        for (CartItem item : cart.getItems()) {
            try {
                ComparisonResult result = prices.compareById(item.getProductId(), false);
                Offer cheapest = result.getCheapest();
                double saving = (item.getUnitPrice() - cheapest.getFinalPrice()) * item.getQuantity();
                if (!cheapest.getStore().getName().equals(item.getStoreName()) && saving >= 1) {
                    report.addSuggestion(new BudgetReport.Suggestion(item, cheapest.getStore().getName(), saving));
                }
            } catch (ProductNotFoundException e) {
                LOG.warning("Skipped " + item.getProductName() + ": " + e.getMessage());
            }
        }

        LOG.info(String.format("Budget check: Rs.%.0f of Rs.%.0f (%s)",
                report.getSpent(), report.getLimit(), report.getStatus().getLabel()));
        return report;
    }

    /**
     * Called before adding to the cart. Throws if the new item would cross
     * the monthly limit or the item's category limit.
     */
    public void ensureFits(Cart cart, Budget budget, CartItem newItem) throws BudgetExceededException {
        double newTotal = cart.getTotal() + newItem.getLineTotal();
        if (budget.hasMonthlyLimit() && newTotal > budget.getMonthlyLimit()) {
            throw new BudgetExceededException("monthly budget", newTotal - budget.getMonthlyLimit());
        }

        Double categoryLimit = budget.getCategoryLimit(newItem.getCategory());
        if (categoryLimit != null) {
            double categoryTotal = cart.totalsByCategory().getOrDefault(newItem.getCategory(), 0.0)
                    + newItem.getLineTotal();
            if (categoryTotal > categoryLimit) {
                throw new BudgetExceededException(newItem.getCategory() + " limit", categoryTotal - categoryLimit);
            }
        }
    }
}
