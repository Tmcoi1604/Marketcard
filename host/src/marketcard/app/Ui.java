package marketcard.app;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.NumberFormat;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/** Bang mau, font va cac thanh phan giao dien dung chung. */
public final class Ui {

    private Ui() {
    }

    public static final Color PRIMARY = new Color(0x0E7C66);
    public static final Color PRIMARY_DARK = new Color(0x095C4C);
    public static final Color ACCENT = new Color(0xF59E0B);
    public static final Color DANGER = new Color(0xDC2626);
    public static final Color INFO = new Color(0x2563EB);
    public static final Color GRAY = new Color(0x64748B);
    public static final Color BG = new Color(0xF1F5F4);
    public static final Color CARD_BG = Color.WHITE;
    public static final Color BORDER = new Color(0xDCE3E1);
    public static final Color TEXT = new Color(0x1E293B);
    public static final Color OK = new Color(0x16A34A);

    public static final Font FONT = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_BIG = new Font("Segoe UI", Font.BOLD, 28);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 12);

    private static final NumberFormat MONEY = NumberFormat.getIntegerInstance(new Locale("vi", "VN"));

    public static String money(long v) {
        return MONEY.format(v) + " đ";
    }

    /** Nut bam bo tron, to mau. */
    public static JButton button(String text, Color color) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = isEnabled() ? color : new Color(0xB6C2BF);
                if (getModel().isPressed()) {
                    c = c.darker();
                } else if (getModel().isRollover() && isEnabled()) {
                    c = c.brighter();
                }
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(FONT_BOLD);
        b.setForeground(Color.WHITE);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
        return b;
    }

    /** Khung trang bo tron chua noi dung, co tieu de. */
    public static JPanel card(String title) {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.setColor(BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        Border pad = BorderFactory.createEmptyBorder(14, 16, 14, 16);
        if (title != null) {
            p.setBorder(BorderFactory.createCompoundBorder(pad,
                    BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), title,
                            0, 0, FONT_TITLE, PRIMARY_DARK)));
        } else {
            p.setBorder(pad);
        }
        return p;
    }

    /** Panel nen co mau gradient (header). */
    public static JPanel gradient(Color a, Color b) {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, a, getWidth(), getHeight(), b));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
    }

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT);
        l.setForeground(TEXT);
        return l;
    }

    public static JLabel chip(String text, Color color) {
        JLabel l = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setOpaque(false);
        l.setBackground(color);
        l.setForeground(Color.WHITE);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        return l;
    }

    public static void setChip(JLabel chip, String text, Color color) {
        chip.setText(text);
        chip.setBackground(color);
        chip.repaint();
    }

    public static JTextField field(int cols) {
        JTextField f = new JTextField(cols);
        styleField(f);
        return f;
    }

    public static JPasswordField pinField() {
        JPasswordField f = new JPasswordField(10);
        styleField(f);
        ((AbstractDocument) f.getDocument()).setDocumentFilter(new DigitsFilter(8));
        return f;
    }

    public static void styleField(JComponent f) {
        f.setFont(FONT);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    }

    public static void styleTable(JTable t) {
        t.setFont(FONT);
        t.setRowHeight(28);
        t.setGridColor(new Color(0xEEF2F1));
        t.setSelectionBackground(new Color(0xCDEFE6));
        t.setSelectionForeground(TEXT);
        t.setShowVerticalLines(false);
        t.setFillsViewportHeight(true);
        JTableHeader h = t.getTableHeader();
        h.setFont(FONT_BOLD);
        h.setBackground(new Color(0xE3F1EE));
        h.setForeground(PRIMARY_DARK);
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 32));
    }

    public static DefaultTableCellRenderer rightRenderer() {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer();
        r.setHorizontalAlignment(SwingConstants.RIGHT);
        return r;
    }

    /** Them 1 dong "nhan: o nhap" vao form GridBag. */
    public static void addRow(JPanel form, int row, String label, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(6, 0, 6, 12);
        form.add(label(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(6, 0, 6, 0);
        form.add(field, c);
    }

    public static JPanel form() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        return p;
    }

    public static JPanel row(Component... comps) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setOpaque(false);
        for (Component c : comps) {
            p.add(c);
        }
        return p;
    }

    // ===================== Anh chu the =====================

    /** Doc anh, thu nho ve 120x150 va nen JPEG cho vua bo nho the (<= maxBytes). */
    public static byte[] compressPhoto(File file, int maxBytes) throws Exception {
        BufferedImage src = ImageIO.read(file);
        if (src == null) {
            throw new Exception("Không đọc được file ảnh");
        }
        int maxW = 120;
        int maxH = 150;
        double scale = Math.min(1.0, Math.min((double) maxW / src.getWidth(), (double) maxH / src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        for (float q = 0.85f; q >= 0.2f; q -= 0.1f) {
            byte[] data = jpeg(img, q);
            if (data.length <= maxBytes) {
                return data;
            }
        }
        throw new Exception("Ảnh quá phức tạp, không nén được xuống " + maxBytes + " byte");
    }

    private static byte[] jpeg(BufferedImage img, float quality) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(quality);
            writer.write(null, new IIOImage(img, null, null), p);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    public static ImageIcon photoIcon(byte[] data, int w, int h) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img == null) {
                return null;
            }
            double s = Math.min((double) w / img.getWidth(), (double) h / img.getHeight());
            return new ImageIcon(img.getScaledInstance((int) (img.getWidth() * s), (int) (img.getHeight() * s),
                    Image.SCALE_SMOOTH));
        } catch (Exception e) {
            return null;
        }
    }

    /** Khung hien thi anh chu the. */
    public static JLabel photoBox(int w, int h) {
        JLabel l = new JLabel("Chưa có ảnh", SwingConstants.CENTER);
        l.setPreferredSize(new Dimension(w, h));
        l.setMinimumSize(new Dimension(w, h));
        l.setMaximumSize(new Dimension(w, h));
        l.setFont(FONT);
        l.setForeground(GRAY);
        l.setOpaque(true);
        l.setBackground(new Color(0xF8FAFA));
        l.setBorder(BorderFactory.createDashedBorder(BORDER, 4, 4));
        return l;
    }

    /** Chi cho nhap chu so, toi da n ky tu. */
    public static class DigitsFilter extends DocumentFilter {
        private final int max;

        public DigitsFilter(int max) {
            this.max = max;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String s, AttributeSet a) throws BadLocationException {
            replace(fb, offset, 0, s, a);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String s, AttributeSet a)
                throws BadLocationException {
            if (s == null) {
                return;
            }
            String digits = s.replaceAll("\\D", "");
            int room = max - (fb.getDocument().getLength() - length);
            if (room <= 0) {
                return;
            }
            if (digits.length() > room) {
                digits = digits.substring(0, room);
            }
            super.replace(fb, offset, length, digits, a);
        }
    }
}
