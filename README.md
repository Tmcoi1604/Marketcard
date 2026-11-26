# MarketCard – Thẻ thanh toán siêu thị dùng Smart Card (Java Card)

Project môn Smart Card: xây dựng **thẻ thanh toán siêu thị** gồm 2 phần:

| Phần | Thư mục | Công nghệ |
|---|---|---|
| Applet chạy trên thẻ | [applet/src/marketcard/MarketCardApplet.java](applet/src/marketcard/MarketCardApplet.java) | Java Card (JCIDE), CLA `B0` |
| Phần mềm máy trạm (quầy thu ngân) | [host/src/marketcard/app/](host/src/marketcard/app/) | Java Swing + `javax.smartcardio` + SQLite |

---

## 1. Bài toán

Siêu thị phát hành cho khách hàng một thẻ thông minh dùng để **nạp tiền** và **thanh toán** tại quầy.

- Thẻ lưu thông tin chủ thẻ (mã KH, họ tên, ngày sinh, SĐT, địa chỉ, ảnh) dưới dạng **mã hóa AES**.
- Chỉ chính chủ (biết **mã PIN**) mới dùng được thẻ; chính chủ có thể **đổi PIN**.
- **Mỗi lần thanh toán đều phải nhập PIN**; thẻ **tự trừ tiền** trong số dư.
- **Không đủ tiền → thẻ từ chối giao dịch**, phần mềm **gửi thông báo** cho chủ thẻ.
- Nhập **sai PIN 3 lần → thẻ tự khóa có thời hạn** (mặc định 5 phút), hết hạn thẻ tự mở khóa.
- Thẻ có **cặp khóa RSA** riêng: dùng để xác thực thẻ thật/giả và ký biên lai mỗi giao dịch.

## 2. Đáp ứng yêu cầu

| Yêu cầu | Cách thực hiện |
|---|---|
| Xác minh chính chủ bằng PIN, đổi được PIN | Lệnh `VERIFY_PIN (20)`, `CHANGE_PIN (21)`. PIN **không lưu dạng rõ**: thẻ lưu `SHA-1(salt ‖ PIN)`, salt ngẫu nhiên đổi mỗi lần đổi PIN. Đổi PIN phải nhập đúng PIN cũ. |
| Mỗi lần thanh toán đều cần PIN | Lệnh `PAY (41)` **bắt buộc** chứa PIN trong dữ liệu; thẻ kiểm tra PIN ngay trong lệnh, không phụ thuộc phiên đăng nhập trước. Nạp tiền `TOPUP (42)` cũng vậy. |
| Tự động trừ tiền | Thẻ tự tính `số dư mới = số dư − số tiền` (số học 32 bit) và ghi trong `JCSystem.beginTransaction/commitTransaction` (TH3) → không bao giờ trừ "nửa chừng". |
| Không đủ tiền → từ chối + thông báo | Thẻ trả `SW = 6A90`, số dư giữ nguyên. Phần mềm ghi giao dịch `DECLINED_FUNDS`, hiện hộp thoại + thông báo hệ thống (khay Windows) + lưu vào bảng `notifications` với số dư hiện tại và số tiền còn thiếu. |
| Khóa thẻ có thời hạn khi sai 3 lần | Bộ đếm `triesLeft` (giảm **trước** khi so sánh để chống rút thẻ). Về 0 → `locked = true`, `lockUntil = thời điểm + 300 giây`. Trong thời gian khóa mọi lệnh có PIN trả `6983`. Hết hạn → tự mở khóa, bộ đếm về 3. |
| (Yêu cầu chung) Mã hóa thông tin bằng AES, khóa từ PIN | Thông tin + ảnh mã hóa **AES-128-CBC** bằng khóa dữ liệu **DEK** ngẫu nhiên. DEK được "bọc" bằng **KEK = SHA-1(kekSalt ‖ PIN)[0..15]** ⇒ khóa AES sinh từ PIN. DEK chỉ nằm trong RAM (`TYPE_AES_TRANSIENT_DESELECT`) sau khi nhập đúng PIN. Đổi PIN chỉ cần bọc lại DEK. |
| (Yêu cầu chung) RSA | Thẻ tự sinh **RSA-1024** khi phát hành (`KeyPair.genKeyPair`, TH6). Khóa công khai lưu vào CSDL. Khi kết nối: xác thực thẻ bằng **challenge–response**. Mỗi giao dịch thẻ **ký biên lai** (`ALG_RSA_SHA_PKCS1`), máy trạm kiểm tra chữ ký. |
| (Yêu cầu chung) Có CSDL, giao diện, demo | SQLite `host/data/marketcard.db`: khách hàng, sản phẩm, giao dịch, chi tiết hóa đơn, thông báo. Giao diện Swing 5 tab + nhật ký APDU. |

## 3. Cấu trúc thư mục

```
Marketcard/
├── README.md
├── applet/
│   └── src/marketcard/MarketCardApplet.java   ← applet Java Card (nạp vào JCIDE)
└── host/
    ├── build.bat / run.bat                    ← biên dịch / chạy phần mềm
    ├── lib/                                   ← đặt sqlite-jdbc-*.jar vào đây
    ├── data/marketcard.db                     ← CSDL (tự tạo khi chạy lần đầu)
    └── src/marketcard/app/
        ├── App.java              điểm khởi chạy
        ├── MainFrame.java        giao diện chính (GUI.java trong Connect.pdf)
        ├── SmartCardWork.java    các hàm giao tiếp thẻ – mỗi hàm = 1 lệnh APDU
        ├── PcscTransport.java    kết nối PC/SC: terminal.connect("T=0") (Connect.pdf)
        ├── CardTransport.java    interface kênh APDU
        ├── Database.java         CSDL SQLite
        ├── CardInfo.java, CardStatus.java, TxReceipt.java   mô hình dữ liệu
        ├── CardOpException.java  mã lỗi SW → thông báo tiếng Việt
        ├── PinDialog.java, Ui.java, Hex.java                giao diện / tiện ích
```

## 4. Thiết kế applet

### 4.1 AID & CLA

| | Giá trị |
|---|---|
| Package AID | `11 22 33 44 55 00` |
| Applet AID | `11 22 33 44 55 00 00` |
| CLA | `B0` (theo Connect.pdf) |

> Nếu đổi AID trong JCIDE thì sửa hằng `APPLET_AID` trong `SmartCardWork.java` cho khớp.

### 4.2 Bảng lệnh APDU

| INS | Lệnh | Cần | Dữ liệu gửi | Dữ liệu trả về |
|---|---|---|---|---|
| `10` | INIT – phát hành thẻ | thẻ trắng | `pinLen ‖ PIN ‖ infoLen ‖ info` | `cardId` (8 byte) |
| `12` | GET_PUBKEY | đã phát hành | – | `modLen(2) ‖ modulus ‖ expLen(2) ‖ exponent` |
| `13` | GET_STATUS | – | – | `state ‖ cardId(8) ‖ triesLeft ‖ locked ‖ lockUntil(4) ‖ lastSeen(4) ‖ maxTries ‖ lockSeconds(2)` |
| `20` | VERIFY_PIN | – | `time(4) ‖ pinLen ‖ PIN` | – |
| `21` | CHANGE_PIN | PIN cũ | `time(4) ‖ oldLen ‖ old ‖ newLen ‖ new` | – |
| `22` | LOGOUT | – | – | – |
| `30` | GET_INFO | phiên PIN | – | thông tin (đã giải mã) |
| `31` | UPDATE_INFO | phiên PIN | thông tin mới (≤ 224 byte) | – |
| `32` | SET_PHOTO | phiên PIN | P1P2 = offset, ≤ 128 byte | – |
| `33` | GET_PHOTO | phiên PIN | P1P2 = offset | ≤ 128 byte ảnh |
| `34` / `35` | SET / GET_PHOTO_LEN | phiên PIN | độ dài (2) | độ dài (2) |
| `40` | GET_BALANCE | phiên PIN | – | `balance(4) ‖ txCounter(2)` |
| `41` | PAY | **PIN trong lệnh** | `time(4) ‖ pinLen ‖ PIN ‖ amount(4) ‖ nonce(8)` | `newBalance(4) ‖ txCounter(2) ‖ chữ ký RSA(128)` |
| `42` | TOPUP | **PIN trong lệnh** | như PAY | như PAY |
| `50` | AUTH – xác thực thẻ | đã phát hành | challenge (8–64 byte) | chữ ký RSA trên `'A' ‖ cardId ‖ challenge` |

Chữ ký biên lai được tính trên: `'P'/'T' ‖ cardId ‖ amount ‖ newBalance ‖ txCounter ‖ nonce ‖ time`.
`nonce` ngẫu nhiên do máy trạm tạo ⇒ không thể dùng lại (replay) biên lai cũ.

### 4.3 Status word

| SW | Ý nghĩa |
|---|---|
| `9000` | Thành công |
| `63Cx` | Sai PIN, còn `x` lần thử |
| `6983` | Thẻ đang bị khóa tạm thời |
| `6A90` | **Số dư không đủ** – giao dịch bị từ chối |
| `6A91` | Nạp tiền vượt giới hạn số dư (32 bit) |
| `6A92` / `6A93` | Thẻ chưa phát hành / đã phát hành |
| `6A94` | PIN không đúng định dạng (4–8 chữ số) |
| `6982` | Chưa xác thực PIN |

### 4.4 Khóa thẻ có thời hạn – vì sao cần gửi "time"?

Thẻ Java Card **không có đồng hồ**. Vì vậy máy trạm gửi kèm thời gian hiện tại (giây Unix, 4 byte) trong mọi lệnh chứa PIN. Thẻ:

1. Lưu `lastSeen = max(lastSeen, time)` ⇒ thời gian **chỉ được tiến**, lùi đồng hồ máy tính không giúp mở khóa sớm.
2. Khi khóa: `lockUntil = lastSeen + LOCK_SECONDS`.
3. Mỗi lần nhập PIN: nếu `lastSeen < lockUntil` → `6983`; ngược lại tự mở khóa và cho thử lại 3 lần.

Muốn demo nhanh, sửa `LOCK_SECONDS` trong applet (ví dụ `60`) rồi build lại.

### 4.5 Kiến thức từ các bài thực hành đã áp dụng

| Bài | Áp dụng trong project |
|---|---|
| TH1 – APDU | `setIncomingAndReceive` + vòng `receiveBytes` (hàm `receiveAll`, hỗ trợ bộ đệm 133 byte), `setOutgoing/setOutgoingLength/sendBytesLong` (gửi phản hồi từ nhiều vùng nhớ trong `PAY`) |
| TH2 – Đối tượng tạm thời | `makeTransientByteArray/BooleanArray(CLEAR_ON_DESELECT)` cho bộ đệm, cờ phiên PIN; khóa AES transient |
| TH3 – Giao dịch | `beginTransaction/commitTransaction` khi trừ/cộng tiền, đổi PIN, ghi thông tin |
| TH4 – install | Cấp phát toàn bộ đối tượng trong constructor, `register(bArray, bOffset+1, bArray[bOffset])` |
| TH6 – Mật mã | `MessageDigest.ALG_SHA`, `Cipher.ALG_AES_BLOCK_128_ECB/CBC_NOPAD`, `KeyPair.ALG_RSA` 1024, `Signature.ALG_RSA_SHA_PKCS1`, `RandomData` |
| Connect.pdf | `TerminalFactory → terminal.connect("T=0") → SELECT AID`, CLA `B0`, tách `SmartCardWork` / giao diện |

## 5. CSDL (SQLite)

| Bảng | Nội dung |
|---|---|
| `customers` | `card_id`, mã KH, họ tên, SĐT, **khóa công khai RSA** của thẻ, ngày phát hành |
| `products` | danh mục hàng (tự thêm 12 sản phẩm mẫu lần đầu chạy) |
| `transactions` | mỗi lần nạp/thanh toán: số tiền, số dư sau, số GD trên thẻ, trạng thái (`SUCCESS`, `DECLINED_FUNDS`, `FAILED_PIN`), chữ ký RSA, kết quả kiểm tra chữ ký |
| `transaction_items` | chi tiết hóa đơn (sản phẩm, SL, đơn giá) |
| `notifications` | thông báo gửi tới chủ thẻ (thanh toán, từ chối, sai PIN, khóa thẻ, đổi PIN…) |

Thông tin cá nhân đầy đủ chỉ nằm **trên thẻ** (đã mã hóa); CSDL chỉ giữ thông tin tối thiểu để tra cứu.

## 6. Hướng dẫn chạy

### 6.1 Yêu cầu

- **JDK 11 trở lên** (máy đang có JDK 17 – đủ dùng).
- **JCIDE** (máy ảo Java Card + đầu đọc ảo PC/SC) – như trong Connect.pdf.
- Thư viện **SQLite JDBC**: `sqlite-jdbc-3.44.1.0.jar`.
- (Tuỳ chọn) NetBeans nếu muốn mở bằng IDE.

### 6.2 Tải thư viện SQLite

Tải 2 file từ Maven Central và đặt vào `host/lib/`:

- `sqlite-jdbc-3.44.1.0.jar` – `https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.44.1.0/`
- `slf4j-api-1.7.36.jar` (thư viện log mà sqlite-jdbc bắt buộc phải có) – `https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/`

> Khi chạy có thể thấy cảnh báo `SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder"` – vô hại, bỏ qua.

### 6.3 Nạp applet vào JCIDE

1. Mở JCIDE → **File → New → Project**, đặt tên `MarketCard`.
2. Tạo package `marketcard` với **Package AID** `1122334455 00`.
3. Tạo applet `MarketCardApplet` với **Applet AID** `1122334455 0000`.
4. Thay nội dung file applet bằng [applet/src/marketcard/MarketCardApplet.java](applet/src/marketcard/MarketCardApplet.java).
5. **Build** (biên dịch) → bấm **Run/Debug** để khởi động máy ảo; JCIDE tự cài applet và mở đầu đọc ảo.
6. (Kiểm tra nhanh trong Debug Shell của JCIDE)
   ```
   /select 11223344550000        → 90 00
   /send B013000016              → trạng thái thẻ (byte đầu 00 = thẻ trắng) + 90 00
   ```

### 6.4 Chạy phần mềm máy trạm

Cách 1 – dòng lệnh (trong thư mục `host`):

```powershell
cd F:\Marketcard\host
```

```powershell
.\build.bat
```

```powershell
.\run.bat
```

Cách 2 – NetBeans (bản mới, mục nằm trong nhóm **Java with Ant**):

1. **File → New Project** → ở *Categories* bấm mũi tên mở **Java with Ant** → chọn
   **Java Project with Existing Sources** → **Next** (lần đầu NetBeans có thể kích hoạt tính năng Java SE, chờ xong).
2. *Project Name*: `MarketCard`; *Project Folder*: `F:\Marketcard\host` → **Next**.
3. *Source Package Folders* → **Add Folder…** → chọn `F:\Marketcard\host\src` → **Finish**.
   Không thêm thư mục `applet` (applet cần API Java Card, chỉ biên dịch trong JCIDE).
4. Chuột phải project → **Properties**:
   - **Sources**: *Encoding* = `UTF-8`, *Source/Binary Format* = `17` (hoặc ≥ 11).
   - **Libraries** → *Classpath* **+** → **Add JAR/Folder** → chọn 2 file jar trong `host\lib`.
   - **Run**: *Main Class* = `marketcard.app.App`; *Working Directory* = `F:\Marketcard\host`.
5. Mở JCIDE và chạy máy ảo trước, rồi bấm **Run Project (F6)**.

### 6.5 Kịch bản demo

1. Chọn đầu đọc ảo của JCIDE ở góc trên → **Kết nối** (thanh trạng thái hiện *Thẻ trắng*).
2. Tab **Phát hành thẻ**: nhập thông tin, chọn ảnh, đặt PIN (vd `1234`) → **Khởi tạo**.
   Thẻ sinh RSA, khóa công khai lưu CSDL, thanh trạng thái hiện **RSA: Thẻ hợp lệ**.
3. Tab **Tài khoản chủ thẻ** → **Đăng nhập bằng PIN** → thông tin được giải mã từ thẻ.
   Thử **Lưu thông tin**, **Đổi ảnh**, **Đổi mã PIN**.
4. Tab **Nạp tiền**: nạp 200.000 đ → nhập PIN → biên lai có chữ ký RSA hợp lệ.
5. Tab **Thanh toán**: thêm hàng vào giỏ → **Thanh toán bằng thẻ** → nhập PIN → thẻ tự trừ tiền, in biên lai.
6. Thanh toán một giỏ hàng lớn hơn số dư → thẻ trả `6A90`, giao dịch **bị từ chối**, hiện thông báo
   (số dư hiện tại, số tiền còn thiếu) – xem lại ở tab **Lịch sử & Thông báo**.
7. Nhập sai PIN 3 lần → thẻ **khóa**, thanh trạng thái đếm ngược `THẺ BỊ KHÓA – còn mm:ss`;
   nhập PIN đúng trong thời gian này vẫn bị từ chối. Hết thời gian → nhập PIN lại bình thường.
8. Khung **Nhật ký APDU** ở dưới hiển thị mọi lệnh/phản hồi (dữ liệu chứa PIN được ẩn).

> Khởi động lại máy ảo JCIDE sẽ tạo lại thẻ trắng (thẻ cũ trong CSDL vẫn còn, cứ phát hành thẻ mới).
> Muốn xóa sạch dữ liệu máy trạm: xóa `host/data/marketcard.db`.

## 7. Kiểm thử đã thực hiện

Applet đã được chạy thử trên một bộ giả lập API Java Card (dùng JCA của JDK), điều khiển bằng chính
`SmartCardWork` của phần mềm máy trạm, bộ đệm APDU 133 byte. 52/52 trường hợp đạt, gồm:
phát hành, sai/đúng PIN, mã hóa – giải mã thông tin & ảnh, mất phiên khi chọn lại applet, nạp tiền,
thanh toán, **từ chối khi không đủ tiền (số dư không đổi)**, tràn số dư, chữ ký biên lai (kể cả biên lai bị sửa),
đổi PIN (vẫn giải mã được dữ liệu cũ), **khóa 3 lần sai – vẫn khóa khi PIN đúng / khi lùi đồng hồ – tự mở sau 300 giây**.

CSDL SQLite được kiểm thử cùng thẻ giả lập (11/11 đạt): lưu/đọc khóa công khai và xác thực RSA bằng khóa trong CSDL,
lưu giao dịch thành công / bị từ chối, tồn kho chỉ giảm khi thanh toán thành công, lưu thông báo.
Giao diện đã mở được với CSDL thật, ảnh chụp các tab nằm trong [docs/screenshots/](docs/screenshots/).

Chưa kiểm thử giao dịch thật với máy ảo JCIDE (phần mềm đã nhận ra đầu đọc ảo `JAVACOS Virtual Contact Reader 0`).
Nếu JCIDE báo thiếu API (vd `TYPE_AES_TRANSIENT_DESELECT`), hãy chọn phiên bản Java Card ≥ 2.2.2 trong cấu hình project.

## 8. Giới hạn & hướng phát triển

- Thời gian khóa dựa vào đồng hồ máy trạm (thẻ không có RTC); đã chặn lùi giờ, nhưng máy trạm gian lận có thể tua nhanh giờ.
- Dùng RSA-1024/SHA-1 để tương thích JCIDE và tài liệu TH6; thẻ thật nên dùng RSA-2048/SHA-256.
- Quên PIN: không có PUK ⇒ phải phát hành thẻ mới (có thể bổ sung mã PUK của siêu thị).
- Có thể bổ sung: lưu lịch sử giao dịch ngay trên thẻ, hạn mức thanh toán theo ngày, tích điểm thành viên.
