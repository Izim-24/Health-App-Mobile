# Kịch bản demo (6 phút)

## Chuẩn bị (trước giờ thi, KHÔNG làm trong giờ thi)
1. Mở emulator `Small_Phone`, chạy `./gradlew :app:installDebug`.
2. `cd server && npm start` — kiểm tra `http://127.0.0.1:8787/health` trả `"configured": true`.
   - Không có mạng/hết lượt miễn phí → `npm run mock` (kết quả có nhãn **GIẢ LẬP**).
3. Trong app: **Hồ sơ → Cài đặt & dữ liệu → Nạp dữ liệu mẫu (demo)**. Màn hình Thuốc → bấm **Cho phép** "Báo thức và lời nhắc" (Android 12+).
4. Đặt sẵn 1 ảnh món ăn trong thư viện ảnh của emulator.
5. Quay sẵn **video dự phòng** cảnh quét ảnh thật (phòng khi AI lỗi).
6. Không chạy thử AI nhiều lần trong ngày thi (gói miễn phí giới hạn lượt/ngày).

## Trình bày

| Phút | Màn hình | Nói gì |
|---|---|---|
| 0:00 | **Trang chủ** | Điểm sức khỏe 0–100, chạm "Xem chi tiết": từng phần dựa trên khuyến nghị (ngủ 7–9h, ~8.000 bước, huyết áp ACC/AHA, BMI châu Á); **biểu đồ 8 tuần + nhận xét so với tuần trước**. Không phải chẩn đoán. |
| 1:00 | **Sức khỏe** | Chỉ số có nhãn nguồn (Nhập tay / Health Connect / Dữ liệu mẫu), biểu đồ tuần. Nhắc Health Connect: chỉ đọc, đồng bộ không trùng. |
| 2:00 | **Ăn uống → Quét món ăn** | Chọn ảnh → AI trả món + gram + kcal + độ tin cậy → **sửa gram, kcal tự tính lại** → Lưu. Nói về đánh giá: sai số trung vị 16,5% trên ảnh cân thật. |
| 3:30 | **Thuốc** (nút nhanh trên trang chủ) | Thêm thuốc giờ = 1–2 phút sau → thông báo hiện **đúng phút** (báo thức chính xác), bấm **"Đã uống"** ngay trên thông báo → tỷ lệ tuân thủ cập nhật. |
| 4:30 | **Thói quen / Tinh thần** | Đánh dấu thói quen; check-in cảm xúc; bài thở 4–4 (vòng tròn co giãn). |
| 5:15 | **Cài đặt** | Đổi giao diện Sáng/Tối; xuất JSON, xóa dữ liệu mẫu (giữ dữ liệu thật). |
| 5:45 | Kết | Kiến trúc Clean Architecture, 81 test + CI, khóa API chỉ ở server. |

## Câu hỏi thường gặp — trả lời ngắn
- **Sao không có đăng nhập?** App một người dùng, offline, dữ liệu sức khỏe ở lại trên máy. Đăng nhập cần backend có xác thực và đồng bộ — nằm trong hướng phát triển.
- **Sao không để khóa API trong app?** Mọi thứ trong APK đều trích xuất được; khóa nằm ở server, app chỉ gọi server.
- **Nhắc thuốc có đúng giờ không?** Có quyền báo thức chính xác → `AlarmManager.setExactAndAllowWhileIdle`, đúng phút cả khi Doze; báo thức mất khi khởi động lại nên có receiver đặt lại (đã thử bằng cách reboot emulator). Không có quyền → WorkManager (có thể trễ). Nhắc nước/thói quen dùng WorkManager vì không cần đúng từng phút.
- **Calo lấy từ đâu?** AI chỉ nhận diện món và ước tính gram; calo/macro lấy từ bảng thành phần dinh dưỡng (giá trị/100 g), món không có trong bảng mới dùng số của AI — mỗi món ghi rõ nguồn.
- **AI sai thì sao?** Luôn cho sửa trước khi lưu; cảnh báo độ tin cậy thấp và calo lệch với thành phần.
- **Kiểm chứng kiến trúc thế nào?** `presentation` không import `data`; ViewModel test bằng fake repository không cần Android.
