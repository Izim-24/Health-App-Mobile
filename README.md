# Health App

[![CI](https://github.com/Izim-24/Health-App-Mobile/actions/workflows/ci.yml/badge.svg)](https://github.com/Izim-24/Health-App-Mobile/actions/workflows/ci.yml)

Ứng dụng Android (Kotlin, Jetpack Compose, Room, Hilt, Clean Architecture + MVVM) theo dõi sức khỏe, thói quen và dinh dưỡng, có ước tính dinh dưỡng từ ảnh món ăn bằng AI. Dữ liệu lưu cục bộ trên thiết bị.

## Đã hoạt động

- **Tiếng Việt và English**: toàn bộ giao diện nằm trong `strings.xml` (608 chuỗi mỗi ngôn ngữ); chọn ngôn ngữ trong **Cài đặt → Ngôn ngữ** (Theo máy / Tiếng Việt / English), Android 13+ cũng hiện trong Cài đặt hệ thống → Ứng dụng → Ngôn ngữ. Trợ lý AI và tên món từ ảnh trả lời theo ngôn ngữ đang chọn.
- **Hình minh họa**: 13 hình vẽ vector tự thiết kế (onboarding, trạng thái trống, quét món, trợ lý AI, Health Connect), tự đổi màu theo giao diện sáng/tối; sinh từ `tools/illustrations/gen.py`.
- **Trải nghiệm**: trang chủ có thẻ **liều thuốc tiếp theo** (bấm "Đã uống" ngay), tiến độ nước so với mục tiêu, khung tải thay cho vòng xoay, điểm số đếm lên, rung nhẹ khi hoàn thành thói quen, chạm biểu đồ để xem từng ngày; **hỏi xác nhận trước khi xóa** thói quen, bữa ăn, số đo, lượt uống nước, nhắc nhở.
- **Nhắc uống nước thông minh**: chia đều mục tiêu trong ngày (07:00–22:00); đang đúng nhịp thì bỏ qua nhắc, chậm thì báo thiếu bao nhiêu ml.
- **Sao lưu & khôi phục**: xuất toàn bộ dữ liệu ra JSON và **khôi phục từ tệp** (Cài đặt → Khôi phục từ bản xuất) — gộp theo mã nên khôi phục hai lần không bị trùng; dùng khi đổi điện thoại.
- **Theo dõi số viên thuốc**: nhập số viên đang có, app đếm lùi khi bấm "Đã uống" và báo khi còn ≤ 5 ngày.
- **Quét món ăn – chỉnh khẩu phần nhanh**: ½ · ¾ · AI · 1¼ · 1½ cho từng món và "ít hơn / như ảnh / nhiều hơn" cho cả bữa (AI thường ước lượng thấp ~10–15% gram).
- **Trợ năng**: nhãn TalkBack cho nút biểu tượng (kèm tên mục, ví dụ "Xóa Vitamin D3"), vai trò radio/checkbox cho lựa chọn, thông báo lỗi tự đọc (live region), vùng chạm ≥ 48 dp, kiểm tra với cỡ chữ 130%.
- Lần đầu mở: **onboarding** 4 bước (tên, ngày sinh, giới tính → chiều cao, cân nặng, mức vận động → gợi ý calo/macro, nước, BMI), có thể bỏ qua. Giao diện **sáng / tối / theo máy** trong Cài đặt.
- Dashboard: chỉ số đã nhập, thói quen, dinh dưỡng và trạng thái trống khi chưa có dữ liệu. **Điểm sức khỏe** (0–100) chấm từng mặt theo khuyến nghị công khai — giấc ngủ 7–9 giờ (7 đêm gần nhất), ~8.000 bước/ngày (ngày trọn vẹn gần nhất), huyết áp theo ACC/AHA 2017 (đo trong 30 ngày), BMI theo chuẩn châu Á–Thái Bình Dương của WHO (bình thường 18,5–22,9; thừa cân từ 23, béo phì từ 25 — cũng dùng cho nhãn BMI trong Hồ sơ), tỷ lệ uống thuốc và hoàn thành thói quen 7 ngày — rồi lấy trung bình có trọng số; cần ít nhất 3 mặt có dữ liệu. Chạm vào để xem chi tiết từng phần, gợi ý, **biểu đồ điểm 8 tuần** và **nhận xét so với tuần trước** (ngủ, bước chân, uống thuốc, thói quen) — mỗi tuần tính cùng quy tắc với điểm hôm nay. Không phải chẩn đoán y khoa.
- Theo dõi sức khỏe: nhập nhịp tim, huyết áp, đường huyết, cân nặng, giấc ngủ và hoạt động; xem lịch sử, biểu đồ theo ngày/tuần/tháng.
- Thói quen: tạo và chỉnh mục tiêu, đánh dấu hoàn thành theo ngày. Sang ngày mới, trạng thái hoàn thành của ngày trước không còn áp dụng.
- Nước uống: ghi và xóa lượt uống, tự đặt mục tiêu ml/ngày.
- Dinh dưỡng: ghi bữa ăn và khẩu phần. **Tìm món không phân biệt dấu** (`pho bo` = "Phở bò"), kết quả hiện ngay khi gõ, kể cả offline: có sẵn **42 món Việt** theo một khẩu phần thường gặp (cùng bảng giá trị với AI quét ảnh, là ước tính theo công thức phổ biến). Nhấn kính lúp để tra thêm sản phẩm đóng gói qua Open Food Facts (dữ liệu cộng đồng, cần đối chiếu nhãn).
- Sức khỏe tinh thần: check-in cảm xúc, mức căng thẳng, ghi chú riêng tư và bài tập thở ngắn.
- Hồ sơ: nhập tên, ngày sinh, giới tính, chiều cao, cân nặng và mức vận động. Nút **Tính mục tiêu calo & macro** ước tính BMR/TDEE theo công thức Mifflin–St Jeor và chia macro (đạm 1,6 g/kg, chất béo 30%), có gợi ý lượng nước. BMI chỉ tính khi có dữ liệu do người dùng nhập.
- Nhắc nhở: tạo nhắc uống nước, vận động, bữa ăn, giấc ngủ, thói quen theo giờ và ngày trong tuần; bật/tắt, sửa, xóa. Lên lịch bằng WorkManager (giữ sau khi khởi động lại máy), cần quyền thông báo trên Android 13+.
- Lịch uống thuốc: thêm thuốc với liều, giờ uống (nhiều lần/ngày), ngày trong tuần và số ngày dùng; thông báo đúng giờ có nút **Đã uống** / **Bỏ qua** ngay trên thông báo; đánh dấu từng liều trong ngày và xem tỷ lệ tuân thủ 7 ngày. Có thể tạm ngừng hoặc xóa thuốc. Khi được phép **"Báo thức và lời nhắc"** (màn hình Thuốc có nút bật), nhắc thuốc dùng báo thức chính xác — đúng phút kể cả khi máy tiết kiệm pin — và tự đặt lại sau khi khởi động lại máy, cập nhật app hoặc đổi giờ; nếu không, dùng WorkManager (có thể trễ vài phút).
- Health Connect (chỉ đọc): kết nối trong **Cài đặt → Health Connect** hoặc tab **Sức khỏe**; **tự nhập 13 loại dữ liệu** từ đồng hồ và app sức khỏe khác (Samsung Health, Fitbit, Garmin, Xiaomi, Huawei, Omron, Withings…): số bước + năng lượng vận động (theo ngày), giấc ngủ, nhịp tim (trung bình mỗi giờ), cân nặng, huyết áp, đường huyết, **buổi tập, SpO2, nhịp tim lúc nghỉ, thân nhiệt, nhịp thở, nước uống**; mỗi số đo ghi app nguồn (vd. "Health Connect · Samsung Health"). Chỉ đọc **phần dữ liệu thay đổi** (changes token), bản ghi bị xoá ở app gốc cũng bị xoá theo. Tự đồng bộ khi mở ứng dụng (tối đa 15 phút/lần) và **đồng bộ nền khoảng 15 phút/lần khi ứng dụng đóng** nếu bạn cho phép "truy cập dữ liệu trong nền" (Health Connect trên Android 14+ có hỗ trợ; WorkManager, chỉ chạy khi pin không yếu, tự hủy khi ngắt kết nối hoặc thu hồi quyền); lần đầu lấy 30 ngày. Đồng bộ lại không tạo bản trùng; giá trị nhập ghi nguồn "Health Connect". Ngắt kết nối thu hồi quyền và có thể xóa dữ liệu đã nhập (giữ dữ liệu tự nhập). Không ghi ngược vào Health Connect, không gửi lên máy chủ.
- Quét món ăn bằng ảnh (Dinh dưỡng → Quét món ăn): chụp ảnh hoặc chọn từ thư viện; ảnh được thu nhỏ ≤1024 px, xoay đúng chiều, xóa EXIF/GPS rồi gửi qua backend tới model thị giác. AI nhận diện từng món và **ước tính số gram**; calo và đạm/tinh bột/béo được **tính từ bảng thành phần dinh dưỡng** (giá trị/100 g × gram) khi món có trong bảng, nếu không thì giữ ước tính của AI — mỗi món ghi rõ "Calo theo bảng dinh dưỡng" hay "Calo do AI ước tính". Bạn sửa số gram (dinh dưỡng tự tính lại), bỏ món sai, chọn bữa rồi lưu. Ứng dụng cảnh báo món có độ tin cậy thấp hoặc calo không khớp với thành phần. Đây là **ước tính** — khẩu phần, dầu mỡ và nước sốt khó nhìn từ ảnh.
- Cài đặt & dữ liệu: xuất toàn bộ dữ liệu ra tệp JSON và chia sẻ qua bảng chia sẻ Android; xóa toàn bộ dữ liệu trên thiết bị (có xác nhận).
- **Cảnh báo sức khỏe từ đồng hồ**: phân tích nhịp tim, bước chân và huyết áp (Health Connect hoặc tự nhập) để **phân biệt chỉ số cao do đang vận động với cao khi đang nghỉ**. Gửi thông báo khi tim nhanh/chậm bất thường lúc nghỉ, tập quá 90% nhịp tối đa (220 − tuổi), huyết áp ≥ 180/120 (khẩn cấp, hướng dẫn F.A.S.T và nút **Gọi 115**), huyết áp trung bình 7 ngày ≥ 135/85 (nên đi khám). Vừa tập xong tim còn cao thì không báo; cùng cảnh báo không lặp trong 30 phút. Không phải chẩn đoán. Bản demo/debug có **Đồng hồ mô phỏng** (tab Sức khỏe): phát dữ liệu theo thời gian thực, 1 giây = 1 phút, 5 tình huống.
- Báo cáo: tổng hợp ngày, tuần và tháng từ những ngày đã có nhật ký; **xuất báo cáo PDF** (khổ A4, đúng ngôn ngữ đang chọn, có lưu ý không phải chẩn đoán) để in hoặc gửi bác sĩ qua bảng chia sẻ Android.
- Trợ lý AI dạng chat trên Android Emulator khi chạy backend cục bộ và cấu hình khóa mới.

## Chưa hoàn thiện

- Độ chính xác nhận diện ảnh mới được đánh giá trên 15 ảnh món Âu/Mỹ (Nutrition5k): sai số calo trung vị 16,5%. Chưa đánh giá trên món Việt. Tính calo theo bảng dinh dưỡng cho sai số tương đương AI tự đoán (trung vị ~20% trên bộ holdout); sai số chủ yếu do ước lượng gram. Giá trị món Việt trong bảng là xấp xỉ.
- Không có đăng nhập (có chủ đích: ứng dụng một người dùng, offline). Đồng bộ nhiều thiết bị cần backend có xác thực.
- Dữ liệu người dùng và dữ liệu mẫu giữ nguyên ngôn ngữ lúc nhập (ví dụ tên món, ghi chú thuốc).
- Sức khỏe gia đình (nhiều hồ sơ) và trang quản trị không làm, vì ứng dụng một người dùng, offline và không có đăng nhập (xem trên).
- Đồng bộ nền Health Connect cần thiết bị hỗ trợ quyền đọc trong nền; máy cũ hơn vẫn đồng bộ khi mở ứng dụng.
- Dữ liệu cục bộ chưa mã hóa ở cấp ứng dụng. Sao lưu và chuyển dữ liệu ứng dụng đang tắt để tránh tự động sao chép thông tin sức khỏe.

## Build

Yêu cầu JDK 17 và Android SDK 35. Từ thư mục dự án:

```powershell
.\gradlew.bat :app:assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

Kiểm thử (169 test):

```powershell
.\gradlew.bat :app:testDebugUnitTest          # 138 test JVM: domain, mapping, ViewModel, chuỗi vi/en khớp nhau
.\gradlew.bat :app:lintDebug                  # Android Lint: 0 lỗi
.\gradlew.bat :app:connectedDebugAndroidTest  # 13 test trên emulator: migration Room (đến v6), hồi quy, tìm món offline, Compose UI
cd server; npm test                          # 18 test backend
```

Dữ liệu mẫu cho demo: **Hồ sơ → Cài đặt & dữ liệu → Nạp dữ liệu mẫu (demo)** (chỉ bản debug; dữ liệu gắn nhãn "Dữ liệu mẫu" và xóa riêng được).

Không đặt khóa API trong `gradle.properties`, `local.properties`, mã nguồn hoặc APK. Backend phải giữ khóa và kiểm soát quyền truy cập trước khi bật chức năng AI.

## Trợ lý AI trên Android Emulator

Backend phát triển cục bộ nằm ở `server/`. Nó chỉ lắng nghe `127.0.0.1` và gọi OpenAI Responses API; ứng dụng debug kết nối qua địa chỉ emulator `10.0.2.2:8787`. Không công khai cổng này lên Internet. Trợ lý chỉ gửi nội dung bạn gõ, không tự lấy dữ liệu sức khỏe trong Room.

1. Sao chép `server/.env.example` thành `server/.env` (tệp này được bỏ qua bởi `server/.gitignore`) và điền **một** khóa:
   - **Miễn phí:** `GEMINI_API_KEY` từ Google AI Studio, tùy chọn `GEMINI_MODEL` (mặc định `gemini-3.5-flash`). Gói miễn phí giới hạn vài yêu cầu mỗi phút và Google có thể dùng dữ liệu gói miễn phí để cải thiện dịch vụ — không gửi ảnh nhạy cảm.
   - **Trả phí:** `OPENAI_API_KEY` (tùy chọn `OPENAI_MODEL`, mặc định `gpt-4.1-mini`). Nếu có cả hai, Gemini được dùng.
2. Mở PowerShell trong `server/`, chạy `npm start`.
   - Chưa có khóa? `npm run mock` chạy chế độ **giả lập**: chat và quét ảnh trả dữ liệu mẫu, ứng dụng hiện rõ nhãn “DỮ LIỆU GIẢ LẬP”. Chỉ để thử giao diện, không phản ánh ảnh thật.
3. Kiểm tra `http://127.0.0.1:8787/health`, rồi chạy APK debug trên Android Emulator và mở **Hồ sơ → Hỏi trợ lý AI**.

`node --env-file=.env eval-food.mjs` đo độ chính xác calo trên ảnh có số liệu cân thật (xem `server/eval/prepare-nutrition5k.mjs`). `npm test` kiểm tra endpoint và cấu trúc yêu cầu bằng phản hồi giả lập, không dùng khóa thật. Cần backend HTTPS, xác thực người dùng và giới hạn truy cập trước khi triển khai cho điện thoại thật hoặc phát hành công khai. Khóa API không xác nhận được thời hạn sử dụng chỉ từ chuỗi ký tự.

## Kiểm thử Health Connect (bản debug)

Bản debug có `HcSampleDataReceiver` (chỉ trong `app/src/debug`, không có trong bản release) để ghi dữ liệu mẫu vào Health Connect trên emulator Android 14+:

```powershell
adb shell pm grant com.healthapp android.permission.health.WRITE_STEPS   # tương tự WRITE_ACTIVE_CALORIES_BURNED, WRITE_SLEEP, WRITE_HEART_RATE, WRITE_WEIGHT, WRITE_BLOOD_PRESSURE, WRITE_BLOOD_GLUCOSE, WRITE_EXERCISE, WRITE_OXYGEN_SATURATION, WRITE_RESTING_HEART_RATE, WRITE_BODY_TEMPERATURE, WRITE_RESPIRATORY_RATE, WRITE_HYDRATION
adb shell am broadcast -n com.healthapp/.debug.HcSampleDataReceiver                 # ghi 14 ngày dữ liệu mẫu (thay bản cũ, không nhân đôi)
adb shell am broadcast -n com.healthapp/.debug.HcSampleDataReceiver --es mode clear # xóa dữ liệu mẫu
```
