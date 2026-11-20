package com.shopsync.mini.model;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * A ShopSync account. Saved (with all other accounts) to data/users.ser.
 *
 * Concepts: Serializable, encapsulation (no getter ever returns the password;
 * only a salted hash is stored), final fields.
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String username;
    private final String displayName;
    private final String salt;           // random text mixed into the password before hashing
    private final String passwordHash;   // SHA-256 of salt + password, never the password itself
    private final LocalDate joinedOn;

    public User(String username, String displayName, String salt, String passwordHash) {
        this.username = username;
        this.displayName = displayName;
        this.salt = salt;
        this.passwordHash = passwordHash;
        this.joinedOn = LocalDate.now();
    }

    public String getUsername()    { return username; }
    public String getDisplayName() { return displayName; }
    public LocalDate getJoinedOn() { return joinedOn; }

    // Used only by AccountService to check a password.
    public String getSalt()         { return salt; }
    public String getPasswordHash() { return passwordHash; }

    @Override
    public String toString() {
        return displayName + " (@" + username + ")";
    }
}
