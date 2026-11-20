package com.shopsync.mini.service;

import com.shopsync.mini.data.SerialStore;
import com.shopsync.mini.exception.AuthException;
import com.shopsync.mini.model.Budget;
import com.shopsync.mini.model.Cart;
import com.shopsync.mini.model.User;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Sign-up, log-in, and one CartService per user (so every user has their own
 * cart and budget).
 *
 * Concepts: HashMap of users saved with serialization (SerialStore), checked
 * AuthException, String validation with matches(), method overloading
 * (cartFor ×2), logging. Passwords are salted and hashed with SHA-256
 * (java.security.MessageDigest), so the real password is never stored.
 */
public class AccountService {

    private static final Logger LOG = Logger.getLogger(AccountService.class.getName());

    public static final String DEMO_USER = "demo";
    public static final String DEMO_PASSWORD = "demo123";

    private final PriceComparisonService prices;
    private final BudgetService budgetService;
    private final File usersFolder;
    private final SerialStore<HashMap<String, User>> userStore;
    private final HashMap<String, User> users;                     // username -> account
    private final Map<String, CartService> carts = new HashMap<>(); // username -> that user's cart
    private final SecureRandom random = new SecureRandom();

    public AccountService(PriceComparisonService prices, BudgetService budgetService, File dataDir) {
        this.prices = prices;
        this.budgetService = budgetService;
        this.usersFolder = new File(dataDir, "users");
        this.usersFolder.mkdirs();
        this.userStore = new SerialStore<>(new File(dataDir, "users.ser"));
        this.users = userStore.load(new HashMap<>());

        if (!users.containsKey(DEMO_USER)) {               // ready-made account for demos
            try {
                register(DEMO_USER, DEMO_PASSWORD, "Demo User");
            } catch (AuthException e) {
                LOG.warning("Could not create demo account: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------ sign up / log in

    public User register(String username, String password, String displayName) throws AuthException {
        String name = clean(username);
        if (!name.matches("[a-z0-9_]{3,20}")) {
            throw new AuthException("Username must be 3–20 letters, numbers or _");
        }
        if (password == null || password.length() < 6) {
            throw new AuthException("Password must be at least 6 characters");
        }
        if (users.containsKey(name)) {
            throw new AuthException("That username is already taken");
        }
        String shown = (displayName == null || displayName.isBlank()) ? name : displayName.trim();

        String salt = newSalt();
        User user = new User(name, shown, salt, hash(salt, password));
        users.put(name, user);
        userStore.save(users);
        LOG.info("New account: " + name);
        return user;
    }

    public User login(String username, String password) throws AuthException {
        User user = users.get(clean(username));
        // Same message for "no such user" and "wrong password", so nobody can guess usernames.
        if (user == null || password == null || !hash(user.getSalt(), password).equals(user.getPasswordHash())) {
            LOG.warning("Failed log-in for '" + clean(username) + "'");
            throw new AuthException("Wrong username or password");
        }
        LOG.info("Logged in: " + user.getUsername());
        return user;
    }

    public User findUser(String username) {
        return users.get(clean(username));
    }

    public int getUserCount() { return users.size(); }

    // ------------------------------------------------------------ each user's cart

    /** Each user's cart and budget are saved in data/users/<username>/. */
    public CartService cartFor(User user) {
        return cartFor(user.getUsername());
    }

    public CartService cartFor(String username) {
        CartService cart = carts.get(username);
        if (cart == null) {
            File folder = new File(usersFolder, username);
            folder.mkdirs();
            cart = new CartService(prices, budgetService,
                    new SerialStore<Cart>(new File(folder, "cart.ser")),
                    new SerialStore<Budget>(new File(folder, "budget.ser")));
            carts.put(username, cart);
        }
        return cart;
    }

    // ------------------------------------------------------------ helpers

    private String clean(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String newSalt() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String hash(String salt, String password) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);   // every JDK has it
        }
    }
}
