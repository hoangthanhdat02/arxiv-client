# 📱 arXiv Android Client (Java Native) - Topic 28

> **Ứng dụng Di động arXiv Client chuyên dụng cho nền tảng Android (Java Native)**  
> Môn học: **Phát triển Ứng dụng Di động (Mobile Application Development)**  
> Hình thức nghiệm thu: **Live Demo 100% trên Máy giả lập Android (AVD)** từ mã nguồn Android Studio (không dùng slide, không nộp báo cáo giấy).

---

## 👥 Phân công trách nhiệm 5 thành viên Android (Theo Đặc tả dự án)

* **🧑‍💻 Thành viên 1: Lập trình Khung Ứng dụng & Điều hướng (Đạt_con - Leader)**
  * Khởi tạo dự án Android Java, cấu hình `build.gradle`, Git repo, `ViewBinding`.
  * Lập trình `MainActivity` với `BottomNavigationView` và Fragment Container 4 tab (*Khám phá, Tìm kiếm, Đã lưu, Cài đặt*).
  * Chuyển đổi Dark Mode & Light Mode qua `AppCompatDelegate`.
  * Cấu hình `ApiClient` (Retrofit 2 + Gson) kết nối tới Backend Gateway (`10.0.2.2:3001`).

* **🧑‍💻 Thành viên 3: Lập trình Trang chủ & Thẻ bài báo**
  * Thiết kế `item_paper_card.xml` (CardView hiển thị tiêu đề, tác giả, badge chuyên mục, bookmark).
  * Lập trình `PaperAdapter` cho `RecyclerView` hỗ trợ click mở đọc PDF.
  * Lập trình `HomeFragment`: gọi API `/api/papers` qua Retrofit, hỗ trợ `SwipeRefreshLayout` và hiệu ứng khung xương (`Shimmer Loading`).
  * Xử lý phân trang cuộn tải thêm bài báo mới.

* **🧑‍💻 Thành viên 4: Lập trình Màn hình Tìm kiếm & Bộ lọc (Đức)**
  * Thiết kế giao diện tìm kiếm `SearchFragment` với ô nhập liệu và `RecyclerView`.
  * Viết class `DebounceTextWatcher` (delay 500ms) chống spam request khi gõ phím từ máy tính vào máy ảo.
  * Lập trình `FilterBottomSheetDialog`: lọc chuyên mục `cs.AI`, `cs.LG` và sắp xếp theo ngày nộp / độ liên quan.
  * Xử lý trạng thái rỗng (Empty State) và cấu hình bàn phím ảo `adjustResize` không làm vỡ layout.

* **🧑‍💻 Thành viên 5: Lập trình Trình đọc PDF Native trong App (Ngo)**
  * Lập trình `PdfReaderActivity` mở file PDF trực tiếp trong ứng dụng trên máy giả lập.
  * Hỗ trợ thao tác cảm ứng/chuột: phóng to/thu nhỏ (Zoom in/out), lướt chuyển trang.
  * Thanh công cụ hiển thị số trang hiện tại (`Trang X/Y`) và `SeekBar` nhảy nhanh đến trang mong muốn.
  * Lưu lại trang đọc dở dang vào CSDL để tự động mở lại đúng trang ở lần sau.

* **🧑‍💻 Thành viên 6: Lập trình Tải Tệp & Màn hình Cài đặt**
  * Viết module `DownloadService`: tải file PDF ngầm vào bộ nhớ nội bộ của máy giả lập qua OkHttp.
  * Cập nhật tiến độ tải theo % lên giao diện.
  * Lập trình `SettingsFragment`: thống kê dung lượng bộ nhớ đã lưu file PDF trên máy ảo và nút "Dọn dẹp bộ nhớ / Xóa cache".

* **🧑‍💻 Thành viên 7: Lập trình CSDL Cục bộ SQLite & Tiện ích (Hiếu Bigh)**
  * Viết `ArxivDbHelper` quản lý 2 bảng `bookmarks` và `read_history` trong `arxiv_client.db`.
  * Viết các hàm DAO CRUD: thêm bookmark, xóa, lưu đường dẫn file PDF offline.
  * Lập trình màn hình `LibraryFragment`: đọc từ SQLite hiển thị danh sách bài báo ngoại tuyến **khi bật Chế độ máy bay (Airplane Mode)**.
  * Tiện ích: Sao chép trích dẫn BibTeX vào `ClipboardManager` và chia sẻ bài báo qua Android Intent.

---

## 🛠️ Công nghệ & Thư viện sử dụng
* **Ngôn ngữ:** Java (JDK 17)
* **IDE Khuyến nghị:** Android Studio Hedgehog / Iguana / Jellyfish trở lên.
* **Môi trường chạy:** Máy giả lập Android (AVD) - Cấu hình khuyến nghị: Google Pixel 6 hoặc 7, Android 12 hoặc 13 (API 31–33), x86_64, Hardware Acceleration.
* **Thư viện chính:**
  * `Retrofit 2` + `Gson Converter`: Gọi REST API từ Backend ExpressJS.
  * `OkHttp 3` + `Logging Interceptor`: Tải file PDF ngầm và ghi log mạng.
  * `Material Components Android`: CardView, BottomNavigationView, TabLayout, Chips, Dialog.
  * `SwipeRefreshLayout`: Kéo xuống để tải lại dữ liệu.
  * `Facebook Shimmer`: Hiệu ứng khung xương khi tải trang.
  * `SQLiteOpenHelper`: Quản lý CSDL cục bộ `arxiv_client.db`.

---

## 📱 Hướng dẫn Mở và Chạy Dự án trên Android Studio

### Bước 1: Khởi động Backend trước
Đảm bảo bạn đã mở terminal chạy Backend ExpressJS tại thư mục `arxiv-backend`:
```bash
npm start
```
*(Server chạy tại cổng `http://localhost:3001`)*

### Bước 2: Mở dự án trên Android Studio
1. Mở **Android Studio** -> Chọn **Open**.
2. Trỏ đến thư mục: `d:\Application\arxiv-client` (hoặc nơi chứa mã nguồn repo này).
3. Đợi Android Studio đồng bộ Gradle (**Gradle Sync**).

### Bước 3: Chạy trên Máy giả lập AVD
1. Chọn máy giả lập (VD: `Pixel 7 API 33`).
2. Bấm nút **Run (tam giác xanh)** hoặc phím tắt `Shift + F10`.
3. Ứng dụng sẽ tự động được biên dịch và cài đặt mở lên trên màn hình máy giả lập!

---

## 🎬 Kịch bản 6 bước Live Demo khi Bảo vệ trước Giảng viên

1. **Bước 1: Khởi động & Khám phá (TV1 & TV3 - 1 phút):**  
   Mở app trên AVD -> Thấy hiệu ứng Shimmer -> Danh sách bài báo cs.AI hiện ra từ ExpressJS -> Kéo chuột vuốt xuống để làm mới (Pull-to-refresh) -> Vào Cài đặt bật công tắc Dark Mode.
2. **Bước 2: Tìm kiếm & Bộ lọc (TV4 - 1 phút):**  
   Vào tab Tìm kiếm -> Gõ "transformer" (debounce 500ms không lag) -> Mở Bộ lọc chọn cs.LG -> Kết quả trả về ngay lập tức, bàn phím ảo đẩy lên không làm vỡ layout.
3. **Bước 3: Đọc PDF Native trong App (TV5 - 1 phút):**  
   Bấm vào bài báo -> Bấm "Đọc PDF" -> Trình đọc mở mượt mà -> Thao tác zoom to công thức, kéo thanh trượt chuyển trang.
4. **Bước 4: Tải file & Quản lý bộ nhớ (TV6 - 30 giây):**  
   Bấm "Tải về" -> Tiến độ % tải -> Vào Cài đặt xem dung lượng bộ nhớ cache trên máy ảo.
5. **Bước 5: Thử nghiệm ngắt mạng (Điểm nhấn kỹ thuật - TV7):**  
   Kéo thanh cài đặt nhanh của máy giả lập -> **Bật Chế độ máy bay (Airplane Mode)** -> Vào tab "Đã lưu" -> Các bài báo đã lưu từ SQLite vẫn mở đọc offline mượt mà.
6. **Bước 6: Tiện ích học thuật (TV2 & TV7 - 30 giây):**  
   Bấm "BibTeX" -> Báo đã sao chép -> Mở ứng dụng Ghi chú trên máy ảo dán trích dẫn chuẩn học thuật `@misc`.
