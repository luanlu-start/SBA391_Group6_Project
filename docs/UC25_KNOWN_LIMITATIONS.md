# UC25 – Pickup Confirmation: Known Limitations

Các giới hạn sau được chấp nhận trong phạm vi đồ án SBA391 ngày 02/11/2026:

- Upload bằng chứng sử dụng các kiểm tra cơ bản hiện có trong `OrderService.uploadOrderImages()`. MIME do client khai báo chưa được đối chiếu với nội dung ảnh thực tế; có rủi ro tải lên file giả mạo ảnh. Phiên bản này không bổ sung xác minh nội dung ảnh và không thay đổi hàm upload dùng chung.
- File được lưu ngoài transaction SQL. Nếu transaction rollback sau upload, file có thể còn lại trên ổ đĩa dù không có liên kết tương ứng trong database. Tạm thời chưa có cơ chế dọn file mồ côi.
- Các mã lỗi `PICKUP_*` chưa có thông báo riêng trong message resources; frontend hiển thị thông báo lỗi chung.

## Phạm vi xác minh

- 33 test tự động UC25 đã đạt: 26 test service và 7 test MockMvc, không sử dụng dữ liệu thật.
- MockMvc kiểm tra binding multipart, JWT principal và method security; không kiểm tra chữ ký JWT qua filter chain thực tế.
- Chưa kiểm thử tự động persistence, rollback hoặc tranh chấp khóa đồng thời trên SQL Server.
- TypeScript, Vite build và Maven compile đã đạt ở lượt kiểm tra trước commit. Đơn hàng #3 đã được người dùng kiểm thử thành công; không thực hiện lại xác nhận lấy hàng trên đơn này.

Việc chấp nhận các giới hạn trên áp dụng cho phiên bản đồ án hiện tại, không phải xác nhận cơ chế upload đã đáp ứng yêu cầu vận hành production.
