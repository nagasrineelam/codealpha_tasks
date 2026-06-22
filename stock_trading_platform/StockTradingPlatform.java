import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Stock Trading Platform (Simulation)
 * -----------------------------------------------------
 * A console-based, OOP-driven Java application that simulates
 * a basic stock market: live(ish) price movement, a user
 * portfolio with cash + holdings, buy/sell transactions,
 * performance tracking, and simple file-based persistence.
 */
public class StockTradingPlatform {

    // ====================================================
    //  STOCK  -  represents one tradable security
    // ====================================================
    static class Stock {
        private final String symbol;
        private final String name;
        private double price;
        private double previousClose;

        Stock(String symbol, String name, double price) {
            this.symbol = symbol;
            this.name = name;
            this.price = price;
            this.previousClose = price;
        }

        String getSymbol() { return symbol; }
        String getName() { return name; }
        double getPrice() { return price; }

        double getChangePercent() {
            if (previousClose == 0) return 0;
            return ((price - previousClose) / previousClose) * 100.0;
        }

        // Simulates a market tick using a small random walk
        void tick(Random rng) {
            previousClose = price;
            double percentMove = (rng.nextDouble() * 6.0) - 3.0; // -3% to +3%
            double newPrice = price * (1 + percentMove / 100.0);
            price = Math.max(0.50, round2(newPrice)); // floor so price never hits 0
        }

        private static double round2(double v) {
            return Math.round(v * 100.0) / 100.0;
        }
    }

    // ====================================================
    //  MARKET  -  owns all stocks, drives price simulation
    // ====================================================
    static class Market {
        private final Map<String, Stock> stocks = new LinkedHashMap<>();
        private final Random rng = new Random();
        private int tickCount = 0;

        void addStock(Stock s) { stocks.put(s.getSymbol(), s); }

        Stock getStock(String symbol) { return stocks.get(symbol.toUpperCase()); }

        Collection<Stock> allStocks() { return stocks.values(); }

        void advanceTime() {
            for (Stock s : stocks.values()) s.tick(rng);
            tickCount++;
        }

        int getTickCount() { return tickCount; }

        void display() {
            System.out.println("\n================= MARKET DATA (tick " + tickCount + ") =================");
            System.out.printf("%-8s %-22s %-12s %-10s%n", "Symbol", "Company", "Price", "Change%");
            System.out.println("--------------------------------------------------------------------");
            for (Stock s : stocks.values()) {
                String arrow = s.getChangePercent() > 0 ? "▲" : (s.getChangePercent() < 0 ? "▼" : "-");
                System.out.printf("%-8s %-22s $%-11.2f %s%6.2f%%%n",
                        s.getSymbol(), s.getName(), s.getPrice(), arrow, Math.abs(s.getChangePercent()));
            }
            System.out.println("======================================================================");
        }
    }

    // ====================================================
    //  TRANSACTION  -  a single buy/sell event (immutable record)
    // ====================================================
    static class Transaction {
        enum Type { BUY, SELL }

        private final Type type;
        private final String symbol;
        private final int quantity;
        private final double price;
        private final String timestamp;

        Transaction(Type type, String symbol, int quantity, double price) {
            this.type = type;
            this.symbol = symbol;
            this.quantity = quantity;
            this.price = price;
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        // Used when reloading from the persisted log file
        Transaction(Type type, String symbol, int quantity, double price, String timestamp) {
            this.type = type;
            this.symbol = symbol;
            this.quantity = quantity;
            this.price = price;
            this.timestamp = timestamp;
        }

        double getTotal() { return quantity * price; }

        String toFileLine() {
            return type + "," + symbol + "," + quantity + "," + price + "," + timestamp;
        }

        static Transaction fromFileLine(String line) {
            String[] p = line.split(",");
            return new Transaction(Type.valueOf(p[0]), p[1], Integer.parseInt(p[2]),
                    Double.parseDouble(p[3]), p[4]);
        }

        @Override
        public String toString() {
            return String.format("[%s] %-4s %-6s x%-5d @ $%-8.2f total $%-10.2f",
                    timestamp, type, symbol, quantity, price, getTotal());
        }
    }

    // ====================================================
    //  HOLDING  -  shares of one stock owned by the user
    // ====================================================
    static class Holding {
        private final String symbol;
        private int quantity;
        private double totalCost; // sum of cost basis, used for avg cost + P/L

        Holding(String symbol) {
            this.symbol = symbol;
            this.quantity = 0;
            this.totalCost = 0;
        }

        void addShares(int qty, double price) {
            quantity += qty;
            totalCost += qty * price;
        }

        // Returns true if fully sold out (holding should be removed)
        boolean removeShares(int qty) {
            double avg = getAverageCost();
            quantity -= qty;
            totalCost -= qty * avg;
            if (quantity <= 0) {
                quantity = 0;
                totalCost = 0;
                return true;
            }
            return false;
        }

        double getAverageCost() {
            return quantity == 0 ? 0 : totalCost / quantity;
        }

        String getSymbol() { return symbol; }
        int getQuantity() { return quantity; }
        double getTotalCost() { return totalCost; }
    }

    // ====================================================
    //  PORTFOLIO  -  a user's cash, holdings, and history
    // ====================================================
    static class Portfolio {
        private double cashBalance;
        private final double startingBalance;
        private final Map<String, Holding> holdings = new LinkedHashMap<>();
        private final List<Transaction> history = new ArrayList<>();

        Portfolio(double startingBalance) {
            this.startingBalance = startingBalance;
            this.cashBalance = startingBalance;
        }

        double getCashBalance() { return cashBalance; }
        double getStartingBalance() { return startingBalance; }
        Collection<Holding> getHoldings() { return holdings.values(); }
        List<Transaction> getHistory() { return history; }

        boolean buy(Stock stock, int quantity) {
            double cost = stock.getPrice() * quantity;
            if (cost > cashBalance) return false;

            cashBalance -= cost;
            holdings.computeIfAbsent(stock.getSymbol(), Holding::new)
                    .addShares(quantity, stock.getPrice());
            history.add(new Transaction(Transaction.Type.BUY, stock.getSymbol(), quantity, stock.getPrice()));
            return true;
        }

        boolean sell(Stock stock, int quantity) {
            Holding h = holdings.get(stock.getSymbol());
            if (h == null || h.getQuantity() < quantity) return false;

            double proceeds = stock.getPrice() * quantity;
            cashBalance += proceeds;
            boolean soldOut = h.removeShares(quantity);
            if (soldOut) holdings.remove(stock.getSymbol());

            history.add(new Transaction(Transaction.Type.SELL, stock.getSymbol(), quantity, stock.getPrice()));
            return true;
        }

        double getHoldingsValue(Market market) {
            double total = 0;
            for (Holding h : holdings.values()) {
                Stock s = market.getStock(h.getSymbol());
                if (s != null) total += s.getPrice() * h.getQuantity();
            }
            return total;
        }

        double getTotalValue(Market market) {
            return cashBalance + getHoldingsValue(market);
        }

        double getTotalProfitLoss(Market market) {
            return getTotalValue(market) - startingBalance;
        }

        double getTotalProfitLossPercent(Market market) {
            if (startingBalance == 0) return 0;
            return (getTotalProfitLoss(market) / startingBalance) * 100.0;
        }
    }

    // ====================================================
    //  FILE PERSISTENCE
    // ====================================================
    static final String PORTFOLIO_FILE = "portfolio.csv";
    static final String TRANSACTIONS_FILE = "transactions.csv";

    static void savePortfolio(Portfolio p) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(PORTFOLIO_FILE))) {
            pw.println(p.getStartingBalance() + "," + p.getCashBalance());
            for (Holding h : p.getHoldings()) {
                pw.println(h.getSymbol() + "," + h.getQuantity() + "," + h.getTotalCost());
            }
        } catch (IOException e) {
            System.out.println("Warning: could not save portfolio (" + e.getMessage() + ")");
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(TRANSACTIONS_FILE))) {
            for (Transaction t : p.getHistory()) {
                pw.println(t.toFileLine());
            }
        } catch (IOException e) {
            System.out.println("Warning: could not save transaction history (" + e.getMessage() + ")");
        }
    }

    static Portfolio loadPortfolio(double defaultStartingBalance) {
        File f = new File(PORTFOLIO_FILE);
        if (!f.exists()) return new Portfolio(defaultStartingBalance);

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String firstLine = br.readLine();
            if (firstLine == null) return new Portfolio(defaultStartingBalance);

            String[] header = firstLine.split(",");
            double starting = Double.parseDouble(header[0]);
            double cash = Double.parseDouble(header[1]);

            Portfolio p = new Portfolio(starting);
            p.cashBalance = cash;

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                String symbol = parts[0];
                int qty = Integer.parseInt(parts[1]);
                double totalCost = Double.parseDouble(parts[2]);

                Holding h = new Holding(symbol);
                if (qty > 0) h.addShares(qty, totalCost / qty);
                p.holdings.put(symbol, h);
            }

            File tf = new File(TRANSACTIONS_FILE);
            if (tf.exists()) {
                try (BufferedReader tbr = new BufferedReader(new FileReader(tf))) {
                    String tline;
                    while ((tline = tbr.readLine()) != null) {
                        if (tline.trim().isEmpty()) continue;
                        p.history.add(Transaction.fromFileLine(tline));
                    }
                }
            }

            System.out.println("Loaded saved portfolio from " + PORTFOLIO_FILE + ".");
            return p;
        } catch (Exception e) {
            System.out.println("Could not read saved portfolio, starting fresh (" + e.getMessage() + ")");
            return new Portfolio(defaultStartingBalance);
        }
    }

    // ====================================================
    //  APPLICATION / MENU
    // ====================================================
    private static Market market = new Market();
    private static Portfolio portfolio;
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        setupMarket();
        portfolio = loadPortfolio(10000.00);

        System.out.println("=================================================");
        System.out.println("        STOCK TRADING PLATFORM (SIMULATION)");
        System.out.println("=================================================");
        System.out.printf("Cash balance: $%.2f%n", portfolio.getCashBalance());

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Enter your choice: ");

            switch (choice) {
                case 1: market.display(); break;
                case 2: buyStock(); break;
                case 3: sellStock(); break;
                case 4: viewPortfolio(); break;
                case 5: viewTransactionHistory(); break;
                case 6:
                    market.advanceTime();
                    System.out.println("\nMarket advanced one tick — prices updated.");
                    break;
                case 7:
                    savePortfolio(portfolio);
                    System.out.println("\nPortfolio saved to " + PORTFOLIO_FILE + " and " + TRANSACTIONS_FILE + ".");
                    break;
                case 8:
                    savePortfolio(portfolio);
                    running = false;
                    System.out.println("\nPortfolio saved. Goodbye!");
                    break;
                default:
                    System.out.println("\nInvalid choice. Please select a number from 1 to 8.");
            }
        }
        scanner.close();
    }

    private static void setupMarket() {
        market.addStock(new Stock("AAPL", "Apple Inc.", 195.50));
        market.addStock(new Stock("GOOG", "Alphabet Inc.", 167.20));
        market.addStock(new Stock("MSFT", "Microsoft Corp.", 420.75));
        market.addStock(new Stock("AMZN", "Amazon.com Inc.", 185.10));
        market.addStock(new Stock("TSLA", "Tesla Inc.", 248.90));
        market.addStock(new Stock("NFLX", "Netflix Inc.", 680.30));
        market.addStock(new Stock("NVDA", "NVIDIA Corp.", 135.40));
    }

    private static void printMenu() {
        System.out.println("\n-------------------- MENU --------------------");
        System.out.println("1. View market data");
        System.out.println("2. Buy stock");
        System.out.println("3. Sell stock");
        System.out.println("4. View portfolio & performance");
        System.out.println("5. View transaction history");
        System.out.println("6. Advance market (simulate next tick)");
        System.out.println("7. Save portfolio to file");
        System.out.println("8. Save & exit");
        System.out.println("-----------------------------------------------");
    }

    // ---------- Actions ----------

    private static void buyStock() {
        market.display();
        Stock s = promptForStock();
        if (s == null) return;

        int qty = readInt("Quantity to buy: ");
        if (qty <= 0) { System.out.println("Quantity must be positive."); return; }

        double cost = s.getPrice() * qty;
        System.out.printf("Cost: %d x $%.2f = $%.2f (cash available: $%.2f)%n",
                qty, s.getPrice(), cost, portfolio.getCashBalance());

        if (portfolio.buy(s, qty)) {
            System.out.println("Bought " + qty + " share(s) of " + s.getSymbol() + ".");
        } else {
            System.out.println("Insufficient cash balance for this purchase.");
        }
    }

    private static void sellStock() {
        if (portfolio.getHoldings().isEmpty()) {
            System.out.println("\nYou don't own any stock yet.");
            return;
        }

        market.display();
        Stock s = promptForStock();
        if (s == null) return;

        int qty = readInt("Quantity to sell: ");
        if (qty <= 0) { System.out.println("Quantity must be positive."); return; }

        if (portfolio.sell(s, qty)) {
            System.out.printf("Sold %d share(s) of %s for $%.2f.%n", qty, s.getSymbol(), s.getPrice() * qty);
        } else {
            System.out.println("You don't own enough shares of " + s.getSymbol() + " to sell that quantity.");
        }
    }

    private static Stock promptForStock() {
        System.out.print("Enter stock symbol: ");
        String symbol = scanner.nextLine().trim().toUpperCase();
        Stock s = market.getStock(symbol);
        if (s == null) {
            System.out.println("Unknown symbol \"" + symbol + "\".");
            return null;
        }
        return s;
    }

    private static void viewPortfolio() {
        System.out.println("\n=================== PORTFOLIO ===================");
        System.out.printf("Cash balance     : $%.2f%n", portfolio.getCashBalance());
        System.out.printf("Starting balance : $%.2f%n", portfolio.getStartingBalance());

        if (portfolio.getHoldings().isEmpty()) {
            System.out.println("\nNo current stock holdings.");
        } else {
            System.out.println("\nHoldings:");
            System.out.printf("%-8s %-8s %-10s %-12s %-12s %-10s%n",
                    "Symbol", "Qty", "Avg Cost", "Price", "Value", "P/L");
            System.out.println("--------------------------------------------------------------");
            for (Holding h : portfolio.getHoldings()) {
                Stock s = market.getStock(h.getSymbol());
                double price = (s != null) ? s.getPrice() : 0;
                double value = price * h.getQuantity();
                double pl = value - h.getTotalCost();
                System.out.printf("%-8s %-8d $%-9.2f $%-11.2f $%-11.2f $%-9.2f%n",
                        h.getSymbol(), h.getQuantity(), h.getAverageCost(), price, value, pl);
            }
        }

        double totalValue = portfolio.getTotalValue(market);
        double pl = portfolio.getTotalProfitLoss(market);
        double plPercent = portfolio.getTotalProfitLossPercent(market);

        System.out.println("----------------------------------------------------------------");
        System.out.printf("Holdings value   : $%.2f%n", portfolio.getHoldingsValue(market));
        System.out.printf("TOTAL VALUE      : $%.2f%n", totalValue);
        System.out.printf("Overall P/L      : $%.2f (%.2f%%)%n", pl, plPercent);
        System.out.println(pl >= 0 ? "Status           : UP since starting" : "Status           : DOWN since starting");
        System.out.println("==================================================================");
    }

    private static void viewTransactionHistory() {
        List<Transaction> history = portfolio.getHistory();
        System.out.println("\n================= TRANSACTION HISTORY =================");
        if (history.isEmpty()) {
            System.out.println("No transactions yet.");
        } else {
            for (Transaction t : history) {
                System.out.println(t);
            }
        }
        System.out.println("=========================================================");
    }

    // ---------- Input helpers ----------

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }
}
