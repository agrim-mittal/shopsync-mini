package com.shopsync.mini.ui;

import com.shopsync.mini.data.ReportWriter;
import com.shopsync.mini.exception.AuthException;
import com.shopsync.mini.exception.BudgetExceededException;
import com.shopsync.mini.exception.InvalidSearchException;
import com.shopsync.mini.exception.ProductNotFoundException;
import com.shopsync.mini.model.Budget;
import com.shopsync.mini.model.BudgetReport;
import com.shopsync.mini.model.CartItem;
import com.shopsync.mini.model.ComparisonResult;
import com.shopsync.mini.model.Offer;
import com.shopsync.mini.model.Product;
import com.shopsync.mini.model.User;
import com.shopsync.mini.service.AccountService;
import com.shopsync.mini.service.CartService;
import com.shopsync.mini.service.PriceComparisonService;
import com.shopsync.mini.store.Store;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Terminal version of ShopSync Mini.
 *
 * Concepts: Scanner input, do-while menu loops, switch, try-catch-finally for each
 * kind of exception, and a non-static inner class ({@link Screen}).
 */
public class ConsoleApp {

    private final PriceComparisonService service;
    private final AccountService accounts;
    private CartService cartService;          // the logged-in user's cart (set after log-in)
    private User user;
    private final ReportWriter reports;
    private final Scanner in = new Scanner(System.in);
    private final Screen screen = new Screen();

    private ComparisonResult lastResult;     // remembered for sorting, saving and adding to cart
    private String sortBy = "price";

    public ConsoleApp(PriceComparisonService service, AccountService accounts, ReportWriter reports) {
        this.service = service;
        this.accounts = accounts;
        this.reports = reports;
    }

    public void run() {
        screen.banner();
        try {
            if (!logIn()) {
                return;
            }
            int choice;
            do {
                screen.menu();
                choice = readInt();
                try {
                    switch (choice) {
                        case 1 -> searchAndCompare(null);
                        case 2 -> browseCategory();
                        case 3 -> changeSort();
                        case 4 -> saveReport();
                        case 5 -> showHistory();
                        case 6 -> showStores();
                        case 7 -> cartMenu();
                        case 8 -> budgetMenu();
                        case 0 -> System.out.println("Bye! Happy shopping.");
                        default -> System.out.println("Please choose a number from the menu.");
                    }
                } catch (ProductNotFoundException | InvalidSearchException e) {
                    System.out.println("  ! " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    System.out.println("  ! " + e.getMessage());
                } catch (IOException e) {
                    System.out.println("  ! Could not write the file: " + e.getMessage());
                }
            } while (choice != 0);
        } finally {
            in.close();                // finally always runs, even after an exception
        }
    }

    // ------------------------------------------------------------ log in

    /** Log in or sign up before the main menu. Returns false if the user chose to exit. */
    private boolean logIn() {
        while (user == null) {
            System.out.println();
            System.out.println("  1. Log in   2. Sign up   0. Exit");
            System.out.print("  > ");
            int choice = readInt();
            if (choice == 0) {
                System.out.println("Bye!");
                return false;
            }
            try {
                System.out.print("  Username: ");
                String name = in.nextLine();
                System.out.print("  Password: ");
                String password = in.nextLine();
                if (choice == 1) {
                    user = accounts.login(name, password);
                } else if (choice == 2) {
                    System.out.print("  Your name: ");
                    user = accounts.register(name, password, in.nextLine());
                } else {
                    System.out.println("  ! Please choose 1, 2 or 0.");
                }
            } catch (AuthException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
        cartService = accounts.cartFor(user);
        System.out.println();
        System.out.println("  Welcome, " + user.getDisplayName() + "!");
        return true;
    }

    // ------------------------------------------------------------ compare

    private void searchAndCompare(String category) throws ProductNotFoundException {
        System.out.print("Product name (e.g. iphone, shoes, air fryer): ");
        String query = in.nextLine();

        List<Product> matches = (category == null)
                ? service.search(query)
                : service.search(query, category);           // overloaded method

        Product chosen = matches.get(0);
        if (matches.size() > 1) {
            System.out.println("Found " + matches.size() + " products:");
            for (int i = 0; i < matches.size(); i++) {
                System.out.printf("  %d. %s%n", i + 1, matches.get(i).getName());
            }
            System.out.print("Pick one: ");
            int pick = readInt();
            if (pick < 1 || pick > matches.size()) {
                System.out.println("  ! Not in the list, showing the first one.");
                pick = 1;
            }
            chosen = matches.get(pick - 1);
        }
        lastResult = service.compare(chosen);
        List<Offer> shown = screen.table(lastResult);
        offerToAddToCart(shown);
    }

    /** After a comparison: "add to cart?" The row numbers match the table on screen. */
    private void offerToAddToCart(List<Offer> shown) {
        System.out.print("  Add to cart? Type the row number (or press Enter to skip): ");
        int row = readInt();
        if (row < 1 || row > shown.size()) {
            return;
        }
        System.out.print("  Quantity (Enter = 1): ");
        int qty = readInt();
        if (qty < 1) {
            qty = 1;
        }
        addToCart(shown.get(row - 1), qty);
    }

    private void addToCart(Offer offer, int qty) {
        try {
            CartItem item = cartService.addToCart(offer, qty, false);
            System.out.println("  Added: " + item);
        } catch (BudgetExceededException e) {
            System.out.println("  ! Budget warning: " + e.getMessage());
            System.out.print("  Add it anyway? (y/n): ");
            if (in.nextLine().trim().equalsIgnoreCase("y")) {
                try {
                    cartService.addToCart(offer, qty, true);
                    System.out.println("  Added (over budget).");
                } catch (BudgetExceededException never) {
                    // cannot happen: the budget check is skipped when allowOverBudget is true
                }
            } else {
                System.out.println("  Not added.");
            }
        }
        System.out.printf("  Cart: %d item(s), Rs.%,.0f%n", cartService.getCart().getItemCount(),
                cartService.getCart().getTotal());
    }

    /** Lists every category, then every product in the chosen one, with its best price. */
    private void browseCategory() {
        Map<String, List<ComparisonResult>> catalog = service.getCatalog();
        List<String> categories = new ArrayList<>(catalog.keySet());

        for (int i = 0; i < categories.size(); i++) {
            System.out.printf("  %d. %-12s (%d products)%n", i + 1, categories.get(i),
                    catalog.get(categories.get(i)).size());
        }
        System.out.print("Category number: ");
        int n = readInt();
        if (n < 1 || n > categories.size()) {
            System.out.println("  ! No such category.");
            return;
        }

        List<ComparisonResult> products = catalog.get(categories.get(n - 1));
        System.out.println();
        System.out.println("  " + categories.get(n - 1).toUpperCase());
        for (int i = 0; i < products.size(); i++) {
            ComparisonResult.Summary s = products.get(i).summarise();
            System.out.printf("  %d. %-36s from Rs.%,9.0f at %s%n", i + 1,
                    products.get(i).getProduct().getName(), s.getBestPrice(), s.getBestStore());
        }
        System.out.print("Product number to compare (Enter to go back): ");
        int pick = readInt();
        if (pick < 1 || pick > products.size()) {
            return;
        }
        lastResult = service.compare(products.get(pick - 1).getProduct());   // saved to history
        offerToAddToCart(screen.table(lastResult));
    }

    private void changeSort() {
        System.out.print("Sort by  1) final price  2) store name  3) delivery fee : ");
        int n = readInt();
        sortBy = (n == 2) ? "store" : (n == 3) ? "delivery" : "price";
        System.out.println("  Sorting by " + sortBy + ".");
        if (lastResult != null) {
            screen.table(lastResult);
        }
    }

    private void saveReport() throws IOException {
        if (lastResult == null) {
            System.out.println("  ! Compare a product first.");
            return;
        }
        File file = reports.write(lastResult);
        System.out.println("  Saved to " + file.getPath());
    }

    private void showHistory() {
        List<String> entries = service.getHistory();
        if (entries.isEmpty()) {
            System.out.println("  No searches yet.");
        }
        for (String e : entries) {
            System.out.println("  - " + e);
        }
    }

    private void showStores() {
        for (Store s : service.getStores()) {                      // polymorphic call
            System.out.printf("  %-9s %s%n", s.getName(), s.offerNote());
        }
    }

    // ------------------------------------------------------------ cart

    private void cartMenu() {
        int choice;
        do {
            List<CartItem> items = screen.cart();
            System.out.println("  1. Change quantity   2. Remove an item   3. Empty cart   0. Back");
            System.out.print("  > ");
            choice = readInt();
            try {
                switch (choice) {
                    case 1 -> {
                        CartItem item = pickItem(items);
                        System.out.print("  New quantity: ");
                        cartService.changeQuantity(item.getKey(), readInt());
                    }
                    case 2 -> {
                        CartItem item = pickItem(items);
                        cartService.remove(item.getKey());
                        System.out.println("  Removed " + item.getProductName());
                    }
                    case 3 -> {
                        cartService.clearCart();
                        System.out.println("  Cart emptied.");
                    }
                    default -> { }
                }
            } catch (IllegalArgumentException e) {
                System.out.println("  ! " + e.getMessage());
            }
        } while (choice != 0);
    }

    private CartItem pickItem(List<CartItem> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Your cart is empty");
        }
        System.out.print("  Item number: ");
        int n = readInt();
        if (n < 1 || n > items.size()) {
            throw new IllegalArgumentException("No item with that number");
        }
        return items.get(n - 1);
    }

    // ------------------------------------------------------------ budget

    private void budgetMenu() throws ProductNotFoundException {
        int choice;
        do {
            BudgetReport report = cartService.checkBudget();
            screen.budget(report);
            System.out.println("  1. Set monthly budget   2. Set a category limit   3. Apply a saving tip   0. Back");
            System.out.print("  > ");
            choice = readInt();
            try {
                switch (choice) {
                    case 1 -> {
                        System.out.print("  Monthly budget in Rs.: ");
                        cartService.setMonthlyBudget(readDouble());
                        System.out.println("  Saved.");
                    }
                    case 2 -> setCategoryLimit();
                    case 3 -> applySuggestion(report.getSuggestions());
                    default -> { }
                }
            } catch (IllegalArgumentException e) {
                System.out.println("  ! " + e.getMessage());
            }
        } while (choice != 0);
    }

    private void setCategoryLimit() {
        List<String> categories = service.getCategories();
        for (int i = 0; i < categories.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, categories.get(i));
        }
        System.out.print("  Category number: ");
        int n = readInt();
        if (n < 1 || n > categories.size()) {
            throw new IllegalArgumentException("No such category");
        }
        System.out.print("  Limit in Rs. (0 removes the limit): ");
        cartService.setCategoryLimit(categories.get(n - 1), readDouble());
        System.out.println("  Saved.");
    }

    private void applySuggestion(List<BudgetReport.Suggestion> tips) throws ProductNotFoundException {
        if (tips.isEmpty()) {
            System.out.println("  No saving tips right now.");
            return;
        }
        System.out.print("  Tip number: ");
        int n = readInt();
        if (n < 1 || n > tips.size()) {
            throw new IllegalArgumentException("No tip with that number");
        }
        BudgetReport.Suggestion tip = tips.get(n - 1);
        cartService.switchStore(tip.getItemKey(), tip.getToStore());
        System.out.printf("  Switched to %s. Saved Rs.%,.0f%n", tip.getToStore(), tip.getSaving());
    }

    // ------------------------------------------------------------ input helpers

    /** Reads a whole line and turns it into a number; -1 if it is not a number. */
    private int readInt() {
        try {
            return Integer.parseInt(in.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private double readDouble() {
        try {
            return Double.parseDouble(in.nextLine().trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Please type a number, e.g. 25000");
        }
    }

    // ------------------------------------------------------------ inner class

    /**
     * Non-static inner class (Lecture 4): it can use the outer object's fields
     * directly, e.g. {@code service}, {@code cartService} and {@code sortBy}.
     */
    private class Screen {

        void banner() {
            System.out.println();
            System.out.println("  ShopSync  |  compare prices across Amazon, Flipkart, Myntra and Croma");
            System.out.println("  ---------------------------------------------------------------------------");
            System.out.println("  Demo account: " + AccountService.DEMO_USER + " / " + AccountService.DEMO_PASSWORD);
        }

        void menu() {
            System.out.println();
            System.out.println("  1. Search and compare a product");
            System.out.println("  2. Browse all products by category");
            System.out.println("  3. Change sort order (now: " + sortBy + ")");
            System.out.println("  4. Save last comparison as a report");
            System.out.println("  5. Recent searches");
            System.out.println("  6. Store offers");
            System.out.printf("  7. My cart (%d item%s)%n", cartService.getCart().getItemCount(),
                    cartService.getCart().getItemCount() == 1 ? "" : "s");
            System.out.println("  8. Monthly budget check");
            System.out.println("  0. Log out and exit  (logged in as " + user.getUsername() + ")");
            System.out.print("  > ");
        }

        /** Prints the comparison and returns the offers in the order shown. */
        List<Offer> table(ComparisonResult result) {
            Offer best = result.getCheapest();
            List<Offer> offers = service.sortOffers(result, sortBy);
            System.out.println();
            System.out.println("  " + result.getProduct());
            System.out.printf("     %-10s %10s %9s %9s %11s%n", "Store", "Listed", "Offer", "Delivery", "You pay");
            for (int i = 0; i < offers.size(); i++) {
                Offer o = offers.get(i);
                String mark = (o == best) ? "  <- best" : "";
                System.out.printf("  %d. %-10s %,10.0f %,9.0f %,9.0f %,11.0f%s%n", i + 1,
                        o.getStore().getName(), o.getListedPrice(),
                        o.getExtraDiscount() == 0 ? 0 : -o.getExtraDiscount(),
                        o.getDeliveryFee(), o.getFinalPrice(), mark);
            }
            System.out.println("  " + result.summarise());
            return offers;
        }

        List<CartItem> cart() {
            List<CartItem> items = cartService.getCart().getItems();
            System.out.println();
            System.out.println("  MY CART");
            if (items.isEmpty()) {
                System.out.println("  (empty) - compare a product and add it from there.");
            }
            for (int i = 0; i < items.size(); i++) {
                CartItem it = items.get(i);
                System.out.printf("  %d. %-34s %-9s %,9.0f x%-2d %,11.0f%n", i + 1, it.getProductName(),
                        it.getStoreName(), it.getUnitPrice(), it.getQuantity(), it.getLineTotal());
            }
            System.out.printf("  %58s %,11.0f%n", "Total Rs.", cartService.getCart().getTotal());
            return items;
        }

        void budget(BudgetReport r) {
            Budget b = cartService.getBudget();
            System.out.println();
            System.out.println("  BUDGET CHECK - " + r.getMonth());
            if (!b.hasMonthlyLimit()) {
                System.out.printf("  No monthly budget set. Cart total: Rs.%,.0f%n", r.getSpent());
            } else {
                System.out.printf("  Cart Rs.%,.0f of Rs.%,.0f  (%.0f%%)  -> %s%n",
                        r.getSpent(), r.getLimit(), r.getPercentUsed(), r.getStatus().getLabel());
                System.out.println("  " + bar(r.getPercentUsed()));
                if (r.getRemaining() >= 0) {
                    System.out.printf("  Left to spend: Rs.%,.0f%n", r.getRemaining());
                } else {
                    System.out.printf("  Over by: Rs.%,.0f%n", -r.getRemaining());
                }
            }

            if (!r.getCategories().isEmpty()) {
                System.out.println("  By category:");
                for (BudgetReport.CategoryLine c : r.getCategories()) {
                    String limit = c.getLimit() == null ? "no limit"
                            : String.format("limit Rs.%,.0f%s", c.getLimit(), c.isOver() ? "  OVER" : "");
                    System.out.printf("    %-12s Rs.%,10.0f   %s%n", c.getCategory(), c.getSpent(), limit);
                }
            }
            // category limits with nothing in the cart yet
            for (Map.Entry<String, Double> e : b.getCategoryLimits().entrySet()) {
                if (!cartService.getCart().totalsByCategory().containsKey(e.getKey())) {
                    System.out.printf("    %-12s Rs.%,10.0f   limit Rs.%,.0f%n", e.getKey(), 0.0, e.getValue());
                }
            }

            if (!r.getSuggestions().isEmpty()) {
                System.out.printf("  Saving tips (up to Rs.%,.0f):%n", r.getPossibleSavings());
                for (int i = 0; i < r.getSuggestions().size(); i++) {
                    System.out.println("    " + (i + 1) + ". " + r.getSuggestions().get(i));
                }
            }
        }

        /** A 30-character text bar, e.g. [##########----------]. */
        private String bar(double percent) {
            int filled = (int) Math.min(30, Math.round(percent * 30 / 100));
            return "[" + "#".repeat(filled) + "-".repeat(30 - filled) + "]";
        }
    }
}
