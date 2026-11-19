package com.shopsync.mini.service;

import com.shopsync.mini.data.SerialStore;
import com.shopsync.mini.data.ProductRepository;
import com.shopsync.mini.exception.InvalidSearchException;
import com.shopsync.mini.exception.ProductNotFoundException;
import com.shopsync.mini.model.ComparisonResult;
import com.shopsync.mini.model.Offer;
import com.shopsync.mini.model.Product;
import com.shopsync.mini.model.SearchHistory;
import com.shopsync.mini.store.Amazon;
import com.shopsync.mini.store.Croma;
import com.shopsync.mini.store.Flipkart;
import com.shopsync.mini.store.Myntra;
import com.shopsync.mini.store.Store;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * The "brain" of the app. Both the console UI and the web UI call this class,
 * so the comparison logic is written only once.
 *
 * Concepts: Map/LinkedHashMap, List, method overloading, throwing and
 * wrapping exceptions, anonymous inner class (Comparator), logging.
 */
public class PriceComparisonService {

    private static final Logger LOG = Logger.getLogger(PriceComparisonService.class.getName());

    private final ProductRepository repository;
    private final SerialStore<SearchHistory> historyStore;
    private final SearchHistory history;

    // Store name -> Store object. LinkedHashMap keeps insertion order.
    private final Map<String, Store> stores = new LinkedHashMap<>();

    public PriceComparisonService(ProductRepository repository, SerialStore<SearchHistory> historyStore) {
        this.repository = repository;
        this.historyStore = historyStore;
        this.history = historyStore.load(new SearchHistory());

        // Polymorphism: every value is a Store reference, but each is a different subclass.
        register(new Amazon());
        register(new Flipkart());
        register(new Myntra());
        register(new Croma());
    }

    private void register(Store store) {
        stores.put(store.getName(), store);
    }

    public Collection<Store> getStores() { return stores.values(); }

    // ---------------------------------------------------------------- search (overloaded)

    public List<Product> search(String keyword) throws ProductNotFoundException {
        return search(keyword, null);
    }

    public List<Product> search(String keyword, String category) throws ProductNotFoundException {
        String q = validate(keyword);
        try {
            List<Product> found = (category == null || category.isBlank())
                    ? repository.searchByName(q)
                    : repository.searchByName(q, category);
            if (found.isEmpty()) {
                throw new ProductNotFoundException(q);
            }
            LOG.info("Search '" + q + "' matched " + found.size() + " product(s)");
            return found;
        } catch (SQLException e) {
            // Wrap the low-level checked exception in an unchecked one for the UI layer.
            throw new IllegalStateException("Database error: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- compare (overloaded)

    /** Compare the first product that matches the text. */
    public ComparisonResult compare(String keyword) throws ProductNotFoundException {
        return compare(search(keyword).get(0));
    }

    /** Compare a specific product and remember it in the search history. */
    public ComparisonResult compare(Product product) {
        return compare(product, true);
    }

    /** Overload: re-sorting the same product should not add another history entry. */
    public ComparisonResult compare(Product product, boolean saveToHistory) {
        try {
            List<Offer> offers = repository.findOffers(product, stores);
            ComparisonResult result = new ComparisonResult(product, offers);

            ComparisonResult.Summary s = result.summarise();
            if (saveToHistory) {
                history.add(product.getName(), s.getBestStore(), s.getBestPrice());
                historyStore.save(history);
                history.markSaved();
            }

            LOG.info("Compared " + product.getId() + ": " + s);
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Database error: " + e.getMessage(), e);
        }
    }

    public ComparisonResult compareById(String productId) throws ProductNotFoundException {
        return compareById(productId, true);
    }

    public ComparisonResult compareById(String productId, boolean saveToHistory) throws ProductNotFoundException {
        try {
            Product p = repository.findById(productId);
            if (p == null) {
                throw new ProductNotFoundException(productId);
            }
            return compare(p, saveToHistory);
        } catch (SQLException e) {
            throw new IllegalStateException("Database error: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- sorting

    /**
     * Returns the offers in another order.
     * "price" uses the natural order (Offer implements Comparable),
     * "store" uses an anonymous inner class, "delivery" uses a lambda.
     */
    public List<Offer> sortOffers(ComparisonResult result, String sortBy) {
        List<Offer> copy = new ArrayList<>(result.getOffers());

        switch (sortBy) {
            case "store":
                Collections.sort(copy, new Comparator<Offer>() {       // anonymous inner class
                    @Override
                    public int compare(Offer a, Offer b) {
                        return a.getStore().getName().compareTo(b.getStore().getName());
                    }
                });
                break;
            case "delivery":
                copy.sort(Comparator.comparingDouble(Offer::getDeliveryFee));
                break;
            default:
                Collections.sort(copy);                                 // cheapest first
        }
        return copy;
    }

    // ---------------------------------------------------------------- misc

    public List<String> getCategories() {
        try {
            return repository.findCategories();
        } catch (SQLException e) {
            throw new IllegalStateException("Database error: " + e.getMessage(), e);
        }
    }

    public List<String> getHistory() { return history.getEntries(); }

    /**
     * The whole catalogue for the home page: category -> comparisons of every product in it.
     * TreeMap keeps categories in A-Z order; each list is an ArrayList.
     */
    public Map<String, List<ComparisonResult>> getCatalog() {
        Map<String, List<ComparisonResult>> catalog = new TreeMap<>();
        try {
            for (Product p : repository.findAll()) {
                List<ComparisonResult> inCategory = catalog.get(p.getCategory());
                if (inCategory == null) {
                    inCategory = new ArrayList<>();
                    catalog.put(p.getCategory(), inCategory);
                }
                inCategory.add(compare(p, false));          // false = don't add to search history
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error: " + e.getMessage(), e);
        }
        return catalog;
    }

    private String validate(String keyword) {
        if (keyword == null || keyword.trim().length() < 2) {
            throw new InvalidSearchException("Type at least 2 characters to search");
        }
        return keyword.trim();
    }
}
