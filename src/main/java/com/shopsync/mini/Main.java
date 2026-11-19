package com.shopsync.mini;

import com.shopsync.mini.data.CsvLoader;
import com.shopsync.mini.data.ProductRepository;
import com.shopsync.mini.data.ReportWriter;
import com.shopsync.mini.data.SerialStore;
import com.shopsync.mini.model.Budget;
import com.shopsync.mini.model.Cart;
import com.shopsync.mini.model.SearchHistory;
import com.shopsync.mini.service.BudgetService;
import com.shopsync.mini.service.CartService;
import com.shopsync.mini.service.PriceComparisonService;
import com.shopsync.mini.ui.ConsoleApp;
import com.shopsync.mini.web.WebServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Starting point.
 *   java -jar shopsync-mini.jar console   -> menu in the terminal
 *   java -jar shopsync-mini.jar           -> website on http://localhost:8080
 *
 * Both modes share the same data loading and the same PriceComparisonService.
 */
public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        boolean consoleMode = args.length > 0 && args[0].equalsIgnoreCase("console");
        setUpLogging(consoleMode);

        try {
            File dataDir = new File("data");
            dataDir.mkdirs();

            // 1. File handling: read the price list from data/products.csv
            File csv = new File(dataDir, "products.csv");
            copyCsvFromJar(csv);
            List<CsvLoader.Row> rows = new CsvLoader().load(csv);

            // 2. JDBC: put the rows into the database
            ProductRepository repository = new ProductRepository();
            repository.saveAll(rows);

            // 3. Serialization: history, cart and budget are each saved in data/*.ser
            PriceComparisonService service = new PriceComparisonService(repository,
                    new SerialStore<SearchHistory>(new File(dataDir, "history.ser")));
            CartService cartService = new CartService(service, new BudgetService(service),
                    new SerialStore<Cart>(new File(dataDir, "cart.ser")),
                    new SerialStore<Budget>(new File(dataDir, "budget.ser")));
            ReportWriter reports = new ReportWriter(new File("reports"));

            if (consoleMode) {
                try (repository) {                         // closes the DB when the menu ends
                    new ConsoleApp(service, cartService, reports).run();
                }
            } else {
                int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
                new WebServer(service, cartService, reports, port).start();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "ShopSync Mini could not start", e);
            System.err.println("Startup failed: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Copies the price list out of the jar into data/products.csv on every start,
     * so the file on disk always matches src/main/resources/products.csv.
     */
    private static void copyCsvFromJar(File csv) throws IOException {
        try (InputStream in = Main.class.getResourceAsStream("/products.csv")) {
            if (in == null) {
                throw new IOException("products.csv is missing from the jar");
            }
            Files.copy(in, csv.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Lecture 10 (Logging): everything goes to logs/shopsync-mini.log. */
    private static void setUpLogging(boolean consoleMode) {
        Logger root = Logger.getLogger("");
        try {
            new File("logs").mkdirs();
            FileHandler file = new FileHandler("logs/shopsync-mini.log", true);   // true = append
            file.setFormatter(new SimpleFormatter());
            file.setLevel(Level.INFO);
            root.addHandler(file);
        } catch (IOException e) {
            System.err.println("Log file unavailable: " + e.getMessage());
        }
        // In the console menu, only show warnings on screen so the menu stays clean.
        for (Handler h : root.getHandlers()) {
            if (!(h instanceof FileHandler)) {
                h.setLevel(consoleMode ? Level.WARNING : Level.INFO);
            }
        }
    }
}
