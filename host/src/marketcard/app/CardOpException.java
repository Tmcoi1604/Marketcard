package marketcard.app;

/** Loi tra ve tu the (status word khac 9000) kem thong bao tieng Viet. */
public class CardOpException extends Exception {

    public static final int SW_OK = 0x9000;
    public static final int SW_CARD_LOCKED = 0x6983;
    public static final int SW_INSUFFICIENT_FUNDS = 0x6A90;
    public static final int SW_BALANCE_OVERFLOW = 0x6A91;
    public static final int SW_NOT_ISSUED = 0x6A92;
    public static final int SW_ALREADY_ISSUED = 0x6A93;
    public static final int SW_BAD_PIN_FORMAT = 0x6A94;
    public static final int SW_SECURITY_NOT_SATISFIED = 0x6982;

    private final int sw;

    public CardOpException(int sw) {
        super(describe(sw));
        this.sw = sw;
    }

    public CardOpException(String message) {
        super(message);
        this.sw = -1;
    }

    public int getSw() {
        return sw;
    }

    public boolean isWrongPin() {
        return (sw & 0xFFF0) == 0x63C0;
    }

    public int triesLeft() {
        return isWrongPin() ? (sw & 0x0F) : -1;
    }

    public boolean isLocked() {
        return sw == SW_CARD_LOCKED;
    }

    public boolean isInsufficientFunds() {
        return sw == SW_INSUFFICIENT_FUNDS;
    }

    public static String describe(int sw) {
        if ((sw & 0xFFF0) == 0x63C0) {
            return "Sai mã PIN. Còn " + (sw & 0x0F) + " lần thử.";
        }
        switch (sw) {
            case SW_OK: return "Thành công";
            case SW_CARD_LOCKED: return "Thẻ đang bị KHÓA TẠM THỜI do nhập sai PIN quá 3 lần.";
            case SW_INSUFFICIENT_FUNDS: return "Số dư trong thẻ không đủ để thanh toán.";
            case SW_BALANCE_OVERFLOW: return "Số dư vượt quá giới hạn cho phép của thẻ.";
            case SW_NOT_ISSUED: return "Thẻ chưa được phát hành (khởi tạo).";
            case SW_ALREADY_ISSUED: return "Thẻ đã được phát hành, không thể khởi tạo lại.";
            case SW_BAD_PIN_FORMAT: return "Mã PIN phải gồm 4-8 chữ số.";
            case SW_SECURITY_NOT_SATISFIED: return "Cần xác thực mã PIN trước.";
            case 0x6A82: return "Không tìm thấy applet MarketCard trên thẻ (sai AID?).";
            case 0x6400: return "Thẻ bị vô hiệu hóa.";
            case 0x6700: return "Sai độ dài dữ liệu.";
            case 0x6A80: return "Dữ liệu không hợp lệ.";
            case 0x6B00: return "Tham số P1/P2 không hợp lệ.";
            case 0x6D00: return "Lệnh không được hỗ trợ (INS).";
            case 0x6E00: return "CLA không được hỗ trợ.";
            default: return String.format("Lỗi từ thẻ (SW=%04X)", sw);
        }
    }
}
