package marketcard.app;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

/**
 * Cac ham giao tiep voi applet MarketCard (theo cau truc SmartCardWork.java trong Connect.pdf).
 * Moi ham tuong ung 1 lenh APDU (CLA = B0).
 */
public class SmartCardWork {

    /** AID cua applet da dang ky voi JCRE (phai trung voi AID khai bao trong JCIDE). */
    public static final byte[] APPLET_AID = {0x11, 0x22, 0x33, 0x44, 0x55, 0x00, 0x00};

    private static final int CLA = 0xB0;
    private static final int INS_INIT = 0x10;
    private static final int INS_GET_PUBKEY = 0x12;
    private static final int INS_GET_STATUS = 0x13;
    private static final int INS_VERIFY_PIN = 0x20;
    private static final int INS_CHANGE_PIN = 0x21;
    private static final int INS_LOGOUT = 0x22;
    private static final int INS_GET_INFO = 0x30;
    private static final int INS_UPDATE_INFO = 0x31;
    private static final int INS_SET_PHOTO = 0x32;
    private static final int INS_GET_PHOTO = 0x33;
    private static final int INS_SET_PHOTO_LEN = 0x34;
    private static final int INS_GET_PHOTO_LEN = 0x35;
    private static final int INS_GET_BALANCE = 0x40;
    private static final int INS_PAY = 0x41;
    private static final int INS_TOPUP = 0x42;
    private static final int INS_AUTH = 0x50;

    public static final int PHOTO_MAX = 8192;
    private static final int CHUNK = 128;
    public static final long MAX_AMOUNT = 0xFFFFFFFFL;

    private final SecureRandom random = new SecureRandom();
    private CardTransport transport;
    private Consumer<String> logger = s -> { };

    public void setLogger(Consumer<String> logger) {
        this.logger = logger == null ? s -> { } : logger;
    }

    // ===================== Ket noi =====================

    /** Ket noi va chon applet: 00 A4 04 00 Lc AID. Tra ve true neu thanh cong. */
    public synchronized boolean connectCard(CardTransport t) throws Exception {
        disconnect();
        this.transport = t;
        logger.accept("Đã kết nối: " + t.name());
        ResponseAPDU r = transmit(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, APPLET_AID));
        if (r.getSW() != CardOpException.SW_OK) {
            disconnect();
            throw new CardOpException(r.getSW());
        }
        return true;
    }

    public synchronized boolean isConnected() {
        return transport != null;
    }

    public synchronized void disconnect() {
        if (transport != null) {
            transport.close();
            logger.accept("Đã ngắt kết nối thẻ.");
        }
        transport = null;
    }

    // ===================== Cac chuc nang =====================

    public synchronized CardStatus getStatus() throws Exception {
        return CardStatus.parse(call(INS_GET_STATUS, 0, 0, null, true));
    }

    /** Phat hanh the: dat PIN + thong tin. Tra ve ma the (hex). */
    public synchronized String initCard(String pin, CardInfo info) throws Exception {
        byte[] p = pinBytes(pin);
        byte[] i = info.toBytes();
        ByteArrayOutputStream d = new ByteArrayOutputStream();
        d.write(p.length);
        d.write(p, 0, p.length);
        d.write(i.length);
        d.write(i, 0, i.length);
        byte[] id = call(INS_INIT, 0, 0, d.toByteArray(), true);
        return Hex.of(id);
    }

    /** Doc khoa cong khai RSA cua the. */
    public synchronized PublicKey getPublicKey() throws Exception {
        byte[] d = call(INS_GET_PUBKEY, 0, 0, null, true);
        int modLen = Hex.u16(d, 0);
        byte[] mod = Arrays.copyOfRange(d, 2, 2 + modLen);
        int expLen = Hex.u16(d, 2 + modLen);
        byte[] exp = Arrays.copyOfRange(d, 4 + modLen, 4 + modLen + expLen);
        RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, mod), new BigInteger(1, exp));
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    /** Xac thuc the bang RSA challenge-response voi khoa cong khai da luu trong CSDL. */
    public synchronized boolean authenticate(String cardIdHex, PublicKey pub) throws Exception {
        byte[] challenge = new byte[16];
        random.nextBytes(challenge);
        byte[] sig = call(INS_AUTH, 0, 0, challenge, true);
        ByteArrayOutputStream m = new ByteArrayOutputStream();
        m.write('A');
        m.write(hexToBytes(cardIdHex));
        m.write(challenge);
        return verify(pub, m.toByteArray(), sig);
    }

    public synchronized void verifyPin(String pin) throws Exception {
        byte[] p = pinBytes(pin);
        ByteArrayOutputStream d = new ByteArrayOutputStream();
        d.write(timeBytes());
        d.write(p.length);
        d.write(p);
        call(INS_VERIFY_PIN, 0, 0, d.toByteArray(), false);
    }

    public synchronized void changePin(String oldPin, String newPin) throws Exception {
        byte[] o = pinBytes(oldPin);
        byte[] n = pinBytes(newPin);
        ByteArrayOutputStream d = new ByteArrayOutputStream();
        d.write(timeBytes());
        d.write(o.length);
        d.write(o);
        d.write(n.length);
        d.write(n);
        call(INS_CHANGE_PIN, 0, 0, d.toByteArray(), false);
    }

    public synchronized void logout() throws Exception {
        call(INS_LOGOUT, 0, 0, null, false);
    }

    public synchronized CardInfo getInfo() throws Exception {
        return CardInfo.fromBytes(call(INS_GET_INFO, 0, 0, null, true));
    }

    public synchronized void updateInfo(CardInfo info) throws Exception {
        call(INS_UPDATE_INFO, 0, 0, info.toBytes(), false);
    }

    /** Ghi anh (JPEG) xuong the theo tung khoi 128 byte; the ma hoa AES tung khoi. */
    public synchronized void uploadPhoto(byte[] photo) throws Exception {
        if (photo.length > PHOTO_MAX) {
            throw new CardOpException("Ảnh quá lớn (" + photo.length + " byte, tối đa " + PHOTO_MAX + ")");
        }
        int padded = (photo.length + 15) / 16 * 16;
        byte[] data = Arrays.copyOf(photo, padded);
        for (int off = 0; off < padded; off += CHUNK) {
            int len = Math.min(CHUNK, padded - off);
            call(INS_SET_PHOTO, off >> 8, off & 0xFF, Arrays.copyOfRange(data, off, off + len), false);
        }
        call(INS_SET_PHOTO_LEN, 0, 0, new byte[]{(byte) (photo.length >> 8), (byte) photo.length}, false);
    }

    /** Doc anh tu the (null neu chua co anh). */
    public synchronized byte[] downloadPhoto() throws Exception {
        byte[] l = call(INS_GET_PHOTO_LEN, 0, 0, null, true);
        int len = Hex.u16(l, 0);
        if (len == 0) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int padded = (len + 15) / 16 * 16;
        for (int off = 0; off < padded; off += CHUNK) {
            out.write(call(INS_GET_PHOTO, off >> 8, off & 0xFF, null, true));
        }
        return Arrays.copyOf(out.toByteArray(), len);
    }

    /** [0] = so du, [1] = bo dem giao dich. */
    public synchronized long[] getBalance() throws Exception {
        byte[] d = call(INS_GET_BALANCE, 0, 0, null, true);
        return new long[]{Hex.u32(d, 0), Hex.u16(d, 4)};
    }

    /** Thanh toan: the tu xac thuc PIN, tu tru tien, ky bien lai. */
    public synchronized TxReceipt pay(String pin, long amount, PublicKey pub) throws Exception {
        return money(INS_PAY, pin, amount, pub);
    }

    /** Nap tien (cung yeu cau PIN). */
    public synchronized TxReceipt topUp(String pin, long amount, PublicKey pub) throws Exception {
        return money(INS_TOPUP, pin, amount, pub);
    }

    private TxReceipt money(int ins, String pin, long amount, PublicKey pub) throws Exception {
        if (amount <= 0 || amount > MAX_AMOUNT) {
            throw new CardOpException("Số tiền không hợp lệ");
        }
        byte[] p = pinBytes(pin);
        byte[] time = timeBytes();
        byte[] amt = new byte[4];
        Hex.putU32(amt, 0, amount);
        byte[] nonce = new byte[8];
        random.nextBytes(nonce);

        ByteArrayOutputStream d = new ByteArrayOutputStream();
        d.write(time);
        d.write(p.length);
        d.write(p);
        d.write(amt);
        d.write(nonce);
        byte[] resp = call(ins, 0, 0, d.toByteArray(), true);

        TxReceipt r = new TxReceipt();
        r.pay = ins == INS_PAY;
        r.amount = amount;
        r.newBalance = Hex.u32(resp, 0);
        r.txCounter = Hex.u16(resp, 4);
        r.nonce = nonce;
        r.time = Hex.u32(time, 0);
        r.signature = Arrays.copyOfRange(resp, 6, resp.length);

        // Dung lai thong diep the da ky de kiem tra
        CardStatus st = CardStatus.parse(call(INS_GET_STATUS, 0, 0, null, true));
        ByteArrayOutputStream m = new ByteArrayOutputStream();
        m.write(r.pay ? 'P' : 'T');
        m.write(hexToBytes(st.cardId));
        m.write(amt);
        m.write(Arrays.copyOfRange(resp, 0, 6));
        m.write(nonce);
        m.write(time);
        r.signedMessage = m.toByteArray();
        r.signatureValid = pub == null ? null : verify(pub, r.signedMessage, r.signature);
        return r;
    }

    // ===================== Ham noi bo =====================

    private byte[] call(int ins, int p1, int p2, byte[] data, boolean expectData) throws Exception {
        CommandAPDU cmd;
        boolean hasData = data != null && data.length > 0;
        if (hasData && expectData) {
            cmd = new CommandAPDU(CLA, ins, p1, p2, data, 256);
        } else if (hasData) {
            cmd = new CommandAPDU(CLA, ins, p1, p2, data);
        } else if (expectData) {
            cmd = new CommandAPDU(CLA, ins, p1, p2, 256);
        } else {
            cmd = new CommandAPDU(CLA, ins, p1, p2);
        }
        ResponseAPDU r = transmit(cmd);
        if (r.getSW() != CardOpException.SW_OK) {
            throw new CardOpException(r.getSW());
        }
        return r.getData();
    }

    private ResponseAPDU transmit(CommandAPDU cmd) throws Exception {
        if (transport == null) {
            throw new CardOpException("Chưa kết nối thẻ. Hãy bấm \"Kết nối\".");
        }
        byte[] raw = cmd.getBytes();
        logger.accept(">> " + maskPin(cmd));
        byte[] resp = transport.transmit(raw);
        ResponseAPDU r = new ResponseAPDU(resp);
        logger.accept("<< " + Hex.spaced(resp));
        return r;
    }

    /** Khong ghi PIN ra nhat ky APDU. */
    private static String maskPin(CommandAPDU cmd) {
        int ins = cmd.getINS();
        if (ins == INS_VERIFY_PIN || ins == INS_CHANGE_PIN || ins == INS_PAY
                || ins == INS_TOPUP || ins == INS_INIT) {
            return String.format("%02X %02X %02X %02X %02X [dữ liệu chứa PIN - đã ẩn, %d byte]",
                    cmd.getCLA(), ins, cmd.getP1(), cmd.getP2(), cmd.getNc(), cmd.getNc());
        }
        return Hex.spaced(cmd.getBytes());
    }

    private static boolean verify(PublicKey pub, byte[] msg, byte[] sig) throws Exception {
        Signature s = Signature.getInstance("SHA1withRSA");
        s.initVerify(pub);
        s.update(msg);
        return s.verify(sig);
    }

    private static byte[] pinBytes(String pin) throws CardOpException {
        if (pin == null || !pin.matches("\\d{4,8}")) {
            throw new CardOpException("Mã PIN phải gồm 4-8 chữ số.");
        }
        return pin.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] timeBytes() {
        byte[] t = new byte[4];
        Hex.putU32(t, 0, System.currentTimeMillis() / 1000);
        return t;
    }

    public static byte[] hexToBytes(String hex) {
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return b;
    }
}
