package marketcard.app;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Diem khoi chay phan mem may tram MarketCard. */
public class App {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            Database db;
            try {
                db = new Database("data/marketcard.db");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Không mở được cơ sở dữ liệu:\n" + e.getMessage(),
                        "MarketCard", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
                return;
            }
            new MainFrame(db).setVisible(true);
        });
    }
}
