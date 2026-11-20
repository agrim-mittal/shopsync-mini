# ShopSync

ShopSync has a login and three main features:

- **Log in / Sign up**: each user has their own cart and budget. Demo account: `demo` / `demo123`. Passwords are salted and hashed (SHA-256), never stored as plain text.
0. **Product catalogue** on the home page: every product grouped by category (filter chips), each showing its best price and store. Click one to compare. In the console: option 2, *Browse all products by category*.
1. **Compare prices** of a product across Amazon, Flipkart, Myntra and Croma, including each store's extra offer and delivery charge.
2. **My cart**: add a product from any store, change quantities, remove items. The cart is saved between runs.
3. **Monthly budget**: set a monthly limit (and optional per-category limits). The budget check goes through every item in the cart, shows how much is used or over, and suggests cheaper stores for items already in the cart. Adding something that would break a limit asks you to confirm first.

It runs two ways, and both share the same Java code underneath:

| Mode | Command | What you get |
|---|---|---|
| Website | `./run.sh` (or `run.bat`) | Opens on http://localhost:8080 |
| Console | `./run.sh console` | A numbered menu in the terminal |

You need Java 17+ and Maven (`brew install maven` on a Mac). Prices are sample data in `src/main/resources/products.csv`.

---

## How a search flows

```
products.csv ──(File I/O)──► CsvLoader ──► ProductRepository ──(JDBC)──► H2 database
                                                    │
ConsoleApp ─┐                                       ▼
            ├──► PriceComparisonService ──► Offer list (Amazon, Flipkart, ... = Store subclasses)
WebServer  ─┘            │                          │
                         ▼                          ▼
              SerialStore<T> (Serialization)  ComparisonResult (sorted, cheapest first)
              Logger → logs/shopsync-mini.log       ReportWriter → reports/*.txt

ConsoleApp / WebServer ──► CartService ──► Cart (CartItem...)      ──► data/cart.ser
                               │      └──► Budget (monthly + per category) ──► data/budget.ser
                               ▼
                         BudgetService ── ensureFits() throws BudgetExceededException
                               └──────── check() ──► BudgetReport (status enum, categories, saving tips)
```

## Package layout (Lecture 2)

```
com.shopsync.mini
├── Main.java              starts everything, picks console or web mode
├── model/                 Product, Offer, ComparisonResult, SearchHistory,
│                          CartItem, Cart, Budget, BudgetStatus (enum), BudgetReport
├── store/                 PriceCalculator (interface), Store (abstract), Amazon, Flipkart, Myntra, Croma
├── exception/             ProductNotFoundException, InvalidSearchException, BudgetExceededException
├── data/                  CsvLoader, ProductRepository (JDBC), SerialStore<T>, ReportWriter
├── service/               PriceComparisonService, CartService, BudgetService,
│                          AccountService (sign-up, log-in, one CartService per user)
├── ui/                    ConsoleApp (terminal menu)
└── web/                   WebServer (web plumbing only, see note below)
```

---

## Where each course topic is used

| # | Topic | Where to show it |
|---|---|---|
| 1 | **Intro to Java** | `Main.main(String[] args)` reads the `console` argument; loops, `if`, `switch` and `String` methods throughout `ConsoleApp` and `CsvLoader`. **Enum** `BudgetStatus` (constants with a constructor, a field and a static method). Wrapper class `Double` used so a category limit can be `null` (`BudgetReport.CategoryLine`) |
| 2 | **Project structure, packages, classes** | 8 packages above; every class has private fields + getters (`Product`, `Offer`) |
| 3 | **Method & constructor overloading** | `Product` and `CartItem` each have 2 constructors (one calls the other with `this(...)`). `PriceComparisonService.search(...)` × 2, `compare(...)` × 3. `CartService.addToCart(...)` × 3: by `Offer`, by `Offer + qty`, by product id + store name. `ProductRepository.searchByName(...)` × 2 |
| 4 | **Nested classes** | Static nested: `ComparisonResult.Summary`, `CsvLoader.Row`, `BudgetReport.CategoryLine`, `BudgetReport.Suggestion`. Inner (non-static): `ConsoleApp.Screen` uses the outer object's `sortBy` and `service`. Anonymous class: the `Comparator` in `PriceComparisonService.sortOffers` |
| 4 | **Inheritance** | `Amazon`, `Flipkart`, `Myntra`, `Croma` **extend** `Store`, call `super(...)`, and **override** `extraDiscount()` and `offerNote()`. `deliveryFee()` is written once in `Store` and inherited |
| 4 | **Abstract classes** | `Store` is `abstract` with abstract `offerNote()`; it can't be created with `new` |
| 4 | **Interfaces** | `PriceCalculator` interface with a **default method** `finalPrice()`; `Store implements PriceCalculator`. `Offer implements Comparable<Offer>` |
| 4 | **Polymorphism** | `Map<String, Store>` holds 4 different subclasses; `offer.getFinalPrice()` runs the right store's rules at runtime |
| 5 | **Coding conventions & build tools** | Maven `pom.xml` (dependency + packaging into one jar); CamelCase classes, camelCase methods, UPPER_CASE constants, one public class per file |
| 6 | **Exception handling** | Custom **checked** `ProductNotFoundException` and `BudgetExceededException` (carries extra data: how much over). `BudgetService.ensureFits()` **throws** it, `CartService.addToCart()` passes it up with `throws`, and the UI **catches** it to ask "Add anyway?". **Unchecked** `InvalidSearchException`; `IllegalArgumentException` for bad quantity/budget values. `try-catch-finally` (`ConsoleApp.run`), multi-catch, try-with-resources, wrapping `SQLException` |
| 7 | **Collections & utility classes** | `ArrayList`, `LinkedList` (history), `HashMap` (URL params), `LinkedHashMap` (stores; cart items keyed by `productId@store`), `TreeMap` (category totals and limits, sorted A–Z), `Map<String, List<ComparisonResult>>`: a map of lists for the home-page catalogue (`PriceComparisonService.getCatalog()`), `Map.Entry` / `entrySet()` loop (`BudgetService.check`), `getOrDefault`, `Collections.sort`, `Comparator`, `Math.min`, `String.format`. **Generics**: `SerialStore<T extends Serializable>` used as `SerialStore<Cart>`, `SerialStore<Budget>`, `SerialStore<SearchHistory>` |
| 8 | **File I/O** | `CsvLoader` reads with `FileReader` + `BufferedReader`; `ReportWriter` writes with `FileWriter` + `BufferedWriter` + `PrintWriter`; `File.mkdirs()` |
| 6 | **Login (exceptions)** | Custom checked `AuthException` (wrong password, username taken, password too short) → HTTP 401 on the website, a "!" message in the console |
| 9 | **Serialization** | `SearchHistory`, `Cart`, `CartItem` and `Budget` implement `Serializable` (with `serialVersionUID`; `SearchHistory` has a `transient` field). One reusable `SerialStore<T>` uses `ObjectOutputStream` / `ObjectInputStream` → `data/history.ser`, `data/cart.ser`, `data/budget.ser`, so the cart and budget survive a restart |
| 10 | **Logging** | `java.util.logging.Logger` in every data/service class; `Main.setUpLogging()` adds a `FileHandler` + `SimpleFormatter` → `logs/shopsync-mini.log`, with different levels for console vs web mode |
| 11 | **JDBC** | `ProductRepository`: `DriverManager.getConnection`, `Statement` for `CREATE TABLE`, `PreparedStatement` with `?` placeholders, `addBatch`/`executeBatch`, `ResultSet` loops (`findAll()` with `ORDER BY category, name` for the catalogue), implements `AutoCloseable` |
| 12–13 | JavaFX | Not used; the UIs here are console + web |

### About the web part

`web/WebServer.java` and the files in `resources/web/` (HTML/CSS/JS) are **only plumbing** so the same Java logic can be shown in a browser. It uses the JDK's built-in `com.sun.net.httpserver`. No logic lives there: each URL calls a `PriceComparisonService` method, exactly like the console menu does.

---

## How the price is worked out

`final price = listed price − store's extra discount + delivery fee`

| Store | Extra discount | Delivery |
|---|---|---|
| Amazon | 5% on Books | Free above ₹499, else ₹40 |
| Flipkart | 2% bank offer on Electronics (max ₹1,000) | Free above ₹500, else ₹40 |
| Myntra | 10% on Fashion | Free above ₹799, else ₹79 |
| Croma | ₹500 off Electronics above ₹20,000 | Always free |

## How the budget check works

- **Before adding to the cart**, `BudgetService.ensureFits()` adds the new item to the current total. If that crosses the monthly budget or the item's category limit, it throws `BudgetExceededException`. Console asks *"Add it anyway? (y/n)"*; the website shows an **Add anyway** button.
- **Budget check** (console option 8 / Budget tab) goes through every cart item and shows:
  - total vs monthly budget, % used, and status: `SAFE` (under 80%), `NEAR_LIMIT` (80–100%), `OVER` (above 100%), or `NO_BUDGET`
  - each category's cart total vs its own limit
  - **saving tips**: for every item, it re-runs the price comparison and, if another store is now cheaper, says how much switching saves. You can apply a tip with one step.

## Deploying on Render

The `Dockerfile` builds the jar with Maven and runs it. Push this folder to a new GitHub repo, then in Render: **New + → Blueprint →** pick the repo (it reads `render.yaml`). This is a separate service from the main ShopSync, so that one isn't touched.
