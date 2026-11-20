package com.shopsync.mini.exception;

/**
 * Checked exception for log-in and sign-up problems: wrong password,
 * username already taken, password too short, and so on.
 */
public class AuthException extends Exception {

    private static final long serialVersionUID = 1L;

    public AuthException(String message) {
        super(message);
    }
}
