package com.shopsync.mini.exception;

/**
 * Checked exception thrown when adding an item would push the cart past a limit.
 * It carries extra data (how much over) that the UI can show.
 */
public class BudgetExceededException extends Exception {

    private static final long serialVersionUID = 1L;

    private final double overBy;

    public BudgetExceededException(String whichLimit, double overBy) {
        super(String.format("This would put you Rs.%,.0f over your %s", overBy, whichLimit));
        this.overBy = overBy;
    }

    public double getOverBy() { return overBy; }
}
