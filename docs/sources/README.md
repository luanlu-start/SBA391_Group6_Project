# Tài liệu nguồn FPTPost

Hai bản gốc do nhóm cung cấp, phiên bản 1.0 ngày 08/10/2026, trạng thái **dự thảo**.
Đưa vào repository ngày 09/10/2026; nội dung được giữ nguyên, chỉ đổi tên file để dễ liên kết.

- [Đặc tả BR/UC và các danh mục](fptpost-br-uc-v1.0.xlsx): 17 sheet.
- [Thiết kế hệ thống SPEC](fptpost-spec-v1.0.pdf): 46 trang.

Đối chiếu yêu cầu nghiệp vụ theo Excel, thiết kế kỹ thuật theo SPEC. Những lựa chọn
đã chốt riêng cho repository và điểm chưa thống nhất được ghi trong
[SPEC-ALIGNMENT.md](../SPEC-ALIGNMENT.md); không sửa bản gốc để che khác biệt.
Các bảng Markdown là bản tra cứu của v1.0, không phải chứng nhận chức năng đã triển khai.

## Kiểm tra bản gốc

| File trong Git | Tên file được cung cấp | SHA-256 |
| --- | --- | --- |
| fptpost-br-uc-v1.0.xlsx | FPTPost_Dac_ta_BR_UC.xlsx | ef23c85e3d78364d357206801822b7de03bba7f5f6234972d36fcf7f0b495175 |
| fptpost-spec-v1.0.pdf | FPTPost – Tài liệu thiết kế hệ thống (SPEC).pdf | ab3b9410ae1e91459ed88af108c5bb42075a5d3baa6fba9b15d60bae53fc2b91 |

## Cập nhật phiên bản

Khi nhóm duyệt thay đổi, lưu bản nguồn mới với số phiên bản mới, cập nhật các bảng tra cứu
và ghi ảnh hưởng tới UC, BR, API, database trong cùng PR. Giữ các bản nguồn cũ để truy vết.
