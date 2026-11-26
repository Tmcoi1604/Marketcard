package marketcard.app;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Thong tin ca nhan chu the. Duoc dong goi thanh cac truong [do dai 1 byte][UTF-8]
 * truoc khi gui xuong the; the se ma hoa AES toan bo khoi nay.
 */
public class CardInfo {

    /** Gioi han cua applet (INFO_MAX). */
    public static final int MAX_BYTES = 224;

    public String customerCode = "";
    public String fullName = "";
    public String birthDate = "";
    public String phone = "";
    public String address = "";

    public CardInfo() {
    }

    public CardInfo(String customerCode, String fullName, String birthDate, String phone, String address) {
        this.customerCode = customerCode.trim();
        this.fullName = fullName.trim();
        this.birthDate = birthDate.trim();
        this.phone = phone.trim();
        this.address = address.trim();
    }

    public byte[] toBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String f : new String[]{customerCode, fullName, birthDate, phone, address}) {
            byte[] b = (f == null ? "" : f).getBytes(StandardCharsets.UTF_8);
            if (b.length > 255) {
                throw new IllegalArgumentException("Trường thông tin quá dài: " + f);
            }
            out.write(b.length);
            out.write(b, 0, b.length);
        }
        byte[] data = out.toByteArray();
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("Thông tin quá dài (" + data.length + "/" + MAX_BYTES
                    + " byte). Hãy rút gọn họ tên hoặc địa chỉ.");
        }
        return data;
    }

    public static CardInfo fromBytes(byte[] data) {
        CardInfo info = new CardInfo();
        String[] f = new String[5];
        int pos = 0;
        for (int i = 0; i < f.length; i++) {
            if (pos >= data.length) {
                f[i] = "";
                continue;
            }
            int len = data[pos++] & 0xFF;
            len = Math.min(len, data.length - pos);
            f[i] = new String(data, pos, len, StandardCharsets.UTF_8);
            pos += len;
        }
        info.customerCode = f[0];
        info.fullName = f[1];
        info.birthDate = f[2];
        info.phone = f[3];
        info.address = f[4];
        return info;
    }
}
