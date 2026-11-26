package marketcard;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.AESKey;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacard.security.MessageDigest;
import javacard.security.RSAPrivateKey;
import javacard.security.RSAPublicKey;
import javacard.security.RandomData;
import javacard.security.Signature;
import javacardx.crypto.Cipher;

/**
 * THE THANH TOAN SIEU THI (MarketCard)
 *
 * Chuc nang:
 *  - Khoi tao (phat hanh) the: dat PIN ban dau, luu thong tin chu the, sinh cap khoa RSA-1024.
 *  - Xac thuc chu the bang PIN (PIN chi luu duoi dang SHA-1(salt || PIN), khong luu PIN ro).
 *  - Doi PIN (chinh chu phai nhap dung PIN cu).
 *  - Sai PIN 3 lan lien tiep -> the tu khoa trong LOCK_SECONDS giay, het han tu mo khoa.
 *  - Thong tin ca nhan + anh duoc ma hoa AES-128 (CBC) bang khoa du lieu DEK.
 *    DEK duoc "boc" (wrap) bang khoa KEK = SHA-1(kekSalt || PIN)[0..15]
 *    => khoa AES duoc tao ra tu ma PIN; doi PIN chi can boc lai DEK.
 *  - Thanh toan / nap tien: MOI LAN deu phai gui kem PIN; the tu tru tien,
 *    tu choi neu so du khong du (SW 6A90), ky bien lai bang RSA (SHA1withRSA).
 *  - Xac thuc the (chong the gia) bang RSA challenge-response.
 *
 * The khong co dong ho => thoi gian (giay, Unix epoch, 4 byte) do may tram gui kem
 * trong cac lenh co PIN. The luu "lastSeen" va chi cho thoi gian di tien (khong lui).
 *
 * CLA = 0xB0 (theo tai lieu Connect.pdf)
 * AID goi:   11 22 33 44 55 00
 * AID applet: 11 22 33 44 55 00 00
 */
public class MarketCardApplet extends Applet {

    // ======================= CLA / INS =======================
    private static final byte CLA_MARKET = (byte) 0xB0;

    private static final byte INS_INIT          = (byte) 0x10;
    private static final byte INS_GET_PUBKEY    = (byte) 0x12;
    private static final byte INS_GET_STATUS    = (byte) 0x13;
    private static final byte INS_VERIFY_PIN    = (byte) 0x20;
    private static final byte INS_CHANGE_PIN    = (byte) 0x21;
    private static final byte INS_LOGOUT        = (byte) 0x22;
    private static final byte INS_GET_INFO      = (byte) 0x30;
    private static final byte INS_UPDATE_INFO   = (byte) 0x31;
    private static final byte INS_SET_PHOTO     = (byte) 0x32;
    private static final byte INS_GET_PHOTO     = (byte) 0x33;
    private static final byte INS_SET_PHOTO_LEN = (byte) 0x34;
    private static final byte INS_GET_PHOTO_LEN = (byte) 0x35;
    private static final byte INS_GET_BALANCE   = (byte) 0x40;
    private static final byte INS_PAY           = (byte) 0x41;
    private static final byte INS_TOPUP         = (byte) 0x42;
    private static final byte INS_AUTH          = (byte) 0x50;

    // ======================= Status word rieng =======================
    /** 63Cx : sai PIN, con x lan thu */
    private static final short SW_WRONG_PIN_BASE      = (short) 0x63C0;
    /** The dang bi khoa tam thoi (sai PIN qua so lan cho phep) */
    private static final short SW_CARD_LOCKED         = (short) 0x6983;
    /** So du khong du de thanh toan */
    private static final short SW_INSUFFICIENT_FUNDS  = (short) 0x6A90;
    /** Nap tien vuot qua gioi han so du (tran 32 bit) */
    private static final short SW_BALANCE_OVERFLOW    = (short) 0x6A91;
    /** The chua duoc phat hanh */
    private static final short SW_NOT_ISSUED          = (short) 0x6A92;
    /** The da duoc phat hanh roi, khong khoi tao lai */
    private static final short SW_ALREADY_ISSUED      = (short) 0x6A93;
    /** PIN khong dung dinh dang (4-8 chu so) */
    private static final short SW_BAD_PIN_FORMAT      = (short) 0x6A94;

    // ======================= Hang so =======================
    private static final byte STATE_EMPTY  = (byte) 0;
    private static final byte STATE_ISSUED = (byte) 1;

    private static final byte MAX_PIN_TRIES = (byte) 3;
    private static final byte MIN_PIN_LEN   = (byte) 4;
    private static final byte MAX_PIN_LEN   = (byte) 8;
    /** Thoi gian khoa the (giay). Doi thanh 60 neu muon demo nhanh. */
    private static final short LOCK_SECONDS = (short) 300;

    private static final short CARD_ID_LEN = (short) 8;
    private static final short SALT_LEN    = (short) 16;
    private static final short HASH_LEN    = (short) 20;   // SHA-1
    private static final short AES_LEN     = (short) 16;   // AES-128
    private static final short INFO_MAX    = (short) 224;  // boi so cua 16
    private static final short PHOTO_MAX   = (short) 8192; // 8 KB
    private static final short CHUNK       = (short) 128;  // kich thuoc 1 khoi anh / APDU
    private static final short NONCE_LEN   = (short) 8;
    private static final short BUF_LEN     = (short) 256;

    // ======================= Du lieu luu ben (EEPROM) =======================
    private byte state;
    private final byte[] cardId;

    private final byte[] pinSalt;
    private final byte[] pinHash;
    private final byte[] kekSalt;
    private final byte[] wrappedDek;
    private byte triesLeft;
    private boolean locked;
    private final byte[] lockUntil;   // 4 byte, giay
    private final byte[] lastSeen;    // 4 byte, giay
    private final byte[] lockDuration;

    private final byte[] infoIv;
    private final byte[] infoEnc;
    private short infoLen;

    private final byte[] photoIv;
    private final byte[] photoEnc;
    private short photoLen;

    private final byte[] balance;     // 4 byte, VND, big-endian, khong dau
    private short txCounter;

    private final KeyPair rsaPair;
    private final RSAPrivateKey rsaPriv;
    private final RSAPublicKey rsaPub;

    // ======================= Du lieu tam (RAM) =======================
    private final boolean[] session;  // session[0] = da xac thuc PIN trong phien nay
    private final byte[] in;          // du lieu lenh nhan duoc
    private final byte[] work;        // vung nho tinh toan
    private final AESKey dekKey;      // khoa du lieu (chi ton tai trong RAM sau khi nhap PIN)
    private final AESKey kekKey;      // khoa boc DEK (tao tu PIN)

    private final MessageDigest sha;
    private final Cipher aesEcb;
    private final Cipher aesCbc;
    private final Signature rsaSig;
    private final RandomData rng;

    private MarketCardApplet() {
        state = STATE_EMPTY;
        cardId = new byte[CARD_ID_LEN];

        pinSalt = new byte[SALT_LEN];
        pinHash = new byte[HASH_LEN];
        kekSalt = new byte[SALT_LEN];
        wrappedDek = new byte[AES_LEN];
        triesLeft = MAX_PIN_TRIES;
        locked = false;
        lockUntil = new byte[4];
        lastSeen = new byte[4];
        lockDuration = new byte[4];
        Util.setShort(lockDuration, (short) 2, LOCK_SECONDS);

        infoIv = new byte[AES_LEN];
        infoEnc = new byte[INFO_MAX];
        infoLen = 0;

        photoIv = new byte[AES_LEN];
        photoEnc = new byte[PHOTO_MAX];
        photoLen = 0;

        balance = new byte[4];
        txCounter = 0;

        rsaPair = new KeyPair(KeyPair.ALG_RSA, KeyBuilder.LENGTH_RSA_1024);
        rsaPriv = (RSAPrivateKey) rsaPair.getPrivate();
        rsaPub = (RSAPublicKey) rsaPair.getPublic();

        session = JCSystem.makeTransientBooleanArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
        in = JCSystem.makeTransientByteArray(BUF_LEN, JCSystem.CLEAR_ON_DESELECT);
        work = JCSystem.makeTransientByteArray(BUF_LEN, JCSystem.CLEAR_ON_DESELECT);
        dekKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES_TRANSIENT_DESELECT,
                KeyBuilder.LENGTH_AES_128, false);
        kekKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES_TRANSIENT_DESELECT,
                KeyBuilder.LENGTH_AES_128, false);

        sha = MessageDigest.getInstance(MessageDigest.ALG_SHA, false);
        aesEcb = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD, false);
        aesCbc = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
        rsaSig = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
        rng = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new MarketCardApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void deselect() {
        session[0] = false;
        dekKey.clearKey();
        kekKey.clearKey();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != CLA_MARKET) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        switch (buf[ISO7816.OFFSET_INS]) {
            case INS_INIT:          initCard(apdu); break;
            case INS_GET_PUBKEY:    getPublicKey(apdu); break;
            case INS_GET_STATUS:    getStatus(apdu); break;
            case INS_VERIFY_PIN:    verifyPin(apdu); break;
            case INS_CHANGE_PIN:    changePin(apdu); break;
            case INS_LOGOUT:        logout(); break;
            case INS_GET_INFO:      getInfo(apdu); break;
            case INS_UPDATE_INFO:   updateInfo(apdu); break;
            case INS_SET_PHOTO:     setPhoto(apdu); break;
            case INS_GET_PHOTO:     getPhoto(apdu); break;
            case INS_SET_PHOTO_LEN: setPhotoLen(apdu); break;
            case INS_GET_PHOTO_LEN: getPhotoLen(apdu); break;
            case INS_GET_BALANCE:   getBalance(apdu); break;
            case INS_PAY:           payOrTopUp(apdu, true); break;
            case INS_TOPUP:         payOrTopUp(apdu, false); break;
            case INS_AUTH:          authenticate(apdu); break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    // =====================================================================
    //  KHOI TAO THE
    //  Data: [pinLen][PIN...][infoLen][info...]   -> Tra ve: cardId (8 byte)
    // =====================================================================
    private void initCard(APDU apdu) {
        if (state != STATE_EMPTY) {
            ISOException.throwIt(SW_ALREADY_ISSUED);
        }
        short lc = receiveAll(apdu);
        if (lc < 2) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short off = 0;
        byte pinLen = in[off++];
        checkPinFormat(in, off, pinLen);
        short pinOff = off;
        off += pinLen;
        if (off >= lc) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short iLen = (short) (in[off++] & 0xFF);
        if (iLen > INFO_MAX || (short) (off + iLen) != lc) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short iOff = off;

        // 1. Sinh ma the va cap khoa RSA tren the (khoa bi mat khong bao gio roi khoi the)
        rng.generateData(work, (short) 0, CARD_ID_LEN);
        Util.arrayCopy(work, (short) 0, cardId, (short) 0, CARD_ID_LEN);
        rsaPair.genKeyPair();

        // 2. Sinh khoa du lieu DEK ngau nhien
        rng.generateData(work, (short) 0, AES_LEN);
        dekKey.setKey(work, (short) 0);
        Util.arrayFillNonAtomic(work, (short) 0, AES_LEN, (byte) 0);

        // 3. Luu PIN (bam) + boc DEK bang khoa sinh tu PIN
        JCSystem.beginTransaction();
        storeNewPin(in, pinOff, pinLen);
        JCSystem.commitTransaction();

        // 4. Ma hoa va luu thong tin ca nhan
        storeInfo(in, iOff, iLen);

        // 5. Cac gia tri ban dau
        JCSystem.beginTransaction();
        Util.arrayFillNonAtomic(work, (short) 0, (short) 4, (byte) 0);
        Util.arrayCopy(work, (short) 0, balance, (short) 0, (short) 4);
        Util.arrayCopy(work, (short) 0, lockUntil, (short) 0, (short) 4);
        Util.arrayCopy(work, (short) 0, lastSeen, (short) 0, (short) 4);
        txCounter = 0;
        triesLeft = MAX_PIN_TRIES;
        locked = false;
        photoLen = 0;
        state = STATE_ISSUED;     // ghi cuoi cung: the chi "hop le" khi moi buoc da xong
        JCSystem.commitTransaction();

        session[0] = true;
        send(apdu, cardId, (short) 0, CARD_ID_LEN);
    }

    // =====================================================================
    //  KHOA CONG KHAI: [modLen 2][modulus][expLen 2][exponent]
    // =====================================================================
    private void getPublicKey(APDU apdu) {
        requireIssued();
        short modLen = rsaPub.getModulus(work, (short) 2);
        Util.setShort(work, (short) 0, modLen);
        short expOff = (short) (2 + modLen);
        short expLen = rsaPub.getExponent(work, (short) (expOff + 2));
        Util.setShort(work, expOff, expLen);
        send(apdu, work, (short) 0, (short) (expOff + 2 + expLen));
    }

    // =====================================================================
    //  TRANG THAI THE (khong can PIN)
    //  [state][cardId 8][triesLeft][locked][lockUntil 4][lastSeen 4][maxTries][lockSeconds 2]
    // =====================================================================
    private void getStatus(APDU apdu) {
        short o = 0;
        work[o++] = state;
        o = Util.arrayCopyNonAtomic(cardId, (short) 0, work, o, CARD_ID_LEN);
        work[o++] = triesLeft;
        work[o++] = locked ? (byte) 1 : (byte) 0;
        o = Util.arrayCopyNonAtomic(lockUntil, (short) 0, work, o, (short) 4);
        o = Util.arrayCopyNonAtomic(lastSeen, (short) 0, work, o, (short) 4);
        work[o++] = MAX_PIN_TRIES;
        o = Util.setShort(work, o, LOCK_SECONDS);
        send(apdu, work, (short) 0, o);
    }

    // =====================================================================
    //  XAC THUC PIN.  Data: [time 4][pinLen][PIN]
    // =====================================================================
    private void verifyPin(APDU apdu) {
        short lc = receiveAll(apdu);
        if (lc < 5 || (short) (5 + in[4]) != lc) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        checkPin(in, (short) 0, (short) 5, in[4]);
    }

    // =====================================================================
    //  DOI PIN.  Data: [time 4][oldLen][oldPIN][newLen][newPIN]
    // =====================================================================
    private void changePin(APDU apdu) {
        short lc = receiveAll(apdu);
        if (lc < 6) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte oldLen = in[4];
        if (oldLen < 0 || (short) (5 + oldLen) >= lc) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short newLenOff = (short) (5 + oldLen);
        byte newLen = in[newLenOff];
        if ((short) (newLenOff + 1 + newLen) != lc) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        checkPinFormat(in, (short) (newLenOff + 1), newLen);
        // Phai nhap dung PIN cu (dong thoi nap DEK vao RAM)
        checkPin(in, (short) 0, (short) 5, oldLen);

        JCSystem.beginTransaction();
        storeNewPin(in, (short) (newLenOff + 1), newLen);
        JCSystem.commitTransaction();
    }

    private void logout() {
        session[0] = false;
        dekKey.clearKey();
    }

    // =====================================================================
    //  THONG TIN CA NHAN (ma hoa AES)
    // =====================================================================
    private void getInfo(APDU apdu) {
        requireSession();
        if (infoLen == 0) {
            return;
        }
        short padded = padLen(infoLen);
        aesCbc.init(dekKey, Cipher.MODE_DECRYPT, infoIv, (short) 0, AES_LEN);
        aesCbc.doFinal(infoEnc, (short) 0, padded, work, (short) 0);
        send(apdu, work, (short) 0, infoLen);
    }

    private void updateInfo(APDU apdu) {
        requireSession();
        short lc = receiveAll(apdu);
        if (lc > INFO_MAX) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        storeInfo(in, (short) 0, lc);
    }

    // =====================================================================
    //  ANH CHU THE: ghi/doc tung khoi 128 byte, P1P2 = offset
    // =====================================================================
    private void setPhoto(APDU apdu) {
        requireSession();
        byte[] buf = apdu.getBuffer();
        short offset = Util.getShort(buf, ISO7816.OFFSET_P1);
        short lc = receiveAll(apdu);
        if (offset < 0 || (short) (offset % CHUNK) != 0 || (short) (offset + lc) > PHOTO_MAX
                || lc == 0 || lc > CHUNK || (short) (lc % AES_LEN) != 0) {
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
        }
        if (offset == 0) {
            // anh moi -> IV moi, danh dau chua co anh cho den khi SET_PHOTO_LEN
            rng.generateData(work, (short) 0, AES_LEN);
            JCSystem.beginTransaction();
            Util.arrayCopy(work, (short) 0, photoIv, (short) 0, AES_LEN);
            photoLen = 0;
            JCSystem.commitTransaction();
        }
        photoChunkIv(offset);
        aesCbc.init(dekKey, Cipher.MODE_ENCRYPT, work, (short) 240, AES_LEN);
        aesCbc.doFinal(in, (short) 0, lc, work, (short) 0);
        Util.arrayCopy(work, (short) 0, photoEnc, offset, lc);
    }

    private void getPhoto(APDU apdu) {
        requireSession();
        byte[] buf = apdu.getBuffer();
        short offset = Util.getShort(buf, ISO7816.OFFSET_P1);
        short padded = padLen(photoLen);
        if (offset < 0 || (short) (offset % CHUNK) != 0 || offset >= padded) {
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
        }
        short len = (short) (padded - offset);
        if (len > CHUNK) {
            len = CHUNK;
        }
        photoChunkIv(offset);
        aesCbc.init(dekKey, Cipher.MODE_DECRYPT, work, (short) 240, AES_LEN);
        aesCbc.doFinal(photoEnc, offset, len, work, (short) 0);
        send(apdu, work, (short) 0, len);
    }

    private void setPhotoLen(APDU apdu) {
        requireSession();
        short lc = receiveAll(apdu);
        if (lc != 2) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short len = Util.getShort(in, (short) 0);
        if (len < 0 || len > PHOTO_MAX) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        photoLen = len;
    }

    private void getPhotoLen(APDU apdu) {
        requireSession();
        Util.setShort(work, (short) 0, photoLen);
        send(apdu, work, (short) 0, (short) 2);
    }

    /** IV rieng cho tung khoi anh = photoIv XOR chi so khoi (dat o work[240..255]) */
    private void photoChunkIv(short offset) {
        Util.arrayCopyNonAtomic(photoIv, (short) 0, work, (short) 240, AES_LEN);
        short idx = (short) (offset / CHUNK);
        work[254] ^= (byte) (idx >> 8);
        work[255] ^= (byte) idx;
    }

    // =====================================================================
    //  SO DU: [balance 4][txCounter 2]
    // =====================================================================
    private void getBalance(APDU apdu) {
        requireSession();
        Util.arrayCopyNonAtomic(balance, (short) 0, work, (short) 0, (short) 4);
        Util.setShort(work, (short) 4, txCounter);
        send(apdu, work, (short) 0, (short) 6);
    }

    // =====================================================================
    //  THANH TOAN / NAP TIEN
    //  Data: [time 4][pinLen][PIN][amount 4][nonce 8]
    //  Tra ve: [newBalance 4][txCounter 2][chu ky RSA]
    //  Chu ky tren: [type 'P'/'T'][cardId 8][amount 4][newBalance 4][txCounter 2][nonce 8][time 4]
    // =====================================================================
    private void payOrTopUp(APDU apdu, boolean isPay) {
        short lc = receiveAll(apdu);
        if (lc < 5) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte pinLen = in[4];
        short amtOff = (short) (5 + pinLen);
        short nonceOff = (short) (amtOff + 4);
        if (pinLen < 0 || (short) (nonceOff + NONCE_LEN) != lc) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        if (isZero32(in, amtOff)) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }

        // Moi giao dich deu phai xac thuc PIN
        checkPin(in, (short) 0, (short) 5, pinLen);

        // Tinh so du moi vao work[0..3]
        if (isPay) {
            if (compare32(balance, (short) 0, in, amtOff) < 0) {
                ISOException.throwIt(SW_INSUFFICIENT_FUNDS);  // khong du tien -> tu choi
            }
            sub32(balance, (short) 0, in, amtOff, work, (short) 0);
        } else {
            if (add32(balance, (short) 0, in, amtOff, work, (short) 0)) {
                ISOException.throwIt(SW_BALANCE_OVERFLOW);
            }
        }

        // Tru / cong tien mot cach nguyen to
        JCSystem.beginTransaction();
        Util.arrayCopy(work, (short) 0, balance, (short) 0, (short) 4);
        txCounter++;
        JCSystem.commitTransaction();
        Util.setShort(work, (short) 4, txCounter);

        // Bien lai: ky bang khoa bi mat RSA cua the
        short m = 16;
        short o = m;
        work[o++] = isPay ? (byte) 'P' : (byte) 'T';
        o = Util.arrayCopyNonAtomic(cardId, (short) 0, work, o, CARD_ID_LEN);
        o = Util.arrayCopyNonAtomic(in, amtOff, work, o, (short) 4);
        o = Util.arrayCopyNonAtomic(work, (short) 0, work, o, (short) 6); // newBalance + txCounter
        o = Util.arrayCopyNonAtomic(in, nonceOff, work, o, NONCE_LEN);
        o = Util.arrayCopyNonAtomic(in, (short) 0, work, o, (short) 4);   // time
        rsaSig.init(rsaPriv, Signature.MODE_SIGN);
        short sigLen = rsaSig.sign(work, m, (short) (o - m), work, (short) 64);

        apdu.setOutgoing();
        apdu.setOutgoingLength((short) (6 + sigLen));
        apdu.sendBytesLong(work, (short) 0, (short) 6);
        apdu.sendBytesLong(work, (short) 64, sigLen);
    }

    // =====================================================================
    //  XAC THUC THE (chong the gia): Data = challenge (8..64 byte)
    //  Tra ve chu ky RSA tren ['A'][cardId 8][challenge]
    // =====================================================================
    private void authenticate(APDU apdu) {
        requireIssued();
        short lc = receiveAll(apdu);
        if (lc < 8 || lc > 64) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short o = 0;
        work[o++] = (byte) 'A';
        o = Util.arrayCopyNonAtomic(cardId, (short) 0, work, o, CARD_ID_LEN);
        o = Util.arrayCopyNonAtomic(in, (short) 0, work, o, lc);
        rsaSig.init(rsaPriv, Signature.MODE_SIGN);
        short sigLen = rsaSig.sign(work, (short) 0, o, work, (short) 96);
        send(apdu, work, (short) 96, sigLen);
    }

    // =====================================================================
    //  LOGIC PIN + KHOA THE CO THOI HAN
    // =====================================================================
    /**
     * Kiem tra PIN. Neu dung: mo phien, nap DEK vao RAM.
     * Neu sai: giam so lan thu; het luot -> khoa the LOCK_SECONDS giay.
     */
    private void checkPin(byte[] data, short timeOff, short pinOff, byte pinLen) {
        requireIssued();
        updateClock(data, timeOff);

        // Truong hop dac biet: mat dien giua chung luc khoa -> khoa lai cho chac
        if (!locked && triesLeft <= 0) {
            lockCard();
        }
        if (locked) {
            if (compare32(lastSeen, (short) 0, lockUntil, (short) 0) >= 0) {
                // Het thoi gian khoa -> tu dong mo khoa
                JCSystem.beginTransaction();
                locked = false;
                triesLeft = MAX_PIN_TRIES;
                JCSystem.commitTransaction();
            } else {
                ISOException.throwIt(SW_CARD_LOCKED);
            }
        }
        if (pinLen < MIN_PIN_LEN || pinLen > MAX_PIN_LEN) {
            ISOException.throwIt(SW_BAD_PIN_FORMAT);
        }

        // Giam bo dem TRUOC khi so sanh (chong tan cong rut the giua chung)
        triesLeft--;

        hashPin(pinSalt, data, pinOff, pinLen, work, (short) 0);
        if (Util.arrayCompare(work, (short) 0, pinHash, (short) 0, HASH_LEN) == 0) {
            triesLeft = MAX_PIN_TRIES;
            session[0] = true;
            loadDek(data, pinOff, pinLen);
            Util.arrayFillNonAtomic(work, (short) 0, HASH_LEN, (byte) 0);
            return;
        }

        session[0] = false;
        dekKey.clearKey();
        if (triesLeft <= 0) {
            lockCard();
            ISOException.throwIt(SW_CARD_LOCKED);
        }
        ISOException.throwIt((short) (SW_WRONG_PIN_BASE | triesLeft));
    }

    private void lockCard() {
        add32(lastSeen, (short) 0, lockDuration, (short) 0, work, (short) 200);
        JCSystem.beginTransaction();
        Util.arrayCopy(work, (short) 200, lockUntil, (short) 0, (short) 4);
        triesLeft = 0;
        locked = true;
        JCSystem.commitTransaction();
    }

    /** Thoi gian chi di tien: lastSeen = max(lastSeen, time) */
    private void updateClock(byte[] data, short timeOff) {
        if (compare32(data, timeOff, lastSeen, (short) 0) > 0) {
            Util.arrayCopy(data, timeOff, lastSeen, (short) 0, (short) 4);
        }
    }

    /** Luu PIN moi: hash co salt + boc lai DEK (dang nam trong dekKey) bang KEK moi. */
    private void storeNewPin(byte[] pin, short off, byte len) {
        // salt moi
        rng.generateData(work, (short) 0, SALT_LEN);
        Util.arrayCopy(work, (short) 0, pinSalt, (short) 0, SALT_LEN);
        rng.generateData(work, (short) 0, SALT_LEN);
        Util.arrayCopy(work, (short) 0, kekSalt, (short) 0, SALT_LEN);

        // hash PIN
        hashPin(pinSalt, pin, off, len, work, (short) 0);
        Util.arrayCopy(work, (short) 0, pinHash, (short) 0, HASH_LEN);

        // KEK = SHA-1(kekSalt || PIN)[0..15]
        hashPin(kekSalt, pin, off, len, work, (short) 32);
        kekKey.setKey(work, (short) 32);

        // wrappedDek = AES-ECB(KEK, DEK)
        dekKey.getKey(work, (short) 64);
        aesEcb.init(kekKey, Cipher.MODE_ENCRYPT);
        aesEcb.doFinal(work, (short) 64, AES_LEN, work, (short) 96);
        Util.arrayCopy(work, (short) 96, wrappedDek, (short) 0, AES_LEN);

        kekKey.clearKey();
        Util.arrayFillNonAtomic(work, (short) 0, (short) 128, (byte) 0);
    }

    /** Giai boc DEK bang KEK sinh tu PIN vua nhap, dat vao dekKey (RAM). */
    private void loadDek(byte[] pin, short off, byte len) {
        hashPin(kekSalt, pin, off, len, work, (short) 32);
        kekKey.setKey(work, (short) 32);
        aesEcb.init(kekKey, Cipher.MODE_DECRYPT);
        aesEcb.doFinal(wrappedDek, (short) 0, AES_LEN, work, (short) 64);
        dekKey.setKey(work, (short) 64);
        kekKey.clearKey();
        Util.arrayFillNonAtomic(work, (short) 32, (short) 48, (byte) 0);
    }

    private void hashPin(byte[] salt, byte[] pin, short off, byte len, byte[] out, short outOff) {
        sha.reset();
        sha.update(salt, (short) 0, SALT_LEN);
        sha.doFinal(pin, off, len, out, outOff);
    }

    private void checkPinFormat(byte[] pin, short off, byte len) {
        if (len < MIN_PIN_LEN || len > MAX_PIN_LEN) {
            ISOException.throwIt(SW_BAD_PIN_FORMAT);
        }
        for (short i = 0; i < len; i++) {
            byte c = pin[(short) (off + i)];
            if (c < (byte) '0' || c > (byte) '9') {
                ISOException.throwIt(SW_BAD_PIN_FORMAT);
            }
        }
    }

    /** Ma hoa thong tin (AES-CBC, IV ngau nhien moi lan ghi) va luu vao EEPROM. */
    private void storeInfo(byte[] src, short off, short len) {
        short padded = padLen(len);
        Util.arrayFillNonAtomic(work, (short) 0, BUF_LEN, (byte) 0);
        Util.arrayCopyNonAtomic(src, off, work, (short) 0, len);
        rng.generateData(work, (short) 240, AES_LEN);
        if (padded > 0) {
            aesCbc.init(dekKey, Cipher.MODE_ENCRYPT, work, (short) 240, AES_LEN);
            aesCbc.doFinal(work, (short) 0, padded, in, (short) 0);
        }
        JCSystem.beginTransaction();
        Util.arrayCopy(work, (short) 240, infoIv, (short) 0, AES_LEN);
        if (padded > 0) {
            Util.arrayCopy(in, (short) 0, infoEnc, (short) 0, padded);
        }
        infoLen = len;
        JCSystem.commitTransaction();
        Util.arrayFillNonAtomic(work, (short) 0, BUF_LEN, (byte) 0);
    }

    // =====================================================================
    //  TIEN ICH
    // =====================================================================
    private void requireIssued() {
        if (state != STATE_ISSUED) {
            ISOException.throwIt(SW_NOT_ISSUED);
        }
    }

    private void requireSession() {
        requireIssued();
        if (!session[0] || !dekKey.isInitialized()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
    }

    /** Nhan toan bo du lieu lenh vao mang in[] (ho tro bo dem APDU nho). */
    private short receiveAll(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short lc = (short) (buf[ISO7816.OFFSET_LC] & 0xFF);
        short read = apdu.setIncomingAndReceive();
        short pos = 0;
        while (read > 0) {
            if ((short) (pos + read) > BUF_LEN) {
                ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            }
            Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, in, pos, read);
            pos += read;
            read = apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }
        if (pos != lc) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        return lc;
    }

    private void send(APDU apdu, byte[] src, short off, short len) {
        apdu.setOutgoing();
        apdu.setOutgoingLength(len);
        apdu.sendBytesLong(src, off, len);
    }

    private static short padLen(short len) {
        return (short) (((short) (len + 15) / 16) * 16);
    }

    // ---- So hoc 32 bit khong dau bang short (Java Card khong bat buoc ho tro int) ----

    private static short compare32(byte[] a, short aOff, byte[] b, short bOff) {
        for (short i = 0; i < 4; i++) {
            short x = (short) (a[(short) (aOff + i)] & 0xFF);
            short y = (short) (b[(short) (bOff + i)] & 0xFF);
            if (x != y) {
                return x > y ? (short) 1 : (short) -1;
            }
        }
        return 0;
    }

    /** out = a + b; tra ve true neu tran */
    private static boolean add32(byte[] a, short aOff, byte[] b, short bOff, byte[] out, short oOff) {
        short carry = 0;
        for (short i = 3; i >= 0; i--) {
            short s = (short) ((a[(short) (aOff + i)] & 0xFF) + (b[(short) (bOff + i)] & 0xFF) + carry);
            out[(short) (oOff + i)] = (byte) s;
            carry = (short) ((s >> 8) & 0x01);
        }
        return carry != 0;
    }

    /** out = a - b (gia thiet a >= b) */
    private static void sub32(byte[] a, short aOff, byte[] b, short bOff, byte[] out, short oOff) {
        short borrow = 0;
        for (short i = 3; i >= 0; i--) {
            short d = (short) ((a[(short) (aOff + i)] & 0xFF) - (b[(short) (bOff + i)] & 0xFF) - borrow);
            if (d < 0) {
                d += 256;
                borrow = 1;
            } else {
                borrow = 0;
            }
            out[(short) (oOff + i)] = (byte) d;
        }
    }

    private static boolean isZero32(byte[] a, short off) {
        return (short) (a[off] | a[(short) (off + 1)] | a[(short) (off + 2)] | a[(short) (off + 3)]) == 0;
    }
}
