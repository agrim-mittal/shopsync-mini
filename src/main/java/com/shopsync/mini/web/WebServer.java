package com.shopsync.mini.web;

import com.shopsync.mini.data.ReportWriter;
import com.shopsync.mini.exception.AuthException;
import com.shopsync.mini.exception.BudgetExceededException;
import com.shopsync.mini.exception.InvalidSearchException;
import com.shopsync.mini.exception.ProductNotFoundException;
import com.shopsync.mini.model.Budget;
import com.shopsync.mini.model.BudgetReport;
import com.shopsync.mini.model.Cart;
import com.shopsync.mini.model.CartItem;
import com.shopsync.mini.model.ComparisonResult;
import com.shopsync.mini.model.Offer;
import com.shopsync.mini.model.Product;
import com.shopsync.mini.model.User;
import com.shopsync.mini.service.AccountService;
import com.shopsync.mini.service.CartService;
import com.shopsync.mini.service.PriceComparisonService;
import com.shopsync.mini.store.Store;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * WEB PLUMBING ONLY (not part of the syllabus).
 *
 * Uses the JDK's built-in mini web server so the same PriceComparisonService
 * can be shown in a browser. It just turns URLs into service calls and turns
 * the answers into JSON text. All the actual logic is in the service package.
 */
public class WebServer {

    private static final Logger LOG = Logger.getLogger(WebServer.class.getName());

    private final PriceComparisonService service;
    private final AccountService accounts;
    private final Map<String, String> sessions = new HashMap<>();   // login token -> username
    private final ReportWriter reports;
    private final int port;

    public WebServer(PriceComparisonService service, AccountService accounts, ReportWriter reports, int port) {
        this.service = service;
        this.accounts = accounts;
        this.reports = reports;
        this.port = port;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/search", ex -> handle(ex, () -> {
            Map<String, String> q = params(ex);
            return productsJson(service.search(q.get("q"), q.get("category")));
        }));
        server.createContext("/api/compare", ex -> handle(ex, () -> {
            Map<String, String> q = params(ex);
            ComparisonResult r = service.compareById(q.get("id"), !"0".equals(q.get("record")));
            return comparisonJson(r, q.getOrDefault("sort", "price"));
        }));
        server.createContext("/api/catalog", ex -> handle(ex, this::catalogJson));
        server.createContext("/api/categories", ex -> handle(ex, () -> stringsJson(service.getCategories())));
        server.createContext("/api/history", ex -> handle(ex, () -> stringsJson(service.getHistory())));
        server.createContext("/api/stores", ex -> handle(ex, this::storesJson));
        server.createContext("/api/report", this::sendReport);

        // ---- accounts ----
        server.createContext("/api/auth/login", ex -> handle(ex, () -> {
            Map<String, String> f = form(ex);
            return startSession(ex, accounts.login(f.get("username"), f.get("password")));
        }));
        server.createContext("/api/auth/register", ex -> handle(ex, () -> {
            Map<String, String> f = form(ex);
            return startSession(ex, accounts.register(f.get("username"), f.get("password"), f.get("name")));
        }));
        server.createContext("/api/auth/logout", ex -> handle(ex, () -> {
            sessions.remove(token(ex));
            ex.getResponseHeaders().add("Set-Cookie", "ssid=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
            return "{\"ok\":true}";
        }));
        server.createContext("/api/auth/me", ex -> handle(ex, () -> userJson(currentUser(ex))));

        // ---- cart (each user has their own) ----
        server.createContext("/api/cart", ex -> handle(ex, () -> cartJson(cart(ex))));
        server.createContext("/api/cart/add", ex -> handle(ex, () -> {
            Map<String, String> q = params(ex);
            CartService cart = cart(ex);
            cart.addToCart(q.get("id"), q.get("store"), intParam(q, "qty", 1), "1".equals(q.get("force")));
            return cartJson(cart);
        }));
        server.createContext("/api/cart/qty", ex -> handle(ex, () -> {
            Map<String, String> q = params(ex);
            CartService cart = cart(ex);
            cart.changeQuantity(q.get("key"), intParam(q, "qty", 1));
            return cartJson(cart);
        }));
        server.createContext("/api/cart/remove", ex -> handle(ex, () -> {
            CartService cart = cart(ex);
            cart.remove(params(ex).get("key"));
            return cartJson(cart);
        }));
        server.createContext("/api/cart/clear", ex -> handle(ex, () -> {
            CartService cart = cart(ex);
            cart.clearCart();
            return cartJson(cart);
        }));
        server.createContext("/api/cart/switch", ex -> handle(ex, () -> {
            Map<String, String> q = params(ex);
            CartService cart = cart(ex);
            cart.switchStore(q.get("key"), q.get("store"));
            return budgetJson(cart);
        }));

        // ---- budget ----
        server.createContext("/api/budget", ex -> handle(ex, () -> budgetJson(cart(ex))));
        server.createContext("/api/budget/set", ex -> handle(ex, () -> {
            CartService cart = cart(ex);
            cart.setMonthlyBudget(doubleParam(params(ex), "amount"));
            return budgetJson(cart);
        }));
        server.createContext("/api/budget/category", ex -> handle(ex, () -> {
            Map<String, String> q = params(ex);
            CartService cart = cart(ex);
            cart.setCategoryLimit(q.get("name"), doubleParam(q, "amount"));
            return budgetJson(cart);
        }));
        server.createContext("/", this::sendStaticFile);

        server.start();
        LOG.info("ShopSync Mini running on http://localhost:" + port);
        System.out.println("Open http://localhost:" + port + " in your browser");
    }

    // ------------------------------------------------------------ request handling

    /** A small functional interface so each route can be written as a lambda. */
    private interface JsonAction {
        String run() throws Exception;
    }

    private void handle(HttpExchange ex, JsonAction action) throws IOException {
        int status = 200;
        String body;
        try {
            body = action.run();
        } catch (AuthException e) {
            status = 401;                                   // 401 = "not logged in / wrong password"
            body = errorJson(e.getMessage());
        } catch (BudgetExceededException e) {
            status = 409;                                   // 409 = "conflict": needs the user to confirm
            body = "{\"error\":" + str(e.getMessage()) + ",\"overBudget\":true,\"overBy\":" + e.getOverBy() + "}";
        } catch (InvalidSearchException | IllegalArgumentException e) {
            status = 400;
            body = errorJson(e.getMessage());
        } catch (ProductNotFoundException e) {
            status = 404;
            body = errorJson(e.getMessage());
        } catch (Exception e) {
            status = 500;
            body = errorJson("Something went wrong");
            LOG.warning("Request failed: " + e);
        }
        send(ex, status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private void sendReport(HttpExchange ex) throws IOException {
        try {
            ComparisonResult r = service.compareById(params(ex).get("id"), false);
            File file = reports.write(r);
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            send(ex, 200, "text/plain; charset=utf-8", Files.readAllBytes(file.toPath()));
        } catch (ProductNotFoundException e) {
            send(ex, 404, "text/plain", e.getMessage().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void sendStaticFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        // The app needs a log-in; the login page doesn't.
        if (path.equals("/index.html") && !isLoggedIn(ex)) {
            redirect(ex, "/login.html");
            return;
        }
        if (path.equals("/login.html") && isLoggedIn(ex)) {
            redirect(ex, "/");
            return;
        }
        if (path.contains("..")) {
            send(ex, 400, "text/plain", new byte[0]);
            return;
        }
        try (InputStream in = WebServer.class.getResourceAsStream("/web" + path)) {
            if (in == null) {
                send(ex, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String type = path.endsWith(".css") ? "text/css"
                    : path.endsWith(".js") ? "application/javascript"
                    : path.endsWith(".svg") ? "image/svg+xml" : "text/html";
            send(ex, 200, type + "; charset=utf-8", in.readAllBytes());
        }
    }

    private void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    private Map<String, String> params(HttpExchange ex) {
        Map<String, String> map = new HashMap<>();
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) {
            return map;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(kv[0], value);
        }
        return map;
    }

    // ------------------------------------------------------------ log-in sessions

    /** Creates a random login token, remembers whose it is, and sends it as a cookie. */
    private String startSession(HttpExchange ex, User user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, user.getUsername());
        String secure = "https".equalsIgnoreCase(ex.getRequestHeaders().getFirst("X-Forwarded-Proto")) ? "; Secure" : "";
        ex.getResponseHeaders().add("Set-Cookie",
                "ssid=" + token + "; Path=/; Max-Age=604800; HttpOnly; SameSite=Lax" + secure);
        return userJson(user);
    }

    private String token(HttpExchange ex) {
        String cookies = ex.getRequestHeaders().getFirst("Cookie");
        if (cookies == null) {
            return "";
        }
        for (String part : cookies.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals("ssid")) {
                return kv[1];
            }
        }
        return "";
    }

    private User currentUser(HttpExchange ex) throws AuthException {
        String username = sessions.get(token(ex));
        User user = (username == null) ? null : accounts.findUser(username);
        if (user == null) {
            throw new AuthException("Please log in");
        }
        return user;
    }

    /** The logged-in user's own cart and budget. */
    private CartService cart(HttpExchange ex) throws AuthException {
        return accounts.cartFor(currentUser(ex));
    }

    private boolean isLoggedIn(HttpExchange ex) {
        String username = sessions.get(token(ex));
        return username != null && accounts.findUser(username) != null;
    }

    /** Reads a POST body like "username=agrim&password=secret" (keeps passwords out of the URL). */
    private Map<String, String> form(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            throw new IllegalArgumentException("Use POST");
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> map = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private String userJson(User u) {
        return "{\"username\":" + str(u.getUsername()) + ",\"name\":" + str(u.getDisplayName()) + "}";
    }

    private void redirect(HttpExchange ex, String to) throws IOException {
        ex.getResponseHeaders().set("Location", to);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    private int intParam(Map<String, String> q, String name, int fallback) {
        try {
            return Integer.parseInt(q.getOrDefault(name, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a whole number");
        }
    }

    private double doubleParam(Map<String, String> q, String name) {
        try {
            return Double.parseDouble(q.getOrDefault(name, "").replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Please enter a number");
        }
    }

    // ------------------------------------------------------------ JSON text

    private String catalogJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean firstCategory = true;
        for (Map.Entry<String, List<ComparisonResult>> entry : service.getCatalog().entrySet()) {
            if (!firstCategory) sb.append(',');
            firstCategory = false;
            sb.append("{\"category\":").append(str(entry.getKey())).append(",\"products\":[");
            List<ComparisonResult> results = entry.getValue();
            for (int i = 0; i < results.size(); i++) {
                ComparisonResult r = results.get(i);
                ComparisonResult.Summary s = r.summarise();
                if (i > 0) sb.append(',');
                sb.append("{\"product\":").append(productJson(r.getProduct()))
                  .append(",\"bestStore\":").append(str(s.getBestStore()))
                  .append(",\"bestPrice\":").append(s.getBestPrice())
                  .append(",\"stores\":").append(r.getOffers().size()).append('}');
            }
            sb.append("]}");
        }
        return sb.append(']').toString();
    }

    private String cartJson(CartService cart) {
        Cart c = cart.getCart();
        Budget b = cart.getBudget();
        StringBuilder sb = new StringBuilder("{\"items\":[");
        List<CartItem> items = c.getItems();
        for (int i = 0; i < items.size(); i++) {
            CartItem it = items.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"key\":").append(str(it.getKey()))
              .append(",\"id\":").append(str(it.getProductId()))
              .append(",\"name\":").append(str(it.getProductName()))
              .append(",\"category\":").append(str(it.getCategory()))
              .append(",\"store\":").append(str(it.getStoreName()))
              .append(",\"unit\":").append(it.getUnitPrice())
              .append(",\"qty\":").append(it.getQuantity())
              .append(",\"total\":").append(it.getLineTotal()).append('}');
        }
        sb.append("],\"count\":").append(c.getItemCount())
          .append(",\"total\":").append(c.getTotal())
          .append(",\"budget\":").append(b.getMonthlyLimit()).append('}');
        return sb.toString();
    }

    private String budgetJson(CartService cart) {
        BudgetReport r = cart.checkBudget();
        Budget b = cart.getBudget();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"month\":").append(str(r.getMonth()))
          .append(",\"limit\":").append(r.getLimit())
          .append(",\"spent\":").append(r.getSpent())
          .append(",\"remaining\":").append(r.getRemaining())
          .append(",\"percent\":").append(r.getPercentUsed())
          .append(",\"status\":").append(str(r.getStatus().name()))
          .append(",\"statusLabel\":").append(str(r.getStatus().getLabel()))
          .append(",\"savings\":").append(r.getPossibleSavings())
          .append(",\"items\":").append(cart.getCart().getItemCount())
          .append(",\"categories\":[");
        boolean first = true;
        for (BudgetReport.CategoryLine c : r.getCategories()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"name\":").append(str(c.getCategory()))
              .append(",\"spent\":").append(c.getSpent())
              .append(",\"limit\":").append(c.getLimit() == null ? "null" : c.getLimit().toString())
              .append(",\"over\":").append(c.isOver()).append('}');
        }
        // categories that have a limit but nothing in the cart yet
        for (Map.Entry<String, Double> e : b.getCategoryLimits().entrySet()) {
            if (!cart.getCart().totalsByCategory().containsKey(e.getKey())) {
                if (!first) sb.append(',');
                first = false;
                sb.append("{\"name\":").append(str(e.getKey())).append(",\"spent\":0,\"limit\":")
                  .append(e.getValue()).append(",\"over\":false}");
            }
        }
        sb.append("],\"suggestions\":[");
        List<BudgetReport.Suggestion> tips = r.getSuggestions();
        for (int i = 0; i < tips.size(); i++) {
            BudgetReport.Suggestion t = tips.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"key\":").append(str(t.getItemKey()))
              .append(",\"name\":").append(str(t.getProductName()))
              .append(",\"from\":").append(str(t.getFromStore()))
              .append(",\"to\":").append(str(t.getToStore()))
              .append(",\"saving\":").append(t.getSaving()).append('}');
        }
        return sb.append("]}").toString();
    }

    private String productJson(Product p) {
        return "{\"id\":" + str(p.getId())
                + ",\"name\":" + str(p.getName())
                + ",\"category\":" + str(p.getCategory())
                + ",\"mrp\":" + p.getMrp() + "}";
    }

    private String productsJson(List<Product> products) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < products.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(productJson(products.get(i)));
        }
        return sb.append(']').toString();
    }

    private String comparisonJson(ComparisonResult r, String sort) {
        ComparisonResult.Summary s = r.summarise();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"product\":").append(productJson(r.getProduct()));
        sb.append(",\"bestStore\":").append(str(s.getBestStore()))
          .append(",\"bestPrice\":").append(s.getBestPrice())
          .append(",\"youSave\":").append(s.getYouSave())
          .append(",\"offers\":[");
        List<Offer> offers = service.sortOffers(r, sort);
        for (int i = 0; i < offers.size(); i++) {
            Offer o = offers.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"store\":").append(str(o.getStore().getName()))
              .append(",\"note\":").append(str(o.getStore().offerNote()))
              .append(",\"listed\":").append(o.getListedPrice())
              .append(",\"discount\":").append(o.getExtraDiscount())
              .append(",\"delivery\":").append(o.getDeliveryFee())
              .append(",\"final\":").append(o.getFinalPrice())
              .append(",\"best\":").append(o == r.getCheapest()).append('}');
        }
        return sb.append("]}").toString();
    }

    private String storesJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Store s : service.getStores()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"name\":").append(str(s.getName())).append(",\"note\":").append(str(s.offerNote())).append('}');
        }
        return sb.append(']').toString();
    }

    private String stringsJson(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(str(items.get(i)));
        }
        return sb.append(']').toString();
    }

    private String errorJson(String message) {
        return "{\"error\":" + str(message) + "}";
    }

    private String str(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
