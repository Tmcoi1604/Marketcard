package marketcard.app;

import java.io.File;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * CSDL SQLite cua phan mem may tram (file data/marketcard.db).
 * Luu: khach hang + khoa cong khai RSA cua the, san pham, giao dich, thong bao.
 * Thong tin ca nhan day du chi nam tren the (da ma hoa).
 */
public class Database implements AutoCloseable {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Connection conn;

    public static class Product {
        public int id;
        public String code;
        public String name;
        public long price;
        public int stock;
    }

    public static class Customer {
        public String cardId;
        public String customerCode;
        public String fullName;
        public String phone;
        public String issuedAt;
    }

    public static class TxRow {
        public long id;
        public String cardId;
        public String type;
        public long amount;
        public Long balanceAfter;
        public Integer cardTxCounter;
        public String status;
        public Boolean signatureValid;
        public String note;
        public String createdAt;
    }

    public static class NotificationRow {
        public String title;
        public String message;
        public String createdAt;
    }

    public static class CartItem {
        public Product product;
        public int quantity;

        public CartItem(Product product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }

        public long total() {
            return product.price * quantity;
        }
    }

    public Database(String path) throws SQLException {
        File f = new File(path).getAbsoluteFile();
        if (f.getParentFile() != null) {
            f.getParentFile().mkdirs();
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Thiếu thư viện SQLite JDBC (lib/sqlite-jdbc-*.jar). Xem README.");
        }
        conn = DriverManager.getConnection("jdbc:sqlite:" + f.getPath());
        createSchema();
        seedProducts();
    }

    private void createSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("CREATE TABLE IF NOT EXISTS customers ("
                    + " card_id TEXT PRIMARY KEY,"
                    + " customer_code TEXT NOT NULL,"
                    + " full_name TEXT NOT NULL,"
                    + " phone TEXT,"
                    + " public_key BLOB NOT NULL,"
                    + " issued_at TEXT NOT NULL,"
                    + " status TEXT NOT NULL DEFAULT 'ACTIVE')");
            st.execute("CREATE TABLE IF NOT EXISTS products ("
                    + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + " code TEXT UNIQUE NOT NULL,"
                    + " name TEXT NOT NULL,"
                    + " price INTEGER NOT NULL,"
                    + " stock INTEGER NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS transactions ("
                    + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + " card_id TEXT NOT NULL,"
                    + " type TEXT NOT NULL,"              // PAY | TOPUP
                    + " amount INTEGER NOT NULL,"
                    + " balance_after INTEGER,"
                    + " card_tx_counter INTEGER,"
                    + " status TEXT NOT NULL,"            // SUCCESS | DECLINED_FUNDS | FAILED
                    + " signature BLOB,"
                    + " signature_valid INTEGER,"
                    + " note TEXT,"
                    + " created_at TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS transaction_items ("
                    + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + " transaction_id INTEGER NOT NULL REFERENCES transactions(id),"
                    + " product_id INTEGER NOT NULL REFERENCES products(id),"
                    + " product_name TEXT NOT NULL,"
                    + " quantity INTEGER NOT NULL,"
                    + " unit_price INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS notifications ("
                    + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + " card_id TEXT,"
                    + " title TEXT NOT NULL,"
                    + " message TEXT NOT NULL,"
                    + " created_at TEXT NOT NULL)");
        }
    }

    private void seedProducts() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM products")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }
        Object[][] seed = {
            {"SP001", "Gạo ST25 túi 5kg", 185000L, 50},
            {"SP002", "Sữa tươi Vinamilk 1L", 32000L, 120},
            {"SP003", "Trứng gà hộp 10 quả", 35000L, 80},
            {"SP004", "Dầu ăn Neptune 1L", 52000L, 60},
            {"SP005", "Mì Hảo Hảo thùng 30 gói", 115000L, 40},
            {"SP006", "Nước mắm Nam Ngư 500ml", 28000L, 70},
            {"SP007", "Táo Envy (kg)", 120000L, 30},
            {"SP008", "Thịt ba chỉ (500g)", 85000L, 25},
            {"SP009", "Bột giặt OMO 3kg", 159000L, 35},
            {"SP010", "Coca-Cola lốc 6 lon", 54000L, 90},
            {"SP011", "Bánh mì sandwich", 25000L, 60},
            {"SP012", "Cà phê G7 hộp 18 gói", 49000L, 55},
        };
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO products(code, name, price, stock) VALUES (?,?,?,?)")) {
            for (Object[] r : seed) {
                ps.setString(1, (String) r[0]);
                ps.setString(2, (String) r[1]);
                ps.setLong(3, (Long) r[2]);
                ps.setInt(4, (Integer) r[3]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ===================== Khach hang =====================

    public void saveCustomer(String cardId, CardInfo info, PublicKey pub) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO customers(card_id, customer_code, full_name, phone, public_key, issued_at, status)"
                + " VALUES (?,?,?,?,?,?,'ACTIVE')")) {
            ps.setString(1, cardId);
            ps.setString(2, info.customerCode);
            ps.setString(3, info.fullName);
            ps.setString(4, info.phone);
            ps.setBytes(5, pub.getEncoded());
            ps.setString(6, now());
            ps.executeUpdate();
        }
    }

    public void updateCustomerInfo(String cardId, CardInfo info) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE customers SET customer_code=?, full_name=?, phone=? WHERE card_id=?")) {
            ps.setString(1, info.customerCode);
            ps.setString(2, info.fullName);
            ps.setString(3, info.phone);
            ps.setString(4, cardId);
            ps.executeUpdate();
        }
    }

    public PublicKey findPublicKey(String cardId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT public_key FROM customers WHERE card_id=?")) {
            ps.setString(1, cardId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(rs.getBytes(1)));
            }
        }
    }

    public Customer findCustomer(String cardId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT card_id, customer_code, full_name, phone, issued_at FROM customers WHERE card_id=?")) {
            ps.setString(1, cardId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Customer c = new Customer();
                c.cardId = rs.getString(1);
                c.customerCode = rs.getString(2);
                c.fullName = rs.getString(3);
                c.phone = rs.getString(4);
                c.issuedAt = rs.getString(5);
                return c;
            }
        }
    }

    // ===================== San pham =====================

    public List<Product> listProducts() throws SQLException {
        List<Product> list = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, code, name, price, stock FROM products ORDER BY code")) {
            while (rs.next()) {
                Product p = new Product();
                p.id = rs.getInt(1);
                p.code = rs.getString(2);
                p.name = rs.getString(3);
                p.price = rs.getLong(4);
                p.stock = rs.getInt(5);
                list.add(p);
            }
        }
        return list;
    }

    // ===================== Giao dich =====================

    /** Luu giao dich (va chi tiet gio hang neu co) trong 1 transaction CSDL. */
    public long saveTransaction(String cardId, String type, long amount, TxReceipt receipt, String status,
                                String note, List<CartItem> items) throws SQLException {
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            long txId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO transactions(card_id, type, amount, balance_after, card_tx_counter, status,"
                    + " signature, signature_valid, note, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, cardId);
                ps.setString(2, type);
                ps.setLong(3, amount);
                if (receipt != null) {
                    ps.setLong(4, receipt.newBalance);
                    ps.setInt(5, receipt.txCounter);
                    ps.setBytes(7, receipt.signature);
                    if (receipt.signatureValid == null) {
                        ps.setNull(8, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(8, receipt.signatureValid ? 1 : 0);
                    }
                } else {
                    ps.setNull(4, java.sql.Types.INTEGER);
                    ps.setNull(5, java.sql.Types.INTEGER);
                    ps.setNull(7, java.sql.Types.BLOB);
                    ps.setNull(8, java.sql.Types.INTEGER);
                }
                ps.setString(6, status);
                ps.setString(9, note);
                ps.setString(10, now());
                ps.executeUpdate();
            }
            // SQLite JDBC khong ho tro getGeneratedKeys -> lay id vua chen tren cung ket noi
            try (Statement st = conn.createStatement();
                 ResultSet keys = st.executeQuery("SELECT last_insert_rowid()")) {
                keys.next();
                txId = keys.getLong(1);
            }
            if (items != null && !items.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO transaction_items(transaction_id, product_id, product_name, quantity, unit_price)"
                        + " VALUES (?,?,?,?,?)");
                     PreparedStatement stock = conn.prepareStatement(
                        "UPDATE products SET stock = stock - ? WHERE id = ?")) {
                    for (CartItem it : items) {
                        ps.setLong(1, txId);
                        ps.setInt(2, it.product.id);
                        ps.setString(3, it.product.name);
                        ps.setInt(4, it.quantity);
                        ps.setLong(5, it.product.price);
                        ps.addBatch();
                        if ("SUCCESS".equals(status)) {
                            stock.setInt(1, it.quantity);
                            stock.setInt(2, it.product.id);
                            stock.addBatch();
                        }
                    }
                    ps.executeBatch();
                    stock.executeBatch();
                }
            }
            conn.commit();
            return txId;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(auto);
        }
    }

    public List<TxRow> listTransactions(String cardId) throws SQLException {
        List<TxRow> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, card_id, type, amount, balance_after, card_tx_counter, status, signature_valid, note,"
                + " created_at FROM transactions WHERE card_id=? ORDER BY id DESC")) {
            ps.setString(1, cardId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TxRow r = new TxRow();
                    r.id = rs.getLong(1);
                    r.cardId = rs.getString(2);
                    r.type = rs.getString(3);
                    r.amount = rs.getLong(4);
                    long b = rs.getLong(5);
                    r.balanceAfter = rs.wasNull() ? null : b;
                    int c = rs.getInt(6);
                    r.cardTxCounter = rs.wasNull() ? null : c;
                    r.status = rs.getString(7);
                    int v = rs.getInt(8);
                    r.signatureValid = rs.wasNull() ? null : v == 1;
                    r.note = rs.getString(9);
                    r.createdAt = rs.getString(10);
                    list.add(r);
                }
            }
        }
        return list;
    }

    // ===================== Thong bao =====================

    public void addNotification(String cardId, String title, String message) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO notifications(card_id, title, message, created_at) VALUES (?,?,?,?)")) {
            ps.setString(1, cardId);
            ps.setString(2, title);
            ps.setString(3, message);
            ps.setString(4, now());
            ps.executeUpdate();
        }
    }

    public List<NotificationRow> listNotifications(String cardId) throws SQLException {
        List<NotificationRow> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT title, message, created_at FROM notifications WHERE card_id=? ORDER BY id DESC")) {
            ps.setString(1, cardId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    NotificationRow n = new NotificationRow();
                    n.title = rs.getString(1);
                    n.message = rs.getString(2);
                    n.createdAt = rs.getString(3);
                    list.add(n);
                }
            }
        }
        return list;
    }

    private static String now() {
        return LocalDateTime.now().format(TS);
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException ignored) {
        }
    }
}
