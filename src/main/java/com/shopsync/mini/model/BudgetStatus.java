package com.shopsync.mini.model;

/**
 * Enum: a fixed set of named constants, each with its own label.
 * Concepts: enum with a constructor and field, a static method on the enum.
 */
public enum BudgetStatus {

    NO_BUDGET("No budget set"),
    SAFE("On track"),
    NEAR_LIMIT("Close to the limit"),
    OVER("Over budget");

    private final String label;

    BudgetStatus(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }

    /** 80% or more of the limit counts as "close". */
    public static BudgetStatus fromPercent(double percentUsed) {
        if (percentUsed > 100) return OVER;
        if (percentUsed >= 80) return NEAR_LIMIT;
        return SAFE;
    }
}
