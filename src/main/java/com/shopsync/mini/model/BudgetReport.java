package com.shopsync.mini.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The answer to "does my cart fit my budget?".
 *
 * Concepts: static nested classes (CategoryLine, Suggestion), ArrayList, enum field.
 */
public class BudgetReport {

    private final String month;
    private final double limit;
    private final double spent;
    private final BudgetStatus status;
    private final List<CategoryLine> categories = new ArrayList<>();
    private final List<Suggestion> suggestions = new ArrayList<>();

    public BudgetReport(String month, double limit, double spent) {
        this.month = month;
        this.limit = limit;
        this.spent = spent;
        this.status = limit > 0 ? BudgetStatus.fromPercent(getPercentUsed()) : BudgetStatus.NO_BUDGET;
    }

    public void addCategory(CategoryLine line)  { categories.add(line); }
    public void addSuggestion(Suggestion s)     { suggestions.add(s); }

    public String getMonth()        { return month; }
    public double getLimit()        { return limit; }
    public double getSpent()        { return spent; }
    public double getRemaining()    { return limit - spent; }          // negative = over
    public double getPercentUsed()  { return limit > 0 ? spent * 100 / limit : 0; }
    public BudgetStatus getStatus() { return status; }
    public List<CategoryLine> getCategories() { return categories; }
    public List<Suggestion> getSuggestions()  { return suggestions; }

    public double getPossibleSavings() {
        double total = 0;
        for (Suggestion s : suggestions) {
            total += s.getSaving();
        }
        return total;
    }

    /** Spending in one category, compared with that category's own limit (if any). */
    public static class CategoryLine {
        private final String category;
        private final double spent;
        private final Double limit;          // wrapper class: can be null when no limit is set

        public CategoryLine(String category, double spent, Double limit) {
            this.category = category;
            this.spent = spent;
            this.limit = limit;
        }

        public String getCategory() { return category; }
        public double getSpent()    { return spent; }
        public Double getLimit()    { return limit; }
        public boolean isOver()     { return limit != null && spent > limit; }
    }

    /** "Buy X from a cheaper store and save Rs.Y". */
    public static class Suggestion {
        private final String itemKey;
        private final String productId;
        private final String productName;
        private final String fromStore;
        private final String toStore;
        private final double saving;

        public Suggestion(CartItem item, String toStore, double saving) {
            this.itemKey = item.getKey();
            this.productId = item.getProductId();
            this.productName = item.getProductName();
            this.fromStore = item.getStoreName();
            this.toStore = toStore;
            this.saving = saving;
        }

        public String getItemKey()     { return itemKey; }
        public String getProductId()   { return productId; }
        public String getProductName() { return productName; }
        public String getFromStore()   { return fromStore; }
        public String getToStore()     { return toStore; }
        public double getSaving()      { return saving; }

        @Override
        public String toString() {
            return String.format("Buy %s from %s instead of %s: save Rs.%,.0f",
                    productName, toStore, fromStore, saving);
        }
    }
}
