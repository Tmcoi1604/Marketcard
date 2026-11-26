package marketcard.app;

/** Bien lai giao dich do the tra ve (da ky RSA). */
public class TxReceipt {

    public boolean pay;          // true = thanh toan, false = nap tien
    public long amount;
    public long newBalance;
    public int txCounter;
    public byte[] nonce;
    public long time;
    public byte[] signedMessage;
    public byte[] signature;
    /** Chu ky hop le theo khoa cong khai luu trong CSDL (null = chua kiem tra duoc). */
    public Boolean signatureValid;
}
