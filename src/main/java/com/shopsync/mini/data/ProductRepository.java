package com.shopsync.mini.data;

import com.shopsync.mini.model.Offer;
import com.shopsync.mini.model.Product;
import com.shopsync.mini.store.Store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * All database work lives here (Lecture 11: JDBC).
 *
 * Concepts: DriverManager.getConnection, Statement for CREATE TABLE,
 * PreparedStatement with ? placeholders (safe from SQL injection),
 * batch inserts, ResultSet loops, SQLException, AutoCloseable.
 */
public class ProductRepository implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ProductRepository.class.getName());
    private static final String URL = "jdbc:h2:mem:shopsync;DB_CLOSE_DELAY=-1";

    private final Connection connection;

    public ProductRepository() throws SQLException {
        connection = DriverManager.getConnection(URL, "sa", "");
        createTables();
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS product ("
                    + " id VARCHAR(10) PRIMARY KEY, name VARCHAR(120), category VARCHAR(40), mrp DOUBLE)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS listing ("
                    + " product_id VARCHAR(10), store VARCHAR(20), listed_price DOUBLE,"
                    + " PRIMARY KEY (product_id, store))");
        }
        LOG.info("Tables ready");
    }

    /** Copies the CSV rows into the two tables. MERGE = insert, or update if it already exists. */
    public void saveAll(List<CsvLoader.Row> rows) throws SQLException {
        String productSql = "MERGE INTO product (id, name, category, mrp) KEY (id) VALUES (?, ?, ?, ?)";
        String listingSql = "MERGE INTO listing (product_id, store, listed_price) KEY (product_id, store) VALUES (?, ?, ?)";

        try (PreparedStatement p = connection.prepareStatement(productSql);
             PreparedStatement l = connection.prepareStatement(listingSql)) {
            for (CsvLoader.Row r : rows) {
                p.setString(1, r.productId);
                p.setString(2, r.name);
                p.setString(3, r.category);
                p.setDouble(4, r.mrp);
                p.addBatch();

                l.setString(1, r.productId);
                l.setString(2, r.store);
                l.setDouble(3, r.listedPrice);
                l.addBatch();
            }
            p.executeBatch();
            l.executeBatch();
        }
        LOG.info("Saved " + rows.size() + " rows into the database");
    }

    /** Products whose name contains the keyword (case-insensitive). */
    public List<Product> searchByName(String keyword) throws SQLException {
        String sql = "SELECT id, name, category, mrp FROM product WHERE LOWER(name) LIKE ? ORDER BY name";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword.toLowerCase() + "%");
            return readProducts(ps);
        }
    }

    /** Overloaded version that also filters by category. */
    public List<Product> searchByName(String keyword, String category) throws SQLException {
        String sql = "SELECT id, name, category, mrp FROM product"
                + " WHERE LOWER(name) LIKE ? AND LOWER(category) = ? ORDER BY name";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword.toLowerCase() + "%");
            ps.setString(2, category.toLowerCase());
            return readProducts(ps);
        }
    }

    /** Every product, grouped by category in the SQL ORDER BY. */
    public List<Product> findAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, category, mrp FROM product ORDER BY category, name")) {
            return readProducts(ps);
        }
    }

    public Product findById(String id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, category, mrp FROM product WHERE id = ?")) {
            ps.setString(1, id);
            List<Product> found = readProducts(ps);
            return found.isEmpty() ? null : found.get(0);
        }
    }

    public List<String> findCategories() throws SQLException {
        List<String> categories = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT category FROM product ORDER BY category")) {
            while (rs.next()) {
                categories.add(rs.getString("category"));
            }
        }
        return categories;
    }

    /** Builds an Offer for every store that sells this product. */
    public List<Offer> findOffers(Product product, Map<String, Store> stores) throws SQLException {
        List<Offer> offers = new ArrayList<>();
        String sql = "SELECT store, listed_price FROM listing WHERE product_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, product.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Store store = stores.get(rs.getString("store"));
                    if (store != null) {
                        offers.add(new Offer(product, store, rs.getDouble("listed_price")));
                    }
                }
            }
        }
        return offers;
    }

    private List<Product> readProducts(PreparedStatement ps) throws SQLException {
        List<Product> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Product(rs.getString("id"), rs.getString("name"),
                        rs.getString("category"), rs.getDouble("mrp")));
            }
        }
        return list;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
        LOG.info("Database connection closed");
    }
}
