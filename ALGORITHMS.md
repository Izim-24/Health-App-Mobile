# Thuật toán & logic nghiệp vụ

Tài liệu này giải thích **cách từng thuật toán hoạt động, vì sao chọn cách đó, trường hợp biên và test nào chứng minh**.
Mọi quy tắc nghiệp vụ nằm trong `app/src/main/java/com/healthapp/domain/usecase/` dưới dạng **hàm thuần Kotlin**
(không phụ thuộc Android) nên kiểm thử được bằng JVM test, chạy nhanh và lặp lại được.

| # | Thuật toán | File | Test |
|---|---|---|---|
| 1 | Điểm sức khỏe 0–100 | `HealthScore.kt` | `HealthScoreTest` (7) |
| 2 | Lịch sử điểm theo tuần + nhận xét | `ScoreHistory.kt` | `ScoreHistoryTest` (9) |
| 3 | BMI châu Á, BMR/TDEE, macro, nước | `HealthCalculations.kt` | `HealthCalculationsTest` (8) |
| 4 | Lịch thuốc, tuân thủ, liều tiếp theo, số viên | `MedicationSchedule.kt` | `MedicationScheduleTest` (8) |
| 5 | Lịch nhắc nhở | `ReminderSchedule.kt` | `ReminderScheduleTest` (7) |
| 6 | Nhắc uống nước thông minh | `WaterPace.kt` | `WaterPaceTest` (3) |
| 7 | Quét món ăn: chuẩn hóa, khẩu phần, kiểm tra calo | `FoodScan.kt` + `server/index.mjs` | `FoodScanTest` (7), backend (18) |
| 8 | Nhập dữ liệu Health Connect | `data/healthconnect/HealthConnectMapper.kt` | `HealthConnectMapperTest` (6) |
| 9 | Báo thức thuốc chính xác | `data/reminder/MedicationScheduler.kt` | kiểm thử trên emulator |
| 10 | Sao lưu / khôi phục | `data/repository/DataManagementRepositoryImpl.kt` | kiểm thử trên emulator |
| 11 | Tìm món không phân biệt dấu | `FoodSearch.kt` + `data/food/VietnameseFoodLibrary.kt` | `FoodSearchTest` (3), `VietnameseFoodLibraryTest` (3), emulator (1) |

---

## 1. Điểm sức khỏe (HealthScore)

**Mục đích:** một con số dễ hiểu tổng hợp nhiều mặt sức khỏe, *có giải thích từng phần* (không phải "hộp đen").

**Cách tính — mỗi mặt chấm 0–100 theo khuyến nghị công khai:**

| Mặt | Trọng số | Công thức | Nguồn |
|---|---|---|---|
| Giấc ngủ (TB 7 đêm) | 20 | h < 7: `100 − (7 − h)·25`; 7–9 h: `100`; h > 9: `100 − (h − 9)·20` | AASM / National Sleep Foundation: 7–9 h |
| Vận động (TB bước/ngày) | 20 | `(bước − 2000) / 6000 · 100`, giới hạn 0–100 | Paluch et al., Lancet Public Health 2022 (lợi ích gần bão hòa ~8.000 bước) |
| Huyết áp (lần đo gần nhất ≤ 30 ngày) | 20 | Bình thường 100 · hơi cao 80 · độ 1: 60 · độ 2: 35 · khủng hoảng 10 — lấy **mức xấu hơn** của tâm thu/tâm trương | ACC/AHA 2017 |
| BMI | 15 | Thiếu cân nhiều 40 · thiếu cân 70 · bình thường 100 · thừa cân 80 · béo phì I 55 · béo phì II 35 | WHO châu Á–Thái Bình Dương (bình thường 18,5–22,9) |
| Uống thuốc | 15 | `liều đã uống / liều đến hạn · 100` (7 ngày) | ≥ 80% là ngưỡng "tuân thủ" thường dùng |
| Thói quen | 10 | `lượt hoàn thành / lượt có thể · 100` (7 ngày) | — |

**Điểm tổng = trung bình có trọng số chỉ trên các mặt đang có dữ liệu:**
`điểm = Σ(điểm_mặt × trọng_số) / Σ(trọng_số của các mặt có dữ liệu)`

- Cần **ít nhất 3 mặt** mới tính điểm (tránh một con số dựa trên 1 chỉ số).
- Thuốc và thói quen chỉ tính khi người dùng có dùng tính năng đó → không bao giờ bị coi là "thiếu".
- Xếp loại: ≥ 85 Rất tốt · ≥ 70 Tốt · ≥ 50 Cần cải thiện · còn lại Cần chú ý.
- **Gợi ý trên trang chủ** = mặt có điểm thấp nhất mà có lời khuyên.

**Vì sao trung bình có trọng số trên phần có dữ liệu?** Nếu coi mặt thiếu dữ liệu là 0, người chưa đo huyết áp sẽ
bị phạt oan; nếu bỏ hẳn trọng số, điểm sẽ nhảy mạnh khi thêm một chỉ số. Chuẩn hóa theo tổng trọng số đang có giải
quyết cả hai. **Độ phức tạp:** O(1).

**Ví dụ (có trong test):** ngủ 100 (w20), bước 50 (w20), BMI 40 (w15) → (2000 + 1000 + 600) / 55 = **65 → "Cần cải thiện"**.

---

## 2. Lịch sử điểm theo tuần & nhận xét (ScoreHistory)

**Mục đích:** cho thấy xu hướng 8 tuần và nói rõ "điều gì đã thay đổi" so với tuần trước.

**Cách tính:** chấm điểm **các cửa sổ 7 ngày trượt** (kết thúc hôm nay, hôm nay − 7, …) bằng **cùng hàm HealthScore**,
nên mọi cột so sánh được với điểm hôm nay. Dữ liệu thô được đọc **một lần**, sau đó tính cho 8 cửa sổ.

Quy tắc cho mỗi cửa sổ `[cuối − 6, cuối]`:
- **Bước chân:** cửa sổ hiện tại dùng 7 ngày *trọn vẹn* trước hôm nay (hôm nay chưa hết ngày); cửa sổ quá khứ dùng đúng 7 ngày của nó.
- **Huyết áp:** lần đo gần nhất *trước cuối cửa sổ* và trong vòng 30 ngày.
- **BMI:** cân nặng gần nhất *trước cuối cửa sổ*, nếu không có thì lấy cân nặng trong hồ sơ.
- **Thói quen:** chỉ đếm những ngày thói quen đã tồn tại; *hôm nay chỉ tính khi đã hoàn thành* (chưa xong không bị coi là bỏ lỡ).
- **Thuốc:** tỷ lệ tuân thủ trong cửa sổ (xem mục 4).

**Nhận xét (so với 7 ngày trước):**

| Nhận xét | Ngưỡng hiện ra | Chiều "tốt" |
|---|---|---|
| Thay đổi điểm | \|Δ\| ≥ 3 điểm (nhỏ hơn: "ổn định") | tăng |
| Giấc ngủ | \|Δ\| ≥ 0,3 giờ/đêm | so **điểm giấc ngủ**, không so số giờ: ngủ 11 h thay vì 9 h là *xấu đi* |
| Bước chân | \|Δ\| ≥ 500 bước/ngày | tăng |
| Uống thuốc / Thói quen | \|Δ\| ≥ 10 điểm % | tăng |
| Mạnh nhất / Nên ưu tiên | luôn có khi ≥ 2 mặt | — |

Ngưỡng tránh "nhận xét nhiễu" do dao động nhỏ hằng ngày. Biểu đồ bỏ các tuần trước khi có dữ liệu (giữ tối thiểu 4 cột).
**Độ phức tạp:** O(W · N) với W = 8 tuần, N = số bản ghi.

---

## 3. Chỉ số cơ thể & mục tiêu (HealthCalculations)

- **BMI** = cân nặng (kg) / chiều cao² (m). Phân loại theo **chuẩn châu Á** (bình thường 18,5–22,9; thừa cân từ 23; béo phì từ 25),
  phân loại trên giá trị **làm tròn 1 chữ số** để nhãn luôn khớp với số hiển thị (22,96 hiển thị 23,0 → "thừa cân").
- **BMR — Mifflin–St Jeor:** `10·kg + 6,25·cm − 5·tuổi + s`, với s = +5 (nam), −161 (nữ), −78 (khác: trung bình hai giá trị).
- **TDEE = BMR × hệ số vận động:** 1,2 · 1,375 · 1,55 · 1,725 · 1,9.
- **Macro:** đạm 1,6 g/kg (tối đa 30% năng lượng) · béo 30% năng lượng · tinh bột = phần còn lại (4/9/4 kcal/g).
- **Nước gợi ý:** 35 ml/kg, làm tròn 50 ml, giới hạn 1.200–4.000 ml.

**Vì sao Mifflin–St Jeor?** Là công thức được khuyến nghị cho người trưởng thành khỏe mạnh, chính xác hơn Harris–Benedict ở dân số hiện đại.

---

## 4. Lịch thuốc, tuân thủ, liều tiếp theo, số viên (MedicationSchedule)

- **Có lịch hôm đó không:** thuốc đang dùng, trong khoảng ngày bắt đầu–kết thúc, và đúng thứ trong tuần.
- **Liều kế tiếp của một giờ uống:** thử từ hôm nay, tối đa 8 ngày tới, lấy thời điểm đầu tiên *sau bây giờ* mà có lịch (8 ngày luôn đủ cho lịch theo tuần).
- **Tỷ lệ tuân thủ:** `đã uống / liều đến hạn`, trong đó **loại bỏ liều chưa đến giờ** (uống liều sáng không làm liều tối bị tính là bỏ lỡ)
  và **liều trước khi thêm thuốc** (trừ khi người dùng có ghi).
- **Liều tiếp theo trên trang chủ:** liều *sớm nhất* hôm nay chưa đánh dấu → liều sáng bị quên được ưu tiên hơn liều tối sắp tới.
- **Số ngày còn thuốc:** `số viên / (số lần uống/ngày × viên mỗi liều × số ngày dùng trong tuần / 7)`, làm tròn xuống.
  "Sắp hết" khi **≤ 5 ngày**; thông báo chỉ gửi **một lần khi vừa vượt ngưỡng** (không làm phiền mỗi liều).
- **Cập nhật số viên:** đánh dấu "đã uống" trừ `viên mỗi liều` (không âm); **bỏ đánh dấu cộng lại** → thao tác đối xứng, hoàn tác an toàn.

---

## 5. Lịch nhắc nhở (ReminderSchedule)

Tìm thời điểm kích hoạt tiếp theo từ giờ `HH:mm` và danh sách thứ: thử hôm nay rồi các ngày kế tiếp, lấy ngày đầu tiên đúng thứ
và *sau thời điểm hiện tại*. Sau mỗi lần thông báo, lịch tiếp theo được tính từ `bây giờ + 1 phút` để không bao giờ kích hoạt lặp cùng một mốc.

---

## 6. Nhắc uống nước thông minh (WaterPace)

**Ý tưởng:** chia đều mục tiêu nước trong ngày thức (07:00–22:00).
`lượng kỳ vọng(t) = mục tiêu × (t − 07:00) / (22:00 − 07:00)` (0 trước 07:00, đủ mục tiêu từ 22:00).

- Đã uống **≥ kỳ vọng** → **bỏ qua** nhắc nhở (người dùng đang đúng nhịp, không làm phiền).
- **Thiếu** → nhắc kèm con số cụ thể: *"Bạn đã uống 750/2000 ml — chậm khoảng 500 ml so với nhịp trong ngày."*
- Chưa đặt mục tiêu → luôn nhắc (không có gì để so).

**Vì sao tuyến tính?** Đơn giản, dễ giải thích, và đủ để tránh nhắc thừa; có thể thay bằng phân bố theo bữa ăn sau này.

---

## 7. Quét món ăn bằng AI (FoodScan + backend)

1. **App:** thu nhỏ ảnh ≤ 1024 px, xoay theo EXIF, **xóa EXIF/GPS**, nén JPEG → gửi backend (khóa AI chỉ ở server).
2. **Model** trả JSON theo **schema bắt buộc**: tên món, gram ước tính, `table_key`, calo/macro, độ tin cậy.
3. **Server kiểm tra & giới hạn** giá trị vật lý: gram 1–3.000, calo 0–5.000, đạm/béo ≤ 500 g, tinh bột ≤ 1.000 g, độ tin cậy 0–1; bỏ món thiếu tên.
4. **Bảng dinh dưỡng:** nếu `table_key` khớp bảng → `calo = calo_100g × gram / 100` (tương tự macro), ghi nguồn `table`; không khớp → giữ số của AI, nguồn `ai`.
5. **Model dự phòng:** lỗi 429/500/503 → thử lại một lần với model nhẹ hơn (hạn mức miễn phí riêng).
6. **App cho duyệt trước khi lưu:**
   - **Khẩu phần theo tỷ lệ:** đổi gram giữ nguyên *calo trên mỗi gram* → `calo_mới = calo_AI × gram_mới / gram_AI`.
     Nút ½ · ¾ · AI · 1¼ · 1½ luôn tính **so với ước tính gốc của AI** (không cộng dồn).
   - **Kiểm tra calo ↔ macro (Atwater 4/4/9):** cảnh báo khi lệch **> 30% và > 40 kcal** — dấu hiệu model ước sai một trong hai.

**Đánh giá:** 15 ảnh Nutrition5k (món được *cân trong phòng thí nghiệm*), bộ holdout riêng; đo **sai số tuyệt đối % trung vị**
(~20%) và **độ lệch trung bình** (gram thấp hơn thực tế ~12%) → căn cứ cho nút "Nhiều hơn". Chi tiết ở `PROJECT_SUMMARY.md` mục 5.

---

## 8. Nhập dữ liệu Health Connect (HealthConnectMapper)

- **Không trùng khi đồng bộ lại:** mỗi bản ghi nhập có **id tất định** tiền tố `hc_`, sinh từ mã (uid) của bản ghi gốc, hoặc từ ngày/giờ với số liệu tổng hợp (`hc_day_2026-11-20`, `hc_hr_…`) → đồng bộ lại *thay thế* thay vì nhân đôi.
- **Bước chân:** dùng tổng theo ngày của Health Connect; ngày nào tổng rỗng thì cộng dữ liệu thô làm dự phòng.
- **Nhịp tim:** đồng hồ ghi mẫu mỗi vài giây → giữ **một giá trị trung bình mỗi giờ**.
- **Giấc ngủ:** gán theo **ngày thức dậy** (khớp nhập tay); phiên > 24 giờ bị bỏ.
- **Cửa sổ đồng bộ:** lần đầu lấy 30 ngày; các lần sau đọc lại **2 ngày chồng lấn** để bắt dữ liệu đến muộn; tự đồng bộ tối đa 15 phút/lần khi mở ứng dụng; nếu được quyền đọc trong nền thì `HealthConnectSyncWorker` (WorkManager, định kỳ 6 giờ, điều kiện pin không yếu) gọi cùng hàm đó khi ứng dụng đóng. Mỗi lần làm mới quyền, worker được đặt lịch hoặc hủy theo `HcState.backgroundSync`.

---

## 9. Báo thức thuốc chính xác (MedicationScheduler)

- Có quyền "Báo thức và lời nhắc" → `AlarmManager.setExactAndAllowWhileIdle` (đúng phút, kể cả khi Doze); không có → WorkManager (có thể trễ).
- Báo thức bị xóa khi **khởi động lại / cập nhật app / đổi giờ** → `MedicationBootReceiver` đặt lại tất cả thuốc đang dùng.
- Mỗi lần kêu: hiện thông báo có nút "Đã uống / Bỏ qua", rồi đặt mốc tiếp theo (từ `bây giờ + 1 phút`).
- Đã kiểm chứng trên emulator: báo thức kêu đúng 00:31:00, khởi động lại vẫn được đặt lại, xóa thuốc thì báo thức bị hủy.

---

## 10. Sao lưu & khôi phục

- **Xuất:** toàn bộ dữ liệu của tài khoản ra JSON (`format: healthapp-export-v1`), kèm các món ăn được bữa ăn tham chiếu.
- **Khôi phục kiểu "gộp" (merge):** mỗi bản ghi giữ **id gốc** và được **thay thế theo id** → khôi phục cùng tệp hai lần **không nhân đôi** (idempotent).
- **Một giao dịch (transaction):** lỗi giữa chừng → không ghi gì, tránh dữ liệu nửa vời.
- **Chuyển sang tài khoản trên máy này** (id tài khoản trong tệp khác máy mới), sau đó **đặt lại nhắc nhở & báo thức thuốc**.
- Tệp không đúng định dạng bị từ chối với thông báo rõ ràng; tệp > 20 MB bị từ chối.
- Đã kiểm chứng trên emulator: xóa một bản ghi → khôi phục → bản ghi trở lại; khôi phục lần hai vẫn chỉ 1 bản ghi.

---

## 11. Tìm món không phân biệt dấu (FoodSearch)

- **Vấn đề:** SQL `LIKE` chỉ bỏ qua hoa/thường với chữ ASCII và phân biệt dấu → gõ `pho bo` hoặc `PHỞ BÒ` không ra "Phở bò"; người dùng (và bàn phím emulator) hay gõ không dấu.
- **Chuẩn hóa (fold):** chữ thường → tách dấu bằng Unicode NFD → bỏ mọi dấu (ký tự loại *Mark*) → `đ` → `d` → gộp khoảng trắng. Ví dụ "Đậu phụ chiên" → `dau phu chien`.
- **Khớp:** mọi từ của câu tìm đều nằm trong tên đã chuẩn hóa (không cần đúng thứ tự: `bo pho` vẫn ra).
- **Xếp hạng:** 0 = tên bắt đầu bằng câu tìm, 1 = một từ trong tên bắt đầu bằng nó, 2 = chỉ chứa; cùng hạng thì tên ngắn trước ("Phở bò" trước "Gia vị phở bò").
- **Nguồn, theo thứ tự:** 42 món Việt có sẵn (offline, mỗi món một khẩu phần thường gặp, giá trị /100 g lấy từ **cùng tệp** `vn-foods.json` mà AI quét ảnh dùng — test bắt hai bản phải giống hệt) → món đã lưu trên máy → Open Food Facts (chỉ khi bấm kính lúp, để không gọi mạng mỗi lần gõ). Gõ chữ thì lọc offline sau 250 ms.
- **Độ phức tạp:** O(n·m) với n món (vài trăm) và m độ dài tên — tức thì trên điện thoại.

---

## Câu hỏi thầy có thể hỏi — trả lời ngắn

- **Sao điểm không tính khi thiếu dữ liệu?** Ít hơn 3 mặt thì một con số không đại diện; app nói rõ cần thêm mặt nào.
- **Sao không dùng machine learning cho điểm sức khỏe?** Không có dữ liệu nhãn; quy tắc dựa trên khuyến nghị y khoa *giải thích được* và kiểm thử được.
- **Sao AI không tự tính calo?** AI tốt ở nhận diện món, kém ổn định ở số liệu; bảng dinh dưỡng cho số *truy được nguồn*. Kết quả đo: sai số tương đương, sai số chủ yếu do ước lượng gram.
- **Làm sao biết thuật toán đúng?** Hàm thuần trong `domain/usecase` có JVM test với ví dụ tính tay (ví dụ mục 1); chạy `./gradlew :app:testDebugUnitTest`.
- **Độ phức tạp?** Hầu hết O(1) hoặc O(N) theo số bản ghi; lịch sử điểm O(8·N), dữ liệu đọc một lần.
