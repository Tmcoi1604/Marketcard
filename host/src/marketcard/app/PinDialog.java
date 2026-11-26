package marketcard.app;

import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

/** Hop thoai nhap ma PIN (chi nhan chu so, an ky tu). */
public final class PinDialog {

    private PinDialog() {
    }

    /** Tra ve PIN hoac null neu nguoi dung huy. */
    public static String ask(Component parent, String title, String message, CardStatus st) {
        while (true) {
            JPasswordField pf = Ui.pinField();
            focusOnShow(pf);
            JPanel p = new JPanel(new BorderLayout(0, 8));
            p.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            String tries = "";
            if (st != null && st.issued) {
                tries = "<br><span style='color:#64748B'>Còn " + st.triesLeft + "/" + st.maxTries
                        + " lần thử trước khi thẻ bị khóa tạm thời.</span>";
            }
            p.add(new JLabel("<html>" + message.replace("\n", "<br>") + tries + "</html>"), BorderLayout.NORTH);
            p.add(pf, BorderLayout.CENTER);
            int r = JOptionPane.showConfirmDialog(parent, p, title, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) {
                return null;
            }
            String pin = new String(pf.getPassword());
            if (pin.matches("\\d{4,8}")) {
                return pin;
            }
            JOptionPane.showMessageDialog(parent, "Mã PIN phải gồm 4-8 chữ số.", title, JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Hop thoai doi PIN. Tra ve {pinCu, pinMoi} hoac null. */
    public static String[] askChange(Component parent) {
        while (true) {
            JPasswordField oldPf = Ui.pinField();
            JPasswordField newPf = Ui.pinField();
            JPasswordField cfPf = Ui.pinField();
            focusOnShow(oldPf);
            JPanel form = Ui.form();
            Ui.addRow(form, 0, "Mã PIN hiện tại", oldPf);
            Ui.addRow(form, 1, "Mã PIN mới (4-8 số)", newPf);
            Ui.addRow(form, 2, "Nhập lại PIN mới", cfPf);
            int r = JOptionPane.showConfirmDialog(parent, form, "Đổi mã PIN", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) {
                return null;
            }
            String o = new String(oldPf.getPassword());
            String n = new String(newPf.getPassword());
            String c = new String(cfPf.getPassword());
            String err = null;
            if (!o.matches("\\d{4,8}") || !n.matches("\\d{4,8}")) {
                err = "Mã PIN phải gồm 4-8 chữ số.";
            } else if (!n.equals(c)) {
                err = "PIN mới nhập lại không khớp.";
            } else if (n.equals(o)) {
                err = "PIN mới phải khác PIN hiện tại.";
            }
            if (err == null) {
                return new String[]{o, n};
            }
            JOptionPane.showMessageDialog(parent, err, "Đổi mã PIN", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void focusOnShow(JPasswordField pf) {
        pf.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent e) {
                SwingUtilities.invokeLater(pf::requestFocusInWindow);
            }

            @Override
            public void ancestorRemoved(AncestorEvent e) {
            }

            @Override
            public void ancestorMoved(AncestorEvent e) {
            }
        });
    }
}
