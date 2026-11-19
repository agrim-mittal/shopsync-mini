package com.shopsync.mini.model;

import java.io.Serializable;
import java.time.YearMonth;
import java.util.Map;
import java.util.TreeMap;

/**
 * Your monthly spending limit, plus optional limits per category.
 * Saved to data/budget.ser.
 *
 * Concepts: Serializable, TreeMap, validation with IllegalArgumentException.
 */
public class Budget implements Serializable {

    private static final long serialVersionUID = 1L;

    private double monthlyLimit;                                   // 0 = not set yet
    private final TreeMap<String, Double> categoryLimits = new TreeMap<>();

    public void setMonthlyLimit(double limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Budget must be more than 0");
        }
        this.monthlyLimit = limit;
    }

    /** A limit of 0 removes the category limit. */
    public void setCategoryLimit(String category, double limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Limit cannot be negative");
        }
        if (limit == 0) {
            categoryLimits.remove(category);
        } else {
            categoryLimits.put(category, limit);
        }
    }

    public double getMonthlyLimit()            { return monthlyLimit; }
    public boolean hasMonthlyLimit()           { return monthlyLimit > 0; }
    public Map<String, Double> getCategoryLimits() { return categoryLimits; }
    public Double getCategoryLimit(String category) { return categoryLimits.get(category); }   // null if none

    /** e.g. "November 2026" */
    public static String currentMonthLabel() {
        YearMonth now = YearMonth.now();
        String month = now.getMonth().toString();
        return month.charAt(0) + month.substring(1).toLowerCase() + " " + now.getYear();
    }
}
