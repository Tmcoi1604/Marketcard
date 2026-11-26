package marketcard.app;

/**
 * Kenh truyen APDU toi the. Ban that dung PC/SC (javax.smartcardio) - xem PcscTransport.
 * Tach ra interface de co the kiem thu voi the gia lap.
 */
public interface CardTransport {

    /** Gui 1 APDU lenh (dang byte tho) va nhan APDU phan hoi (data + SW1 SW2). */
    byte[] transmit(byte[] command) throws Exception;

    /** Ten dau doc / mo ta ket noi. */
    String name();

    void close();
}
