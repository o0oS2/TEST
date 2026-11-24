AUTO MES OQC - BẢN APK (XEM DỮ LIỆU TRÊN ĐIỆN THOẠI)
====================================================
Là gì: app Android mở lại ĐÚNG trang "Xem dữ liệu" (view/index.html) của bản exe: Quét mã / Đóng gói / xuất CSV...
Dữ liệu đọc và ghi THẲNG vào thư mục chia sẻ mà các máy exe đang dùng (qua giao thức SMB, thư viện jcifs-ng).
Không dùng Supabase. Không cần cài gì thêm trên máy chứa thư mục.

1) BUILD APK BẰNG GITHUB (không cần cài phần mềm nào)
   a. Tạo tài khoản github.com (miễn phí) -> New repository (đặt tên tùy ý, chọn Private cũng được) -> Create.
   b. Trong repo: Add file -> Upload files -> kéo TOÀN BỘ nội dung đã giải nén của file zip này
      (thư mục app, gradle, .github, các file settings.gradle, build.gradle...) -> Commit changes.
      LƯU Ý: thư mục ẩn ".github" phải được tải lên. Nếu trình duyệt không kéo được thư mục ẩn, bấm
      Add file -> Create new file, gõ tên  .github/workflows/build-apk.yml  rồi dán nội dung file build-apk.yml vào.
   c. Mở tab Actions -> "Build APK" -> Run workflow (hoặc tự chạy sau mỗi lần commit). Chờ khoảng 3-6 phút.
   d. Bấm vào lần chạy vừa xong -> mục Artifacts ở cuối trang -> tải "AutoMesOQC_Xem-apk" (file zip chứa AutoMesOQC_Xem.apk).
   e. Muốn tải APK trực tiếp từ điện thoại: ở repo bấm Releases -> Create a new release -> đặt tag v1.0 -> Publish;
      sau vài phút file AutoMesOQC_Xem.apk xuất hiện trong release đó.
   Lỗi build: mở lần chạy màu đỏ, chép đoạn lỗi gửi cho Claude để sửa.

1b) CÁCH KHÁC: BUILD TRÊN MÁY TÍNH (1 lần, trên máy tính có Internet)
   Cách A - Android Studio (dễ nhất): File -> Open -> chọn thư mục AutoMesOQC_Android -> chờ Gradle sync
            -> Build -> Build App Bundle(s) / APK(s) -> Build APK(s). File: app\build\outputs\apk\release\app-release.apk
            (nếu chỉ thấy debug: Build Variants -> chọn release, hoặc dùng app-debug.apk, cài như nhau)
   Cách B - dòng lệnh: cài JDK 17 + Android SDK + Gradle 8.7, chạy build-apk.bat -> dist\AutoMesOQC_Xem.apk
   Lần build đầu tự tải thư viện (androidx, jcifs-ng...) từ Google/Maven Central.

2) CÀI LÊN ĐIỆN THOẠI
   Chép file .apk sang điện thoại, mở lên, cho phép "Cài từ nguồn không xác định". Điện thoại phải cùng mạng LAN/Wi-Fi với máy chứa thư mục.

3) LẦN ĐẦU MỞ APP -> màn hình Cài đặt
   - Thư mục lưu dữ liệu: đúng thư mục đã nhập trong exe, ví dụ  \\MAY-KHO\OQC  (hoặc smb://192.168.1.10/OQC). Dùng ĐỊA CHỈ IP nếu điện thoại không tìm được tên máy.
   - Tên đăng nhập / Mật khẩu: tài khoản Windows có quyền Đọc + Ghi thư mục đó. Windows 10/11 thường KHÔNG cho khách (để trống) truy cập,
     nên nên tạo 1 tài khoản riêng (vd oqc) cho các thiết bị xem.
   - Bấm "Kiểm tra kết nối" (app thử đọc và ghi 1 file nhỏ) -> thấy dấu ✔ thì bấm "Lưu".
   - Đổi sau này: menu ⋮ -> Cài đặt.

4) DÙNG
   - Giao diện giống cửa sổ Xem dữ liệu của exe. Nút tải file (⬇) xuất CSV: app mở khung "Lưu" để chọn nơi lưu (Tải xuống, Drive...).
   - Menu ⋮ -> Tải lại để nạp lại trang.

5) LƯU Ý
   - Chỉ chạy trong mạng LAN (không ra ngoài Internet được). Muốn xem từ xa phải có VPN vào mạng công ty.
   - Cần SMB 2/3 (Windows 7 trở lên đều có). Máy chứa thư mục cần mở cổng 445 trong tường lửa cho mạng nội bộ.
   - Mật khẩu lưu trong bộ nhớ riêng của app trên điện thoại (không mã hóa thêm).
   - Cấu trúc thư mục lưu giống bản exe (tickets, codes, _bus, _locks...). Đừng sửa tay khi đang có máy chạy.
   - Khi sửa view\index.html trong source exe: chép đè file vào app\src\main\assets\index.html rồi build lại APK.
