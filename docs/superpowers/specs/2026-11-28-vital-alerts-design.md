# Cảnh báo sức khỏe từ đồng hồ + đồng hồ mô phỏng — thiết kế

Ngày: 2026-11-28 · Trạng thái: chờ duyệt

## Mục đích
Giúp người dùng **phát hiện sớm dấu hiệu nguy cơ** từ dữ liệu đồng hồ (nhịp tim, bước chân, huyết áp) và **nhận thông báo**
để kịp giảm vận động, nghỉ ngơi, đi khám hoặc gọi cấp cứu. Điểm cốt lõi: **phân biệt chỉ số cao do đang vận động
(bình thường) với chỉ số cao khi nghỉ (đáng lo)**. Trong buổi bảo vệ, phải trình diễn được: "đồng hồ" gửi dữ liệu theo thời gian
thực → app nhận ra tình huống → bắn thông báo.

## Mục tiêu
- Bộ quy tắc cảnh báo rõ ràng, có nguồn (AHA), là hàm thuần trong `domain/usecase` + unit test, ghi vào `ALGORITHMS.md`.
- Thông báo Android mức cao, chạm vào mở màn hình chi tiết (vì sao cảnh báo, nên làm gì, nút gọi 115).
- "Đồng hồ mô phỏng" trong app (bản demo/debug) để demo thời gian thực, không cần thiết bị thật.
- Cùng bộ quy tắc chạy trên dữ liệu thật: lúc đồng bộ Health Connect và lúc người dùng tự nhập nhịp tim/huyết áp.

## Không làm
- Không chẩn đoán đột quỵ hay bệnh. Chỉ cảnh báo **yếu tố nguy cơ / chỉ số bất thường**; luôn ghi "không thay thế chẩn đoán y khoa".
- Không làm app Wear OS, không ghép đồng hồ thật/ảo, không tải thêm gì.
- Không tự động gọi cấp cứu (chỉ mở trình quay số với 115).
- Không đổi cơ sở dữ liệu (dữ liệu mô phỏng không lưu vào nhật ký).

## Điều sẽ là thất bại dù "chạy được"
- Báo động khi người dùng đang chạy bộ với nhịp tim bình thường cho vận động (báo động giả làm người dùng tắt thông báo).
- Spam: cùng một cảnh báo lặp lại liên tục.
- Lời lẽ gây hoảng hoặc khẳng định "bạn bị đột quỵ".

## Các cách đã cân nhắc
1. **Đồng hồ Wear OS ảo + app đồng hồ** — giống thật nhất, nhưng phải tải ~1,5–2 GB, ghép 2 emulator dễ lỗi. Loại.
2. **App đồng hồ giả lập ghi vào Health Connect** — đúng đường dữ liệu thật, nhưng thêm một app/module. Không cần vì người dùng
   chỉ cần demo được thông báo.
3. **Đồng hồ mô phỏng trong app** — **đã chọn** (người dùng: "không cần đồng hồ thật, chỉ cần demo để biết là app có thông báo").
   Ít rủi ro nhất; bộ quy tắc vẫn là thật và cũng chạy trên dữ liệu Health Connect/nhập tay.

## Bộ quy tắc (`VitalAlerts`, hàm thuần)
Đầu vào: các mẫu nhịp tim (thời điểm, bpm), bước chân theo phút, các lần đo huyết áp, tuổi (từ hồ sơ; không có → coi 40 tuổi).

**Ngữ cảnh vận động:** một thời điểm là *đang vận động* nếu tổng bước trong 10 phút trước đó ≥ 500 (≈ đi nhanh trở lên).
*Vừa tập xong* = đang nghỉ nhưng 10 phút trước nữa có vận động (cho phép tim còn cao một lúc — không báo).

**Nhịp tim tối đa ước tính:** 220 − tuổi.

| Cảnh báo | Điều kiện | Mức |
|---|---|---|
| Tim nhanh khi nghỉ | đang nghỉ (không phải vừa tập xong), nhịp tim ≥ 120 kéo dài ≥ 10 phút | Cảnh báo |
| Tim rất nhanh khi nghỉ | đang nghỉ, ≥ 150 kéo dài ≥ 5 phút | Khẩn cấp |
| Tim chậm khi nghỉ | đang nghỉ, < 40 kéo dài ≥ 10 phút | Cảnh báo |
| Tập quá sức | đang vận động, > 90% nhịp tối đa kéo dài ≥ 5 phút → "nên giảm cường độ" | Cảnh báo |
| Vượt nhịp tối đa | đang vận động, > 100% nhịp tối đa ≥ 2 phút → "dừng tập, nghỉ ngơi" | Khẩn cấp |
| Huyết áp rất cao | một lần đo ≥ 180 hoặc ≥ 120 (cơn tăng huyết áp, AHA) → kiểm tra F.A.S.T, gọi 115 nếu có triệu chứng | Khẩn cấp |
| Huyết áp cao | một lần đo 160–179 hoặc 100–119 → nghỉ 5 phút rồi đo lại | Cảnh báo |
| Huyết áp cao kéo dài | ≥ 5 lần đo trong 7 ngày, trung bình ≥ 135/85 (ngưỡng đo tại nhà) → nên đi khám (yếu tố nguy cơ đột quỵ) | Xu hướng |

"Kéo dài" = mọi mẫu trong khoảng thời gian đó đều thỏa điều kiện và các mẫu cách nhau ≤ 2 phút (không có lỗ hổng dữ liệu).
**Chống lặp:** cùng loại cảnh báo không gửi lại trong 30 phút (xu hướng: 1 lần/ngày).

## Thông báo & màn hình
- Kênh thông báo mới "Cảnh báo sức khỏe" (mức cao). Nội dung: loại cảnh báo + con số + 1 câu nên làm.
- Chạm → màn hình **Cảnh báo**: giải thích ngắn (vì sao, đang nghỉ hay vận động), việc nên làm, với cảnh báo khẩn cấp có
  danh sách **F.A.S.T** (Mặt méo – Tay yếu – Nói ngọng – Gọi ngay 115) và nút **Gọi 115** (mở trình quay số).
- Thẻ "Cảnh báo gần đây" trên tab Sức khỏe (lưu trong bộ nhớ ứng dụng, không cần đổi DB).

## Đồng hồ mô phỏng (bản demo/debug, `DEMO_TOOLS`)
- Mở từ tab Sức khỏe. Mặt đồng hồ tròn hiển thị nhịp tim, bước/phút, trạng thái (Nghỉ/Vận động) cập nhật mỗi giây.
- **Tua nhanh thời gian:** 1 giây thật = 1 phút dữ liệu, để tình huống "kéo dài 10 phút" hiện ra sau ~10 giây.
- Kịch bản: *Nghỉ ngơi* (bình thường) · *Chạy bộ* (tim 150 nhưng đang vận động → **không** báo) · *Chạy quá sức* ·
  *Tim nhanh khi nghỉ* · *Huyết áp rất cao*. Mỗi tick đưa dữ liệu qua đúng `VitalAlerts` như dữ liệu thật.
- Gắn nhãn rõ "Dữ liệu mô phỏng"; không lưu vào nhật ký.

## Dữ liệu thật
- Đồng bộ Health Connect: chạy `VitalAlerts` trên mẫu nhịp tim **gốc** (trước khi gộp theo giờ) và bước chân theo thời gian
  vừa đọc, chỉ xét dữ liệu 2 giờ gần nhất để không báo chuyện cũ.
- Nhập tay huyết áp/nhịp tim: kiểm tra ngay khi lưu (một lần đo → các quy tắc huyết áp và "tim rất nhanh" không cần kéo dài
  thì chỉ hiện gợi ý trong app, không thông báo).

## Phần để người xây dựng tự quyết
Bố cục chi tiết màn hình, màu (theo HealthPalette: Coral cho tim, Pink cho huyết áp), tên chuỗi, cấu trúc lớp, cách lưu
danh sách cảnh báo gần đây (DataStore/SharedPreferences).
