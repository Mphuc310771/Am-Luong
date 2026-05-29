# 🎧 Trình Điều Khiển Âm Lượng Siêu Cấp (Volume Control)

Ứng dụng quản lý âm lượng thông minh với giao diện vuốt chạm cực nhạy, hỗ trợ phím điều khiển ảo cạnh màn hình (Floating Handle), các bộ cấu hình âm thanh lưu sẵn (Presets) và phím tắt nhanh trên thanh trạng thái (Quick Settings Tile).

---

## 🚀 Các Tính Năng Chính

1. **Bảng Điều Khiển Trực Quan (UI Premium):** Thiết kế thanh trượt âm lượng mượt mà, đồng bộ thời gian thực cho cả 4 luồng âm thanh:
   * **Media** (Nhạc/Game)
   * **Ring** (Điện thoại)
   * **Alarm** (Báo thức)
   * **Notification** (Thông báo)
2. **Nút Ảo/Thanh Cạnh Màn Hình (Floating Edge Handle):**
   * Thanh điều khiển bán trong suốt nằm gọn ở mép màn hình (trái hoặc phải).
   * Hỗ trợ cử chỉ vuốt lên/xuống trực tiếp trên mép để tăng/giảm nhanh âm lượng mà không cần bấm phím cứng.
   * Chạm nhẹ để hiển thị nhanh bảng điều khiển âm lượng dạng Overlay tiện lợi.
3. **Bộ Cấu Hình Sẵn (Volume Presets - SQLite / Room DB):**
   * Tạo, lưu trữ và kích hoạt các cấu hình âm lượng tùy ý (Ví dụ: "Họp hành", "Xem phim", "Ngủ đêm").
   * Áp dụng toàn bộ mức âm thanh mong muốn chỉ bằng một chạm.
4. **Phím Tắt Quick Settings Tile:**
   * Thêm phím điều khiển nhanh vào khu vực thông báo của Android giúp bật/tắt dịch vụ hoặc kích hoạt bảng Overlay từ bất cứ đâu.
5. **Cơ Chế Tối Ưu RAM Cực Hạn (RAM Purge Engine):**
   * Tự động giải phóng 100% cây giao diện Compose, đóng liên kết SQLite Room DB và ép Garbage Collector dọn dẹp sạch sẽ RAM khi ứng dụng xuống nền, giữ bộ nhớ nhàn rỗi ở mức thấp kỷ lục.

---

## 🛠️ Hướng Dẫn Sử Dụng Chi Tiết

### Bước 1: Cấp quyền hoạt động
Khi mở ứng dụng lần đầu, bạn cần nhấn cấp 2 quyền cơ bản sau để ứng dụng hoạt động đầy đủ:
1. **Quyền vẽ đè lên ứng dụng khác (Draw Overlays):** Cần thiết để hiển thị Thanh kéo cạnh màn hình và Bảng điều khiển Overlay khi bạn đang sử dụng ứng dụng khác.
2. **Quyền hiển thị thông báo (Notification Permission):** Cần thiết để duy trì dịch vụ chạy ngầm mượt mà và không bị hệ điều hành tắt.

### Bước 2: Bật/Tắt Dịch Vụ Điều Khiển
* Tại màn hình chính, gạt công tắc **"Bật Dịch Vụ Điều Khiển"** (Enable Volume Service).
* Một thanh ảo mỏng (Floating Handle) sẽ xuất hiện ở góc bên cạnh màn hình của bạn.

### Bước 3: Thao tác cử chỉ trên nút ảo cạnh màn hình
Bạn có thể cấu hình cử chỉ này trong phần cài đặt của màn hình chính:
* **Chạm nhẹ (Tap):** Mở nhanh bảng điều khiển âm lượng dạng thẻ trượt (vẫn giữ nguyên ứng dụng bạn đang xem).
* **Rê/Vuốt dọc (Drag):** Trượt ngón tay lên hoặc xuống trên thanh ảo để tăng hoặc giảm nhanh mức âm lượng hiện tại.
* **Cấu hình vị trí & kích thước:** Có thể thay đổi chiều cao thanh ảo, vị trí chiều dọc (Y Offset), độ mờ (Opacity) và đổi mép màn hình (Trái/Phải).

### Bước 4: Tạo và Sử Dụng Presets (Cấu Hình Sẵn)
1. Kéo các thanh trượt âm thanh tại màn hình chính về mức mong muốn (Ví dụ: Nhạc 80%, Chuông 0%, Báo thức 100%).
2. Nhấn vào nút biểu tượng **Thêm bối cảnh (+)** ở khu vực Presets.
3. Đặt đặt tên cho Preset đó (ví dụ: "Chế độ Chơi Game") và lưu lại.
4. Khi muốn áp dụng bối cảnh, bạn chỉ cần nhấn vào tên Preset trong danh sách.

### Bước 5: Thêm Phím Tắt Vào Thanh Trạng Thái (Quick Settings Tile)
1. Vuốt thanh trạng thái của điện thoại xuống hết cỡ để mở bảng phím tắt nhanh.
2. Chọn biểu tượng **Chỉnh sửa / Bút chì**.
3. Tìm ô có tên **"Bật/Tắt Âm Lượng"** hoặc **"Volume Shortcut"** và kéo vào khu vực phím tắt chính của bạn.
4. Bây giờ, bạn có thể bật nhanh phím ảo hoặc gọi giao diện điều khiển ngay lập tức bất cứ lúc nào!

---

## ⚙️ Giải Thích Cơ Chế Tiết Kiệm Bộ Nhớ (Zero-Idle RAM Tech)

Ứng dụng này tích hợp bộ tối ưu rác bộ nhớ sâu dưới nền:
* Ngay sau khi bạn nhấn phím Home hoặc chuyển sang ứng dụng khác, hệ thống sẽ thực hiện **RAM Purge**: Giải phóng toàn bộ các đối tượng Compose nặng, ngắt kết nối SQLite và đóng hoàn toàn bộ đệm Room DB.
* Hệ thống lập tức thu dọn hoàn toàn tài nguyên ảo về mức tối thiểu, giúp điện thoại luôn hoạt động mát mẻ và trơn tru.
