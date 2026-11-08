package com.fptpost.modules.order.service;

import com.fptpost.modules.order.model.Order;

/**
 * Kiểm duyệt tự động khi đăng hoặc sửa đơn (UC-12 bước 7, UC-54): đơn sạch lên sàn,
 * đơn có từ khóa/danh mục cấm hoặc nghi ngờ ở lại trạng thái chờ kiểm duyệt.
 * Chạy trong cùng giao dịch lưu đơn.
 */
public interface OrderSubmissionScreening {

    void screen(Order order);
}
