package marketcard.app;

import java.util.ArrayList;
import java.util.List;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;

/**
 * Ket noi the qua PC/SC (dau doc that hoac dau doc ao cua JCIDE),
 * lam theo tai lieu Connect.pdf: terminal.connect("T=0") -> getBasicChannel().
 */
public class PcscTransport implements CardTransport {

    private final CardTerminal terminal;
    private final Card card;
    private final CardChannel channel;

    public PcscTransport(CardTerminal terminal) throws Exception {
        this.terminal = terminal;
        Card c;
        try {
            c = terminal.connect("T=0");
        } catch (Exception e) {
            // Mot so dau doc chi ho tro T=1
            c = terminal.connect("*");
        }
        this.card = c;
        this.channel = card.getBasicChannel();
    }

    /** Liet ke ten cac dau doc dang co tren may. */
    public static List<String> listReaders() {
        List<String> names = new ArrayList<>();
        try {
            TerminalFactory factory = TerminalFactory.getDefault();
            for (CardTerminal t : factory.terminals().list()) {
                names.add(t.getName());
            }
        } catch (Exception ignored) {
            // khong co dich vu PC/SC hoac khong co dau doc
        }
        return names;
    }

    public static PcscTransport open(String readerName) throws Exception {
        TerminalFactory factory = TerminalFactory.getDefault();
        List<CardTerminal> terminals = factory.terminals().list();
        if (terminals.isEmpty()) {
            throw new Exception("Không tìm thấy đầu đọc thẻ nào. Hãy chạy máy ảo JCIDE / cắm đầu đọc.");
        }
        CardTerminal chosen = terminals.get(0);
        if (readerName != null) {
            for (CardTerminal t : terminals) {
                if (t.getName().equals(readerName)) {
                    chosen = t;
                    break;
                }
            }
        }
        if (!chosen.isCardPresent()) {
            throw new Exception("Không có thẻ trong đầu đọc: " + chosen.getName());
        }
        return new PcscTransport(chosen);
    }

    @Override
    public byte[] transmit(byte[] command) throws Exception {
        ResponseAPDU r = channel.transmit(new CommandAPDU(command));
        return r.getBytes();
    }

    @Override
    public String name() {
        return terminal.getName() + " (" + card.getProtocol() + ")";
    }

    @Override
    public void close() {
        try {
            card.disconnect(false);
        } catch (Exception ignored) {
        }
    }
}
