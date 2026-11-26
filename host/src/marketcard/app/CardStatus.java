package marketcard.app;

/** Trang thai the doc bang lenh GET_STATUS (khong can PIN). */
public class CardStatus {

    public boolean issued;
    public String cardId;
    public int triesLeft;
    public int maxTries;
    public boolean locked;
    public long lockUntil;     // giay (Unix epoch)
    public long lastSeen;      // giay
    public int lockSeconds;

    public static CardStatus parse(byte[] d) throws CardOpException {
        if (d.length < 22) {
            throw new CardOpException("Phản hồi trạng thái thẻ không hợp lệ");
        }
        CardStatus s = new CardStatus();
        s.issued = d[0] == 1;
        s.cardId = Hex.of(d, 1, 8);
        s.triesLeft = d[9];
        s.locked = d[10] != 0;
        s.lockUntil = Hex.u32(d, 11);
        s.lastSeen = Hex.u32(d, 15);
        s.maxTries = d[19];
        s.lockSeconds = ((d[20] & 0xFF) << 8) | (d[21] & 0xFF);
        return s;
    }

    /** So giay con lai cua thoi gian khoa (0 neu da het han / khong khoa). */
    public long remainingLockSeconds() {
        if (!locked) {
            return 0;
        }
        long now = System.currentTimeMillis() / 1000;
        return Math.max(0, lockUntil - now);
    }
}
