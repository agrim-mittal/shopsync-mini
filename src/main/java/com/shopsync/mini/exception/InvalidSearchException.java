package com.shopsync.mini.exception;

/**
 * Custom UNCHECKED exception (Lecture 6): extends RuntimeException, used for
 * programming/input mistakes like an empty search box.
 */
public class InvalidSearchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidSearchException(String message) {
        super(message);
    }
}
