package marketcard.app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;

/** Giao dien chinh cua phan mem may tram sieu thi. */
public class MainFrame extends JFrame {

    private final SmartCardWork card = new SmartCardWork();
    private final Database db;
    private TrayIcon trayIcon;

    // ---- trang thai ----
    private CardStatus status;
    private PublicKey cardPub;
    private boolean authentic;
    private boolean loggedIn;
    private byte[] issuePhoto;

    // ---- header ----
    private final JComboBox<String> cbReader = new JComboBox<>();
    private final JButton btnConnect = Ui.button("Kết nối", Ui.ACCENT);
    private final JLabel chipConn = Ui.chip("○ Chưa kết nối", Ui.GRAY);
    private final JLabel chipCard = Ui.chip("Thẻ: —", Ui.GRAY);
    private final JLabel chipRsa = Ui.chip("RSA: —", Ui.GRAY);
    private final JLabel chipPin = Ui.chip("PIN: —", Ui.GRAY);
    private final JLabel chipLock = Ui.chip("", Ui.DANGER);
    private final JLabel lblBusy = new JLabel(" ");

    // ---- phat hanh ----
    private final JTextField isCode = Ui.field(22);
    private final JTextField isName = Ui.field(22);
    private final JTextField isDob = Ui.field(22);
    private final JTextField isPhone = Ui.field(22);
    private final JTextField isAddress = Ui.field(22);
    private final JPasswordField isPin = Ui.pinField();
    private final JPasswordField isPin2 = Ui.pinField();
    private final JLabel isPhotoBox = Ui.photoBox(120, 150);

    // ---- tai khoan ----
    private final JLabel acPhoto = Ui.photoBox(150, 190);
    private final JLabel acName = new JLabel("Chưa đăng nhập", SwingConstants.CENTER);
    private final JLabel acBalance = new JLabel("—", SwingConstants.CENTER);
    private final JTextField acCode = Ui.field(22);
    private final JTextField acFullName = Ui.field(22);
    private final JTextField acDob = Ui.field(22);
    private final JTextField acPhone = Ui.field(22);
    private final JTextField acAddress = Ui.field(22);
    private final JButton btnLogin = Ui.button("Đăng nhập bằng PIN", Ui.PRIMARY);
    private final JButton btnLogout = Ui.button("Đăng xuất", Ui.GRAY);
    private final JButton btnSaveInfo = Ui.button("Lưu thông tin", Ui.PRIMARY);
    private final JButton btnChangePhoto = Ui.button("Đổi ảnh", Ui.INFO);
    private final JButton btnChangePin = Ui.button("Đổi mã PIN", Ui.ACCENT);

    // ---- thanh toan ----
    private final DefaultTableModel productModel = readOnlyModel("Mã", "Tên sản phẩm", "Đơn giá", "Tồn kho");
    private final JTable productTable = new JTable(productModel);
    private final DefaultTableModel cartModel = readOnlyModel("Sản phẩm", "SL", "Đơn giá", "Thành tiền");
    private final JTable cartTable = new JTable(cartModel);
    private final JSpinner spQty = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
    private final JLabel lblTotal = new JLabel("0 đ");
    private final List<Database.Product> products = new ArrayList<>();
    private final List<Database.CartItem> cart = new ArrayList<>();

    // ---- nap tien ----
    private final JTextField tfTopUp = Ui.field(16);

    // ---- lich su ----
    private final DefaultTableModel txModel = readOnlyModel("#", "Thời gian", "Loại", "Số tiền", "Số dư sau",
            "Trạng thái", "Chữ ký RSA", "Ghi chú");
    private final DefaultTableModel notiModel = readOnlyModel("Thời gian", "Tiêu đề", "Nội dung");

    // ---- nhat ky APDU ----
    private final JTextArea logArea = new JTextArea();

    private record ConnectResult(CardStatus status, PublicKey pub, boolean authentic) { }

    private record LoginResult(CardInfo info, byte[] photo, long balance, CardStatus status) { }

    private record PayOutcome(TxReceipt receipt, boolean declined, long balance, CardStatus status) { }

    public MainFrame(Database db) {
        super("MarketCard – Thẻ thanh toán siêu thị (Smart Card)");
        this.db = db;
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                card.disconnect();
                db.close();
                if (trayIcon != null) {
                    SystemTray.getSystemTray().remove(trayIcon);
                }
                dispose();
                System.exit(0);
            }
        });

        card.setLogger(this::log);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Ui.BG);
        root.add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Ui.FONT_BOLD);
        tabs.addTab("  Phát hành thẻ  ", wrap(buildIssueTab()));
        tabs.addTab("  Tài khoản chủ thẻ  ", wrap(buildAccountTab()));
        tabs.addTab("  Thanh toán  ", wrap(buildPosTab()));
        tabs.addTab("  Nạp tiền  ", wrap(buildTopUpTab()));
        tabs.addTab("  Lịch sử & Thông báo  ", wrap(buildHistoryTab()));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, buildLogPanel());
        split.setResizeWeight(0.8);
        split.setBorder(null);
        split.setDividerSize(6);
        root.add(split, BorderLayout.CENTER);

        setContentPane(root);
        setSize(1200, 820);
        setMinimumSize(new Dimension(1050, 700));
        setLocationRelativeTo(null);

        setupTray();
        refreshReaders();
        loadProducts();
        setAccountEditable(false);
        updateChips();
        new Timer(1000, e -> updateLockChip()).start();
    }

    // =====================================================================
    //  HEADER
    // =====================================================================
    private JComponent buildHeader() {
        JPanel header = Ui.gradient(Ui.PRIMARY_DARK, Ui.PRIMARY);
        header.setLayout(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel t1 = new JLabel("MARKETCARD");
        t1.setFont(new Font("Segoe UI", Font.BOLD, 24));
        t1.setForeground(Color.WHITE);
        JLabel t2 = new JLabel("Thẻ thanh toán siêu thị • Java Card • PIN • AES • RSA");
        t2.setFont(Ui.FONT);
        t2.setForeground(new Color(0xD1FAE5));
        titles.add(t1);
        titles.add(t2);
        header.add(titles, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        right.setOpaque(false);
        JLabel lr = new JLabel("Đầu đọc:");
        lr.setForeground(Color.WHITE);
        lr.setFont(Ui.FONT);
        cbReader.setFont(Ui.FONT);
        cbReader.setPreferredSize(new Dimension(260, 32));
        JButton btnRefresh = Ui.button("Làm mới", Ui.PRIMARY_DARK);
        btnRefresh.setToolTipText("Làm mới danh sách đầu đọc");
        btnRefresh.addActionListener(e -> refreshReaders());
        btnConnect.addActionListener(e -> toggleConnect());
        right.add(lr);
        right.add(cbReader);
        right.add(btnRefresh);
        right.add(btnConnect);
        header.add(right, BorderLayout.EAST);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.BORDER));
        lblBusy.setFont(Ui.FONT);
        lblBusy.setForeground(Ui.INFO);
        chipLock.setVisible(false);
        bar.add(chipConn);
        bar.add(chipCard);
        bar.add(chipRsa);
        bar.add(chipPin);
        bar.add(chipLock);
        bar.add(Box.createHorizontalStrut(12));
        bar.add(lblBusy);

        JPanel top = new JPanel(new BorderLayout());
        top.add(header, BorderLayout.NORTH);
        top.add(bar, BorderLayout.SOUTH);
        return top;
    }

    // =====================================================================
    //  TAB 1: PHAT HANH THE
    // =====================================================================
    private JComponent buildIssueTab() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);

        JPanel left = Ui.card("Thông tin khách hàng");
        left.setLayout(new BorderLayout());
        JPanel form = Ui.form();
        Ui.addRow(form, 0, "Mã khách hàng", isCode);
        Ui.addRow(form, 1, "Họ và tên", isName);
        Ui.addRow(form, 2, "Ngày sinh (dd/MM/yyyy)", isDob);
        Ui.addRow(form, 3, "Số điện thoại", isPhone);
        Ui.addRow(form, 4, "Địa chỉ", isAddress);
        left.add(form, BorderLayout.NORTH);
        JLabel note = new JLabel("<html><div style='width:420px;color:#64748B'>"
                + "Thông tin cá nhân và ảnh được <b>mã hóa AES-128</b> ngay trên thẻ bằng khóa dữ liệu, "
                + "khóa này lại được bảo vệ bởi khóa sinh ra từ mã PIN. Khi phát hành, thẻ tự sinh "
                + "<b>cặp khóa RSA-1024</b>; khóa công khai được lưu vào CSDL để xác thực thẻ và "
                + "kiểm tra chữ ký biên lai.</div></html>");
        note.setFont(Ui.FONT);
        note.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        left.add(note, BorderLayout.CENTER);

        JPanel right = Ui.card("Ảnh & mã PIN ban đầu");
        right.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.insets = new Insets(4, 4, 4, 4);
        right.add(isPhotoBox, c);
        JButton btnPhoto = Ui.button("Chọn ảnh…", Ui.INFO);
        btnPhoto.addActionListener(e -> {
            byte[] p = choosePhoto();
            if (p != null) {
                issuePhoto = p;
                showPhoto(isPhotoBox, p, 120, 150);
            }
        });
        c.gridy = 1;
        right.add(btnPhoto, c);
        JPanel pinForm = Ui.form();
        Ui.addRow(pinForm, 0, "Mã PIN (4-8 số)", isPin);
        Ui.addRow(pinForm, 1, "Nhập lại PIN", isPin2);
        c.gridy = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        right.add(pinForm, c);
        JButton btnIssue = Ui.button("KHỞI TẠO / PHÁT HÀNH THẺ", Ui.PRIMARY);
        btnIssue.addActionListener(e -> issueCard());
        c.gridy = 3;
        c.insets = new Insets(14, 4, 4, 4);
        right.add(btnIssue, c);

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.insets = new Insets(0, 0, 0, 16);
        g.weightx = 0.62;
        g.weighty = 1;
        root.add(left, g);
        g.gridx = 1;
        g.weightx = 0.38;
        g.insets = new Insets(0, 0, 0, 0);
        root.add(right, g);
        return root;
    }

    private void issueCard() {
        if (!requireConnected()) {
            return;
        }
        if (status != null && status.issued) {
            error("Thẻ này đã được phát hành (mã thẻ " + status.cardId + ").\nHãy dùng thẻ trắng khác.");
            return;
        }
        String pin = new String(isPin.getPassword());
        String pin2 = new String(isPin2.getPassword());
        if (isCode.getText().isBlank() || isName.getText().isBlank()) {
            error("Vui lòng nhập Mã khách hàng và Họ tên.");
            return;
        }
        if (!pin.matches("\\d{4,8}")) {
            error("Mã PIN phải gồm 4-8 chữ số.");
            return;
        }
        if (!pin.equals(pin2)) {
            error("Mã PIN nhập lại không khớp.");
            return;
        }
        CardInfo info = new CardInfo(isCode.getText(), isName.getText(), isDob.getText(), isPhone.getText(),
                isAddress.getText());
        try {
            info.toBytes();
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
            return;
        }
        byte[] photo = issuePhoto;
        runCard("Đang khởi tạo thẻ và sinh khóa RSA…", () -> {
            String id = card.initCard(pin, info);
            if (photo != null) {
                card.uploadPhoto(photo);
            }
            PublicKey pub = card.getPublicKey();
            db.saveCustomer(id, info, pub);
            boolean ok = card.authenticate(id, pub);
            card.logout();
            return new ConnectResult(card.getStatus(), pub, ok);
        }, r -> {
            status = r.status();
            cardPub = r.pub();
            authentic = r.authentic();
            loggedIn = false;
            updateChips();
            isPin.setText("");
            isPin2.setText("");
            log("Phát hành thẻ thành công. Mã thẻ: " + status.cardId);
            notifyUser("Phát hành thẻ thành công",
                    "Khách hàng: " + info.fullName + "\nMã thẻ: " + status.cardId
                    + "\nKhóa công khai RSA đã được lưu vào CSDL.", TrayIcon.MessageType.INFO, true);
            clearIssueForm();
        });
    }

    private void clearIssueForm() {
        for (JTextField f : new JTextField[]{isCode, isName, isDob, isPhone, isAddress}) {
            f.setText("");
        }
        issuePhoto = null;
        isPhotoBox.setIcon(null);
        isPhotoBox.setText("Chưa có ảnh");
    }

    // =====================================================================
    //  TAB 2: TAI KHOAN CHU THE
    // =====================================================================
    private JComponent buildAccountTab() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);

        JPanel left = Ui.card("Chủ thẻ");
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        acPhoto.setAlignmentX(Component.CENTER_ALIGNMENT);
        acName.setFont(Ui.FONT_TITLE);
        acName.setForeground(Ui.TEXT);
        acName.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel lb = new JLabel("SỐ DƯ", SwingConstants.CENTER);
        lb.setFont(Ui.FONT_BOLD);
        lb.setForeground(Ui.GRAY);
        lb.setAlignmentX(Component.CENTER_ALIGNMENT);
        acBalance.setFont(Ui.FONT_BIG);
        acBalance.setForeground(Ui.PRIMARY);
        acBalance.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel btns = new JPanel(new GridLayout(2, 1, 0, 8));
        btns.setOpaque(false);
        btns.add(btnLogin);
        btns.add(btnLogout);
        btns.setMaximumSize(new Dimension(240, 90));
        btns.setAlignmentX(Component.CENTER_ALIGNMENT);
        left.add(acPhoto);
        left.add(Box.createVerticalStrut(10));
        left.add(acName);
        left.add(Box.createVerticalStrut(14));
        left.add(lb);
        left.add(acBalance);
        left.add(Box.createVerticalStrut(18));
        left.add(btns);

        JPanel right = Ui.card("Thông tin cá nhân (lưu mã hóa AES trên thẻ)");
        right.setLayout(new BorderLayout());
        JPanel form = Ui.form();
        Ui.addRow(form, 0, "Mã khách hàng", acCode);
        Ui.addRow(form, 1, "Họ và tên", acFullName);
        Ui.addRow(form, 2, "Ngày sinh", acDob);
        Ui.addRow(form, 3, "Số điện thoại", acPhone);
        Ui.addRow(form, 4, "Địa chỉ", acAddress);
        right.add(form, BorderLayout.NORTH);
        right.add(Ui.row(btnSaveInfo, btnChangePhoto, btnChangePin), BorderLayout.SOUTH);

        btnLogin.addActionListener(e -> login());
        btnLogout.addActionListener(e -> logout());
        btnSaveInfo.addActionListener(e -> saveInfo());
        btnChangePhoto.addActionListener(e -> changePhoto());
        btnChangePin.addActionListener(e -> changePin());

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.weighty = 1;
        g.weightx = 0.3;
        g.insets = new Insets(0, 0, 0, 16);
        root.add(left, g);
        g.gridx = 1;
        g.weightx = 0.7;
        g.insets = new Insets(0, 0, 0, 0);
        root.add(right, g);
        return root;
    }

    private void login() {
        if (!requireIssued() || !checkNotLocked()) {
            return;
        }
        String pin = PinDialog.ask(this, "Xác thực chủ thẻ", "Nhập mã PIN của thẻ:", status);
        if (pin == null) {
            return;
        }
        runCard("Đang xác thực PIN và giải mã thông tin…", () -> {
            card.verifyPin(pin);
            CardInfo info = card.getInfo();
            byte[] photo = card.downloadPhoto();
            long bal = card.getBalance()[0];
            return new LoginResult(info, photo, bal, card.getStatus());
        }, r -> {
            loggedIn = true;
            status = r.status();
            fillAccount(r.info(), r.photo(), r.balance());
            setAccountEditable(true);
            updateChips();
            loadHistory();
            log("Xác thực PIN thành công – đã giải mã thông tin chủ thẻ.");
        });
    }

    private void logout() {
        if (!card.isConnected()) {
            clearAccount();
            return;
        }
        runCard("Đang đăng xuất…", () -> {
            card.logout();
            return card.getStatus();
        }, s -> {
            status = s;
            clearAccount();
            updateChips();
        });
    }

    private void saveInfo() {
        if (!requireLogin()) {
            return;
        }
        CardInfo info = new CardInfo(acCode.getText(), acFullName.getText(), acDob.getText(), acPhone.getText(),
                acAddress.getText());
        if (info.customerCode.isEmpty() || info.fullName.isEmpty()) {
            error("Mã khách hàng và Họ tên không được để trống.");
            return;
        }
        try {
            info.toBytes();
        } catch (IllegalArgumentException ex) {
            error(ex.getMessage());
            return;
        }
        String id = status.cardId;
        runCard("Đang mã hóa và ghi thông tin xuống thẻ…", () -> {
            card.updateInfo(info);
            db.updateCustomerInfo(id, info);
            return card.getInfo();
        }, i -> {
            acName.setText(i.fullName);
            info("Đã cập nhật thông tin cá nhân trên thẻ.");
        });
    }

    private void changePhoto() {
        if (!requireLogin()) {
            return;
        }
        byte[] p = choosePhoto();
        if (p == null) {
            return;
        }
        runCard("Đang ghi ảnh (mã hóa AES) xuống thẻ…", () -> {
            card.uploadPhoto(p);
            return card.downloadPhoto();
        }, photo -> {
            showPhoto(acPhoto, photo, 150, 190);
            info("Đã cập nhật ảnh chủ thẻ (" + p.length + " byte).");
        });
    }

    private void changePin() {
        if (!requireIssued() || !checkNotLocked()) {
            return;
        }
        String[] pins = PinDialog.askChange(this);
        if (pins == null) {
            return;
        }
        runCard("Đang đổi mã PIN…", () -> {
            card.changePin(pins[0], pins[1]);
            return card.getStatus();
        }, s -> {
            status = s;
            updateChips();
            notifyUser("Đổi mã PIN thành công", "Mã PIN của thẻ " + s.cardId + " đã được thay đổi.",
                    TrayIcon.MessageType.INFO, true);
        });
    }

    private void fillAccount(CardInfo info, byte[] photo, long balance) {
        acCode.setText(info.customerCode);
        acFullName.setText(info.fullName);
        acDob.setText(info.birthDate);
        acPhone.setText(info.phone);
        acAddress.setText(info.address);
        acName.setText(info.fullName.isEmpty() ? "(chưa có tên)" : info.fullName);
        acBalance.setText(Ui.money(balance));
        if (photo != null) {
            showPhoto(acPhoto, photo, 150, 190);
        } else {
            acPhoto.setIcon(null);
            acPhoto.setText("Chưa có ảnh");
        }
    }

    private void clearAccount() {
        loggedIn = false;
        for (JTextField f : new JTextField[]{acCode, acFullName, acDob, acPhone, acAddress}) {
            f.setText("");
        }
        acName.setText("Chưa đăng nhập");
        acBalance.setText("—");
        acPhoto.setIcon(null);
        acPhoto.setText("Chưa có ảnh");
        setAccountEditable(false);
    }

    private void setAccountEditable(boolean on) {
        for (JTextField f : new JTextField[]{acCode, acFullName, acDob, acPhone, acAddress}) {
            f.setEditable(on);
        }
        btnSaveInfo.setEnabled(on);
        btnChangePhoto.setEnabled(on);
        btnLogout.setEnabled(on);
    }

    // =====================================================================
    //  TAB 3: THANH TOAN (POS)
    // =====================================================================
    private JComponent buildPosTab() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);

        JPanel left = Ui.card("Sản phẩm");
        left.setLayout(new BorderLayout(0, 10));
        Ui.styleTable(productTable);
        productTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        productTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        productTable.getColumnModel().getColumn(1).setPreferredWidth(240);
        productTable.getColumnModel().getColumn(2).setCellRenderer(Ui.rightRenderer());
        productTable.getColumnModel().getColumn(3).setCellRenderer(Ui.rightRenderer());
        left.add(new JScrollPane(productTable), BorderLayout.CENTER);
        spQty.setFont(Ui.FONT);
        JButton btnAdd = Ui.button("Thêm vào giỏ  →", Ui.PRIMARY);
        btnAdd.addActionListener(e -> addToCart());
        left.add(Ui.row(Ui.label("Số lượng:"), spQty, btnAdd), BorderLayout.SOUTH);

        JPanel right = Ui.card("Giỏ hàng");
        right.setLayout(new BorderLayout(0, 10));
        Ui.styleTable(cartTable);
        cartTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        cartTable.getColumnModel().getColumn(1).setCellRenderer(Ui.rightRenderer());
        cartTable.getColumnModel().getColumn(2).setCellRenderer(Ui.rightRenderer());
        cartTable.getColumnModel().getColumn(3).setCellRenderer(Ui.rightRenderer());
        right.add(new JScrollPane(cartTable), BorderLayout.CENTER);

        JButton btnRemove = Ui.button("Xóa dòng", Ui.GRAY);
        btnRemove.addActionListener(e -> {
            int r = cartTable.getSelectedRow();
            if (r >= 0) {
                cart.remove(r);
                refreshCart();
            }
        });
        JButton btnClear = Ui.button("Xóa giỏ", Ui.GRAY);
        btnClear.addActionListener(e -> {
            cart.clear();
            refreshCart();
        });
        lblTotal.setFont(Ui.FONT_BIG);
        lblTotal.setForeground(Ui.DANGER);
        JButton btnPay = Ui.button("THANH TOÁN BẰNG THẺ", Ui.PRIMARY);
        btnPay.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btnPay.addActionListener(e -> pay());

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(Ui.row(btnRemove, btnClear), BorderLayout.WEST);
        JPanel totalBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        totalBox.setOpaque(false);
        totalBox.add(Ui.label("Tổng cộng:"));
        totalBox.add(lblTotal);
        bottom.add(totalBox, BorderLayout.EAST);
        JPanel payRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        payRow.setOpaque(false);
        payRow.add(btnPay);
        bottom.add(payRow, BorderLayout.SOUTH);
        right.add(bottom, BorderLayout.SOUTH);

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.weighty = 1;
        g.weightx = 0.5;
        g.insets = new Insets(0, 0, 0, 16);
        root.add(left, g);
        g.gridx = 1;
        g.insets = new Insets(0, 0, 0, 0);
        root.add(right, g);
        return root;
    }

    private void loadProducts() {
        try {
            products.clear();
            products.addAll(db.listProducts());
            productModel.setRowCount(0);
            for (Database.Product p : products) {
                productModel.addRow(new Object[]{p.code, p.name, Ui.money(p.price), p.stock});
            }
        } catch (Exception e) {
            error("Không tải được danh sách sản phẩm: " + e.getMessage());
        }
    }

    private void addToCart() {
        int r = productTable.getSelectedRow();
        if (r < 0) {
            info("Hãy chọn một sản phẩm.");
            return;
        }
        Database.Product p = products.get(productTable.convertRowIndexToModel(r));
        int qty = (Integer) spQty.getValue();
        int inCart = 0;
        for (Database.CartItem it : cart) {
            if (it.product.id == p.id) {
                inCart += it.quantity;
            }
        }
        if (inCart + qty > p.stock) {
            error("Không đủ hàng trong kho (còn " + p.stock + ").");
            return;
        }
        for (Database.CartItem it : cart) {
            if (it.product.id == p.id) {
                it.quantity += qty;
                refreshCart();
                return;
            }
        }
        cart.add(new Database.CartItem(p, qty));
        refreshCart();
    }

    private void refreshCart() {
        cartModel.setRowCount(0);
        for (Database.CartItem it : cart) {
            cartModel.addRow(new Object[]{it.product.name, it.quantity, Ui.money(it.product.price),
                Ui.money(it.total())});
        }
        lblTotal.setText(Ui.money(cartTotal()));
    }

    private long cartTotal() {
        long t = 0;
        for (Database.CartItem it : cart) {
            t += it.total();
        }
        return t;
    }

    private void pay() {
        long total = cartTotal();
        if (total <= 0) {
            info("Giỏ hàng đang trống.");
            return;
        }
        if (!requireAuthentic() || !checkNotLocked()) {
            return;
        }
        String pin = PinDialog.ask(this, "Xác nhận thanh toán",
                "Tổng tiền thanh toán: <b>" + Ui.money(total) + "</b>\nNhập mã PIN để xác nhận:", status);
        if (pin == null) {
            return;
        }
        List<Database.CartItem> items = new ArrayList<>();
        for (Database.CartItem it : cart) {
            items.add(new Database.CartItem(it.product, it.quantity));
        }
        String cardId = status.cardId;
        PublicKey pub = cardPub;
        runCard("Đang thanh toán…", () -> {
            try {
                TxReceipt r = card.pay(pin, total, pub);
                return new PayOutcome(r, false, r.newBalance, card.getStatus());
            } catch (CardOpException e) {
                if (e.isInsufficientFunds()) {
                    // PIN dung nen phien da mo -> doc so du de bao cho khach
                    long bal = card.getBalance()[0];
                    return new PayOutcome(null, true, bal, card.getStatus());
                }
                throw e;
            }
        }, o -> {
            status = o.status();
            updateChips();
            if (o.declined()) {
                saveTx(cardId, "PAY", total, null, "DECLINED_FUNDS",
                        "Số dư không đủ (có " + Ui.money(o.balance()) + ")", items);
                notifyUser("Giao dịch bị TỪ CHỐI",
                        "Số dư trong thẻ không đủ để thanh toán.\n"
                        + "Cần thanh toán: " + Ui.money(total) + "\n"
                        + "Số dư hiện tại: " + Ui.money(o.balance()) + "\n"
                        + "Còn thiếu: " + Ui.money(total - o.balance()) + "\n"
                        + "Vui lòng nạp thêm tiền vào thẻ.", TrayIcon.MessageType.ERROR, true);
            } else {
                TxReceipt r = o.receipt();
                saveTx(cardId, "PAY", total, r, "SUCCESS", items.size() + " mặt hàng", items);
                cart.clear();
                refreshCart();
                loadProducts();
                if (loggedIn) {
                    acBalance.setText(Ui.money(r.newBalance));
                }
                notifyUser("Thanh toán thành công",
                        "Đã trừ " + Ui.money(total) + ". Số dư còn lại: " + Ui.money(r.newBalance),
                        TrayIcon.MessageType.INFO, false);
                showReceipt(r, items);
            }
            loadHistory();
        }, e -> {
            if (e instanceof CardOpException ce && (ce.isWrongPin() || ce.isLocked())) {
                saveTx(cardId, "PAY", total, null, "FAILED_PIN", ce.getMessage(), null);
            }
            handleError(e);
        });
    }

    private void showReceipt(TxReceipt r, List<Database.CartItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><div style='width:360px;font-family:Segoe UI'>");
        sb.append("<h2 style='color:#0E7C66;margin:0'>BIÊN LAI THANH TOÁN</h2>");
        sb.append("<p>").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
                .append(" – Thẻ ").append(status.cardId).append("</p><table width='100%'>");
        for (Database.CartItem it : items) {
            sb.append("<tr><td>").append(it.product.name).append(" × ").append(it.quantity)
                    .append("</td><td align='right'>").append(Ui.money(it.total())).append("</td></tr>");
        }
        sb.append("<tr><td><b>TỔNG</b></td><td align='right'><b>").append(Ui.money(r.amount))
                .append("</b></td></tr>");
        sb.append("<tr><td>Số dư còn lại</td><td align='right'>").append(Ui.money(r.newBalance))
                .append("</td></tr></table>");
        sb.append("<p>Số giao dịch trên thẻ: #").append(r.txCounter).append("<br>");
        sb.append("Chữ ký RSA của thẻ: ").append(sigText(r.signatureValid)).append("</p>");
        sb.append("</div></html>");
        JOptionPane.showMessageDialog(this, sb.toString(), "Biên lai", JOptionPane.PLAIN_MESSAGE);
    }

    // =====================================================================
    //  TAB 4: NAP TIEN
    // =====================================================================
    private JComponent buildTopUpTab() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setOpaque(false);
        JPanel box = Ui.card("Nạp tiền vào thẻ");
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        tfTopUp.setFont(new Font("Segoe UI", Font.BOLD, 22));
        tfTopUp.setHorizontalAlignment(SwingConstants.RIGHT);
        ((AbstractDocument) tfTopUp.getDocument()).setDocumentFilter(new Ui.DigitsFilter(10));
        tfTopUp.setMaximumSize(new Dimension(420, 50));
        tfTopUp.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel quick = new JPanel(new GridLayout(1, 4, 8, 0));
        quick.setOpaque(false);
        quick.setAlignmentX(Component.LEFT_ALIGNMENT);
        quick.setMaximumSize(new Dimension(420, 40));
        for (long v : new long[]{50_000, 100_000, 200_000, 500_000}) {
            JButton b = Ui.button(v / 1000 + "K", Ui.INFO);
            b.addActionListener(e -> tfTopUp.setText(String.valueOf(v)));
            quick.add(b);
        }
        JButton btn = Ui.button("NẠP TIỀN", Ui.PRIMARY);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.addActionListener(e -> topUp());
        JLabel hint = new JLabel("<html><div style='width:400px;color:#64748B'>Mỗi lần nạp tiền đều yêu cầu "
                + "chủ thẻ nhập mã PIN.<br>Thẻ tự cộng tiền và ký biên lai bằng khóa bí mật RSA.</div></html>");
        hint.setFont(Ui.FONT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel l = Ui.label("Số tiền (VNĐ):");
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(l);
        box.add(Box.createVerticalStrut(6));
        box.add(tfTopUp);
        box.add(Box.createVerticalStrut(10));
        box.add(quick);
        box.add(Box.createVerticalStrut(16));
        box.add(btn);
        box.add(Box.createVerticalStrut(16));
        box.add(hint);
        box.setPreferredSize(new Dimension(480, 400));
        root.add(box);
        return root;
    }

    private void topUp() {
        long amount;
        try {
            amount = Long.parseLong(tfTopUp.getText().trim());
        } catch (NumberFormatException e) {
            amount = 0;
        }
        if (amount <= 0 || amount > SmartCardWork.MAX_AMOUNT) {
            error("Số tiền nạp không hợp lệ.");
            return;
        }
        if (!requireAuthentic() || !checkNotLocked()) {
            return;
        }
        long amt = amount;
        String pin = PinDialog.ask(this, "Xác nhận nạp tiền",
                "Nạp <b>" + Ui.money(amt) + "</b> vào thẻ.\nNhập mã PIN để xác nhận:", status);
        if (pin == null) {
            return;
        }
        String cardId = status.cardId;
        PublicKey pub = cardPub;
        runCard("Đang nạp tiền…", () -> {
            TxReceipt r = card.topUp(pin, amt, pub);
            return new PayOutcome(r, false, r.newBalance, card.getStatus());
        }, o -> {
            status = o.status();
            updateChips();
            TxReceipt r = o.receipt();
            saveTx(cardId, "TOPUP", amt, r, "SUCCESS", "Nạp tiền tại quầy", null);
            tfTopUp.setText("");
            if (loggedIn) {
                acBalance.setText(Ui.money(r.newBalance));
            }
            notifyUser("Nạp tiền thành công", "Đã nạp " + Ui.money(amt) + ". Số dư mới: "
                    + Ui.money(r.newBalance) + "\nChữ ký RSA: " + sigTextPlain(r.signatureValid),
                    TrayIcon.MessageType.INFO, true);
            loadHistory();
        }, e -> {
            if (e instanceof CardOpException ce && (ce.isWrongPin() || ce.isLocked())) {
                saveTx(cardId, "TOPUP", amt, null, "FAILED_PIN", ce.getMessage(), null);
            }
            handleError(e);
        });
    }

    // =====================================================================
    //  TAB 5: LICH SU & THONG BAO
    // =====================================================================
    private JComponent buildHistoryTab() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);

        JPanel top = Ui.card("Lịch sử giao dịch của thẻ");
        top.setLayout(new BorderLayout(0, 8));
        JTable txTable = new JTable(txModel);
        Ui.styleTable(txTable);
        txTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        txTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        txTable.getColumnModel().getColumn(3).setCellRenderer(Ui.rightRenderer());
        txTable.getColumnModel().getColumn(4).setCellRenderer(Ui.rightRenderer());
        txTable.getColumnModel().getColumn(7).setPreferredWidth(220);
        top.add(new JScrollPane(txTable), BorderLayout.CENTER);
        JButton btnReload = Ui.button("Làm mới", Ui.INFO);
        btnReload.addActionListener(e -> loadHistory());
        top.add(Ui.row(btnReload), BorderLayout.SOUTH);

        JPanel bottom = Ui.card("Thông báo gửi tới chủ thẻ");
        bottom.setLayout(new BorderLayout());
        JTable notiTable = new JTable(notiModel);
        Ui.styleTable(notiTable);
        notiTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        notiTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        notiTable.getColumnModel().getColumn(2).setPreferredWidth(600);
        bottom.add(new JScrollPane(notiTable), BorderLayout.CENTER);

        JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        sp.setResizeWeight(0.6);
        sp.setBorder(null);
        sp.setOpaque(false);
        root.add(sp, BorderLayout.CENTER);
        return root;
    }

    private void loadHistory() {
        txModel.setRowCount(0);
        notiModel.setRowCount(0);
        if (status == null || !status.issued) {
            return;
        }
        try {
            for (Database.TxRow r : db.listTransactions(status.cardId)) {
                txModel.addRow(new Object[]{r.id, r.createdAt, "PAY".equals(r.type) ? "Thanh toán" : "Nạp tiền",
                    Ui.money(r.amount), r.balanceAfter == null ? "" : Ui.money(r.balanceAfter),
                    statusText(r.status), r.status.equals("SUCCESS") ? sigTextPlain(r.signatureValid) : "",
                    r.note == null ? "" : r.note});
            }
            for (Database.NotificationRow n : db.listNotifications(status.cardId)) {
                notiModel.addRow(new Object[]{n.createdAt, n.title, n.message.replace("\n", "  ")});
            }
        } catch (Exception e) {
            log("Lỗi đọc lịch sử: " + e.getMessage());
        }
    }

    private static String statusText(String s) {
        switch (s) {
            case "SUCCESS": return "Thành công";
            case "DECLINED_FUNDS": return "Từ chối – không đủ tiền";
            case "FAILED_PIN": return "Thất bại – PIN";
            default: return s;
        }
    }

    // =====================================================================
    //  NHAT KY APDU
    // =====================================================================
    private JComponent buildLogPanel() {
        JPanel p = Ui.card("Nhật ký APDU");
        p.setLayout(new BorderLayout());
        logArea.setEditable(false);
        logArea.setFont(Ui.FONT_MONO);
        logArea.setBackground(new Color(0x0F172A));
        logArea.setForeground(new Color(0xA7F3D0));
        logArea.setCaretColor(Color.WHITE);
        logArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane sp = new JScrollPane(logArea);
        sp.setPreferredSize(new Dimension(100, 150));
        p.add(sp, BorderLayout.CENTER);
        JButton clear = Ui.button("Xóa", Ui.GRAY);
        clear.addActionListener(e -> logArea.setText(""));
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        east.setOpaque(false);
        east.add(clear);
        p.add(east, BorderLayout.EAST);
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(Ui.BG);
        outer.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 16));
        outer.add(p);
        return outer;
    }

    private void log(String s) {
        String line = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + s + "\n";
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            logArea.append(line);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        } else {
            javax.swing.SwingUtilities.invokeLater(() -> log(s));
        }
    }

    // =====================================================================
    //  KET NOI THE
    // =====================================================================
    private void refreshReaders() {
        cbReader.removeAllItems();
        for (String r : PcscTransport.listReaders()) {
            cbReader.addItem(r);
        }
        if (cbReader.getItemCount() == 0) {
            log("Không tìm thấy đầu đọc PC/SC. Hãy mở JCIDE và chạy máy ảo (Run/Debug) rồi bấm \"Làm mới\".");
        }
    }

    private void toggleConnect() {
        if (card.isConnected()) {
            card.disconnect();
            status = null;
            cardPub = null;
            authentic = false;
            clearAccount();
            updateChips();
            loadHistory();
            return;
        }
        String reader = (String) cbReader.getSelectedItem();
        runCard("Đang kết nối thẻ…", () -> {
            card.connectCard(PcscTransport.open(reader));
            CardStatus st = card.getStatus();
            PublicKey pub = null;
            boolean ok = false;
            if (st.issued) {
                pub = db.findPublicKey(st.cardId);
                if (pub != null) {
                    ok = card.authenticate(st.cardId, pub);
                }
            }
            return new ConnectResult(st, pub, ok);
        }, r -> {
            status = r.status();
            cardPub = r.pub();
            authentic = r.authentic();
            loggedIn = false;
            updateChips();
            loadHistory();
            if (!status.issued) {
                log("Thẻ trắng – vào tab \"Phát hành thẻ\" để khởi tạo.");
            } else if (cardPub == null) {
                error("Thẻ " + status.cardId + " chưa được đăng ký trong CSDL của siêu thị.\n"
                        + "Không thể xác thực RSA nên không cho phép giao dịch.");
            } else if (!authentic) {
                error("CẢNH BÁO: Thẻ KHÔNG vượt qua xác thực RSA (có thể là thẻ giả).");
            } else {
                log("Xác thực RSA thành công – thẻ hợp lệ.");
            }
        });
    }

    // =====================================================================
    //  TIEN ICH GIAO DIEN
    // =====================================================================
    private <T> void runCard(String busy, Callable<T> job, Consumer<T> ok) {
        runCard(busy, job, ok, this::handleError);
    }

    private <T> void runCard(String busy, Callable<T> job, Consumer<T> ok, Consumer<Exception> err) {
        lblBusy.setText(busy);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return job.call();
            }

            @Override
            protected void done() {
                lblBusy.setText(" ");
                setCursor(Cursor.getDefaultCursor());
                try {
                    T v = get();
                    if (ok != null) {
                        ok.accept(v);
                    }
                } catch (ExecutionException ee) {
                    Throwable c = ee.getCause();
                    err.accept(c instanceof Exception ? (Exception) c : new Exception(c));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void handleError(Exception e) {
        if (e instanceof CardOpException ce) {
            log("Lỗi: " + ce.getMessage());
            if (ce.isWrongPin()) {
                notifyUser("Cảnh báo: nhập sai mã PIN", ce.getMessage()
                        + "\nNếu nhập sai " + (status == null ? 3 : status.maxTries)
                        + " lần liên tiếp, thẻ sẽ bị khóa tạm thời.", TrayIcon.MessageType.WARNING, true);
            } else if (ce.isLocked()) {
                int mins = status == null ? 5 : Math.max(1, status.lockSeconds / 60);
                notifyUser("Thẻ bị KHÓA tạm thời", "Nhập sai mã PIN quá số lần cho phép.\n"
                        + "Thẻ tự động mở khóa sau khoảng " + mins + " phút.", TrayIcon.MessageType.ERROR, true);
            } else {
                error(ce.getMessage());
            }
            if (ce.isWrongPin() || ce.isLocked()) {
                loggedIn = false;
                clearAccount();
            }
            refreshStatus();
        } else {
            log("Lỗi: " + e);
            error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private void refreshStatus() {
        if (!card.isConnected()) {
            return;
        }
        runCard("Đang đọc trạng thái thẻ…", card::getStatus, s -> {
            status = s;
            updateChips();
        }, e -> log("Không đọc được trạng thái: " + e.getMessage()));
    }

    private void updateChips() {
        boolean conn = card.isConnected();
        Ui.setChip(chipConn, conn ? "● Đã kết nối" : "○ Chưa kết nối", conn ? Ui.OK : Ui.GRAY);
        btnConnect.setText(conn ? "Ngắt kết nối" : "Kết nối");
        if (!conn || status == null) {
            Ui.setChip(chipCard, "Thẻ: —", Ui.GRAY);
            Ui.setChip(chipRsa, "RSA: —", Ui.GRAY);
            Ui.setChip(chipPin, "PIN: —", Ui.GRAY);
        } else if (!status.issued) {
            Ui.setChip(chipCard, "Thẻ trắng (chưa phát hành)", Ui.ACCENT);
            Ui.setChip(chipRsa, "RSA: —", Ui.GRAY);
            Ui.setChip(chipPin, "PIN: chưa đặt", Ui.GRAY);
        } else {
            Ui.setChip(chipCard, "Thẻ: " + status.cardId, Ui.INFO);
            if (authentic) {
                Ui.setChip(chipRsa, "RSA: Thẻ hợp lệ", Ui.OK);
            } else if (cardPub == null) {
                Ui.setChip(chipRsa, "RSA: chưa đăng ký CSDL", Ui.ACCENT);
            } else {
                Ui.setChip(chipRsa, "RSA: Thẻ KHÔNG hợp lệ", Ui.DANGER);
            }
            if (loggedIn) {
                Ui.setChip(chipPin, "Đã xác thực PIN", Ui.OK);
            } else {
                Ui.setChip(chipPin, "PIN: còn " + status.triesLeft + "/" + status.maxTries + " lần",
                        status.triesLeft <= 1 ? Ui.DANGER : Ui.INFO);
            }
        }
        updateLockChip();
    }

    private void updateLockChip() {
        if (status == null || !status.locked || !card.isConnected()) {
            chipLock.setVisible(false);
            return;
        }
        long rem = status.remainingLockSeconds();
        if (rem > 0) {
            Ui.setChip(chipLock, String.format("THẺ BỊ KHÓA – còn %02d:%02d", rem / 60, rem % 60), Ui.DANGER);
        } else {
            Ui.setChip(chipLock, "Hết thời gian khóa – có thể nhập PIN lại", Ui.ACCENT);
        }
        chipLock.setVisible(true);
    }

    private boolean requireConnected() {
        if (!card.isConnected()) {
            error("Chưa kết nối thẻ. Hãy chọn đầu đọc và bấm \"Kết nối\".");
            return false;
        }
        return true;
    }

    private boolean requireIssued() {
        if (!requireConnected()) {
            return false;
        }
        if (status == null || !status.issued) {
            error("Thẻ chưa được phát hành. Hãy vào tab \"Phát hành thẻ\".");
            return false;
        }
        return true;
    }

    private boolean requireAuthentic() {
        if (!requireIssued()) {
            return false;
        }
        if (!authentic) {
            error("Thẻ chưa vượt qua xác thực RSA với CSDL – không cho phép giao dịch.");
            return false;
        }
        return true;
    }

    private boolean requireLogin() {
        if (!requireIssued()) {
            return false;
        }
        if (!loggedIn) {
            error("Hãy đăng nhập bằng mã PIN trước.");
            return false;
        }
        return true;
    }

    /** Kiem tra nhanh phia may tram (the van la noi quyet dinh cuoi cung). */
    private boolean checkNotLocked() {
        if (status != null && status.locked) {
            long rem = status.remainingLockSeconds();
            if (rem > 0) {
                error(String.format("Thẻ đang bị khóa tạm thời do nhập sai PIN 3 lần.%nVui lòng thử lại sau %02d:%02d.",
                        rem / 60, rem % 60));
                return false;
            }
        }
        return true;
    }

    private void saveTx(String cardId, String type, long amount, TxReceipt r, String st, String note,
                        List<Database.CartItem> items) {
        try {
            db.saveTransaction(cardId, type, amount, r, st, note, items);
        } catch (Exception e) {
            log("Lỗi ghi CSDL: " + e.getMessage());
        }
    }

    /** Gui thong bao toi chu the: luu CSDL + thong bao he thong (khay) + hop thoai. */
    private void notifyUser(String title, String message, TrayIcon.MessageType type, boolean dialog) {
        String cardId = status == null ? null : status.cardId;
        try {
            db.addNotification(cardId, title, message);
        } catch (Exception e) {
            log("Lỗi ghi thông báo: " + e.getMessage());
        }
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, type);
        }
        log("[THÔNG BÁO] " + title + " – " + message.replace("\n", " "));
        loadHistory();
        if (dialog) {
            int jt = type == TrayIcon.MessageType.ERROR ? JOptionPane.ERROR_MESSAGE
                    : type == TrayIcon.MessageType.WARNING ? JOptionPane.WARNING_MESSAGE
                    : JOptionPane.INFORMATION_MESSAGE;
            JOptionPane.showMessageDialog(this, message, title, jt);
        }
    }

    private void setupTray() {
        if (!SystemTray.isSupported()) {
            return;
        }
        try {
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Ui.PRIMARY);
            g.fillRoundRect(0, 2, 16, 12, 4, 4);
            g.setColor(Ui.ACCENT);
            g.fillRect(2, 6, 5, 4);
            g.dispose();
            trayIcon = new TrayIcon(img, "MarketCard");
            trayIcon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(trayIcon);
        } catch (Exception e) {
            trayIcon = null;
        }
    }

    private byte[] choosePhoto() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Ảnh (jpg, png, bmp, gif)", "jpg", "jpeg", "png", "bmp", "gif"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File f = fc.getSelectedFile();
        try {
            byte[] data = Ui.compressPhoto(f, SmartCardWork.PHOTO_MAX);
            log("Ảnh đã nén: " + data.length + " byte");
            return data;
        } catch (Exception e) {
            error(e.getMessage());
            return null;
        }
    }

    private static void showPhoto(JLabel box, byte[] data, int w, int h) {
        ImageIcon icon = Ui.photoIcon(data, w, h);
        box.setIcon(icon);
        box.setText(icon == null ? "Ảnh lỗi" : "");
    }

    private static String sigText(Boolean v) {
        if (v == null) {
            return "<span style='color:#64748B'>không kiểm tra</span>";
        }
        return v ? "<b style='color:#16A34A'>hợp lệ</b>" : "<b style='color:#DC2626'>KHÔNG hợp lệ</b>";
    }

    private static String sigTextPlain(Boolean v) {
        return v == null ? "—" : v ? "hợp lệ" : "không hợp lệ";
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "MarketCard", JOptionPane.ERROR_MESSAGE);
    }

    private void info(String msg) {
        JOptionPane.showMessageDialog(this, msg, "MarketCard", JOptionPane.INFORMATION_MESSAGE);
    }

    private static JComponent wrap(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Ui.BG);
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        p.add(c);
        return p;
    }

    private static DefaultTableModel readOnlyModel(String... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
    }
}
