package com.shopsync.mini.exception;

/**
 * Custom CHECKED exception (Lecture 6): extends Exception, so the compiler forces
 * callers to either catch it or declare it with "throws".
 */
public class ProductNotFoundException extends Exception {

    private static final long serialVersionUID = 1L;

    public ProductNotFoundException(String query) {
        super("No product found for \"" + query + "\"");
    }
}
