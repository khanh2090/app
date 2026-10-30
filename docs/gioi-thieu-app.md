# Fitness Companion – Ứng dụng đồng hành luyện tập

## 1. Giới thiệu

**Fitness Companion** là ứng dụng Android giúp người dùng theo dõi vận động hằng ngày và tập luyện
tại nhà. Ứng dụng tự đếm bước chân kể cả khi đã đóng, dùng cảm biến của điện thoại để đếm số lần
chống đẩy và gập bụng, có video hướng dẫn tập theo hiệp kèm đồng hồ nghỉ, và đồng bộ dữ liệu với
Google Health Connect.

Toàn bộ giao diện bằng tiếng Việt, hỗ trợ chế độ sáng và tối theo màu hình nền của máy
(Material You).

## 2. Chức năng chính

### 2.1. Đếm bước chân chạy nền

- Chạy trong một *foreground service*, có thông báo thường trực hiển thị số bước, quãng đường và
  lượng calo trong ngày, kèm nút "Dừng".
- Dùng cảm biến `TYPE_STEP_COUNTER` của phần cứng, tốn rất ít pin.
- Nếu máy không có cảm biến này hoặc cảm biến không báo bước, ứng dụng tự chuyển sang đếm bằng
  gia tốc kế. Một bước chỉ được tính khi đã thành nhịp đi, nên lắc máy không bị đếm nhầm.
- Quãng đường được tính theo chiều cao (độ dài bước ≈ 41,5% chiều cao); calo tính theo công thức
  MET × cân nặng × thời gian.
- Lịch sử từng ngày được lưu trong cơ sở dữ liệu Room.

### 2.2. Đếm số lần tập (chống đẩy, gập bụng)

Có bốn chế độ đếm:

| Chế độ | Cách hoạt động |
|---|---|
| Tiệm cận | Đặt máy dưới ngực (chống đẩy) hoặc gần đầu gối (gập bụng). Máy trạng thái XA → GẦN → XA được tính là 1 lần, có chống đếm trùng (tối thiểu 500 ms giữa hai lần). |
| Chuyển động | Đeo máy ở bắp tay hoặc giữ sát ngực; đếm theo gia tốc lên xuống của cơ thể. |
| Camera AI | Dùng CameraX và Google ML Kit Pose Detection để đo góc khuỷu tay (chống đẩy) hoặc góc hông (gập bụng). |
| Thủ công | Chạm vào con số sau mỗi lần tập. |

- Mỗi lần đếm được có tiếng bíp và rung.
- Màn hình hiển thị số lần cỡ lớn, đồng hồ, trạng thái tín hiệu cảm biến và các nút Bắt đầu,
  Tạm dừng, Tiếp tục, Dừng.
- Tự tạm dừng khi ứng dụng bị thu nhỏ và giữ nguyên trạng thái khi xoay màn hình.
- Thiết bị thiếu cảm biến nào thì chế độ tương ứng được ẩn đi, kèm thông báo giải thích.

### 2.3. Video hướng dẫn tập luyện

- 10 bài tập: chống đẩy, gập bụng, squat, chùng chân, bật nhảy dang tay, burpee, kéo xà, sprawl,
  vung tạ ấm, squat một chân.
- Mỗi bài có độ khó, nhóm cơ tác động, cách thực hiện và kế hoạch số hiệp × số lần.
- Có thể xem trước video ngay trong danh sách.
- Màn hình tập phát video bằng ExoPlayer (Media3), theo dõi từng hiệp, đếm ngược thời gian nghỉ
  với tiếng bíp ở 3 giây cuối, và lưu buổi tập vào lịch sử khi hoàn thành.
- Video được đóng gói sẵn trong ứng dụng nên xem được cả khi không có mạng. Các clip lấy từ
  Wikimedia Commons, có giấy phép tự do và được ghi nguồn dưới mỗi video.

### 2.4. Lịch sử và biểu đồ

- Biểu đồ cột số bước và số lần tập theo tuần hoặc theo tháng, tự vẽ bằng Compose Canvas.
- Tổng hợp tổng số bước, trung bình mỗi ngày và danh sách các buổi tập gần đây.

### 2.5. Đồng bộ Google Health Connect

- Xin quyền ngay trong ứng dụng: đọc bước chân, ghi bước chân, ghi buổi tập.
- Ghi số bước theo từng ngày và các buổi tập vào Health Connect; đồng bộ lại nhiều lần không
  tạo bản ghi trùng.
- Hiển thị tổng số bước hôm nay mà Health Connect đang lưu.

### 2.6. Hồ sơ và mục tiêu

Người dùng nhập chiều cao, cân nặng và mục tiêu mỗi ngày (số bước, calo, thời gian vận động).
Trang chủ hiển thị ba vòng tiến độ theo các mục tiêu này.

### 2.7. Tài khoản và đám mây (Firebase)

- Đăng ký và đăng nhập bằng email và mật khẩu.
- Sao lưu bước chân, buổi tập và hồ sơ lên Cloud Firestore; đăng nhập trên máy khác sẽ khôi
  phục lại dữ liệu.
- Danh sách bài tập có thể tải từ máy chủ.
- Bảng xếp hạng số bước trong ngày giữa những người dùng.

Phần này chỉ hoạt động khi ứng dụng được nối với một project Firebase (xem mục 6).

## 3. Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Kotlin 2.4 |
| Giao diện | Jetpack Compose, Material 3 |
| Kiến trúc | MVVM + Clean Architecture |
| Tiêm phụ thuộc | Hilt |
| Bất đồng bộ | Kotlin Coroutines, Flow |
| Lưu trữ cục bộ | Room (lịch sử), DataStore (hồ sơ, cài đặt) |
| Phát video | Jetpack Media3 (ExoPlayer) |
| Cảm biến | `SensorManager`: step counter, proximity, accelerometer |
| AI | CameraX + Google ML Kit Pose Detection |
| Sức khỏe | Health Connect |
| Backend | Firebase Authentication, Cloud Firestore |
| Kiểm thử | JUnit 4 |

Yêu cầu thiết bị: Android 8.0 (API 26) trở lên.

## 4. Kiến trúc

Ứng dụng chia thành ba tầng theo Clean Architecture:

- **Tầng domain:** mô hình dữ liệu, các bộ đếm dạng máy trạng thái, công thức tính toán và các
  use case. Tầng này không phụ thuộc Android nên kiểm thử được bằng unit test.
- **Tầng data:** cài đặt các repository bằng Room, DataStore, cảm biến, Health Connect và
  Firebase.
- **Tầng presentation:** các màn hình Compose và ViewModel; ViewModel giữ trạng thái màn hình
  bằng `StateFlow`.

Hilt nối các tầng với nhau.

```text
app/src/main/java/com/fitness/companion/
├── domain/          mô hình, bộ đếm, công thức, use case, interface repository
├── data/            Room, DataStore, cảm biến, Health Connect, Firebase
├── service/         StepCounterService (foreground service đếm bước)
├── di/              cấu hình Hilt
└── presentation/    màn hình: home, counter, workout, history, profile, account
```

Các tệp chính:

- `service/StepCounterService.kt`: service đếm bước chạy nền.
- `domain/counter/ProximityRepCounter.kt`: máy trạng thái đếm bằng cảm biến tiệm cận.
- `data/health/HealthConnectManager.kt`: kết nối Health Connect.
- `presentation/home`, `presentation/counter`, `presentation/workout`: các màn hình Trang chủ,
  Đếm số lần tập và Buổi tập.

## 5. Kiểm thử

31 unit test cho phần logic thuần, gồm:

- các bộ đếm số lần tập (tiệm cận, gia tốc, góc khớp);
- cách tính bước chân từ cảm biến, kể cả khi qua nửa đêm và khi khởi động lại máy;
- bộ phát hiện bước bằng gia tốc kế;
- công thức quãng đường và calo.

Chạy kiểm thử:

```bash
./gradlew :app:testDebugUnitTest
```

## 6. Cài đặt và chạy

1. Mở thư mục dự án bằng Android Studio.
2. Cắm điện thoại Android (đã bật Gỡ lỗi USB) hoặc mở một máy ảo.
3. Bấm **Run**.

Để bật phần tài khoản và đám mây:

1. Tạo một project trên Firebase Console.
2. Thêm ứng dụng Android với tên gói `com.fitness.companion`.
3. Tải tệp `google-services.json` về, đặt vào thư mục `app/`.
4. Bật đăng nhập Email/Password trong Authentication.
5. Tạo Firestore Database và dán quy tắc bảo mật trong tệp `firestore.rules`.

## 7. Giới hạn hiện tại

- Độ chính xác của chế độ đếm bằng gia tốc kế và camera phụ thuộc vào cách đặt máy và dáng
  tập; ngưỡng phát hiện mới được tinh chỉnh trên dữ liệu mô phỏng.
- Một số điện thoại dùng cảm biến tiệm cận "ảo", không nhận khi máy nằm trên sàn; khi đó nên
  dùng chế độ Chuyển động hoặc Camera AI.
- Health Connect có sẵn từ Android 14; máy cũ hơn cần cài ứng dụng Health Connect.
- Khi bật đếm bước bằng gia tốc kế, ứng dụng giữ máy thức nên tốn pin hơn so với cảm biến bước
  chân phần cứng.
