-- ==============================================================================================
-- FPTPOST DATABASE SEED DATA SCRIPT (data.sql)
-- DBMS: Microsoft SQL Server (T-SQL) / Transact-SQL
-- Mục đích: Khởi tạo dữ liệu mẫu phong phú, chuẩn xác, toàn vẹn quan hệ Khóa ngoại (FK)
-- Tính chất: Idempotent - Có kiểm tra IF NOT EXISTS, an toàn khi chạy lại nhiều lần
-- Tài khoản mặc định: sender@fptpost.vn, courier@fptpost.vn, admin@fptpost.vn, moderator@fptpost.vn
-- Mật khẩu mặc định: 12345678 (BCrypt hash $2a$12$sqcULeI0cOilwI7ig1NWieaCqxyNZClp5og1aPGtJKyfHzK8P/SH2)
-- ==============================================================================================

-- ----------------------------------------------------------------------------------------------
-- 1. BẢNG ROLES (DB-002)
-- ----------------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM roles WHERE code = 'SENDER')
    INSERT INTO roles (code) VALUES ('SENDER');

IF NOT EXISTS (SELECT 1 FROM roles WHERE code = 'COURIER')
    INSERT INTO roles (code) VALUES ('COURIER');

IF NOT EXISTS (SELECT 1 FROM roles WHERE code = 'MODERATOR')
    INSERT INTO roles (code) VALUES ('MODERATOR');

IF NOT EXISTS (SELECT 1 FROM roles WHERE code = 'ADMIN')
    INSERT INTO roles (code) VALUES ('ADMIN');


-- ----------------------------------------------------------------------------------------------
-- 2. BẢNG USERS (DB-001)
-- ----------------------------------------------------------------------------------------------
-- 2.1 Quản trị viên (ADMIN)
IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@fptpost.vn')
    INSERT INTO users (
        public_id, email, phone, password_hash, status,
        email_verified_at, phone_verified_at, fpt_badge, is_deleted,
        created_at, updated_at, version, failed_login_count, login_locked_until
    ) VALUES (
        'A0000000-0000-0000-0000-000000000001', 'admin@fptpost.vn', '+84903456789',
        '$2a$12$sqcULeI0cOilwI7ig1NWieaCqxyNZClp5og1aPGtJKyfHzK8P/SH2', 'ACTIVE',
        SYSUTCDATETIME(), SYSUTCDATETIME(), 1, 0,
        SYSUTCDATETIME(), SYSUTCDATETIME(), 0, 0, NULL
    );

-- 2.2 Kiểm duyệt viên (MODERATOR)
IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'moderator@fptpost.vn')
    INSERT INTO users (
        public_id, email, phone, password_hash, status,
        email_verified_at, phone_verified_at, fpt_badge, is_deleted,
        created_at, updated_at, version, failed_login_count, login_locked_until
    ) VALUES (
        'A0000000-0000-0000-0000-000000000002', 'moderator@fptpost.vn', '+84904567890',
        '$2a$12$sqcULeI0cOilwI7ig1NWieaCqxyNZClp5og1aPGtJKyfHzK8P/SH2', 'ACTIVE',
        SYSUTCDATETIME(), SYSUTCDATETIME(), 1, 0,
        SYSUTCDATETIME(), SYSUTCDATETIME(), 0, 0, NULL
    );

-- 2.3 Người gửi hàng 1 (SENDER)
IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'sender@fptpost.vn')
    INSERT INTO users (
        public_id, email, phone, password_hash, status,
        email_verified_at, phone_verified_at, fpt_badge, is_deleted,
        created_at, updated_at, version, failed_login_count, login_locked_until
    ) VALUES (
        'A0000000-0000-0000-0000-000000000003', 'sender@fptpost.vn', '+84901234567',
        '$2a$12$sqcULeI0cOilwI7ig1NWieaCqxyNZClp5og1aPGtJKyfHzK8P/SH2', 'ACTIVE',
        SYSUTCDATETIME(), SYSUTCDATETIME(), 1, 0,
        SYSUTCDATETIME(), SYSUTCDATETIME(), 0, 0, NULL
    );

-- 2.4 Người gửi hàng 2 (SENDER)
IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'sender2@fptpost.vn')
    INSERT INTO users (
        public_id, email, phone, password_hash, status,
        email_verified_at, phone_verified_at, fpt_badge, is_deleted,
        created_at, updated_at, version, failed_login_count, login_locked_until
    ) VALUES (
        'A0000000-0000-0000-0000-000000000004', 'sender2@fptpost.vn', '+84901112233',
        '$2a$12$sqcULeI0cOilwI7ig1NWieaCqxyNZClp5og1aPGtJKyfHzK8P/SH2', 'ACTIVE',
        SYSUTCDATETIME(), SYSUTCDATETIME(), 1, 0,
        SYSUTCDATETIME(), SYSUTCDATETIME(), 0, 0, NULL
    );

-- 2.5 Tài xế / Shipper 1 (COURIER)
IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'courier@fptpost.vn')
    INSERT INTO users (
        public_id, email, phone, password_hash, status,
        email_verified_at, phone_verified_at, fpt_badge, is_deleted,
        created_at, updated_at, version, failed_login_count, login_locked_until
    ) VALUES (
        'A0000000-0000-0000-0000-000000000005', 'courier@fptpost.vn', '+84902345678',
        '$2a$12$sqcULeI0cOilwI7ig1NWieaCqxyNZClp5og1aPGtJKyfHzK8P/SH2', 'ACTIVE',
        SYSUTCDATETIME(), SYSUTCDATETIME(), 1, 0,
        SYSUTCDATETIME(), SYSUTCDATETIME(), 0, 0, NULL
    );

-- 2.6 Tài xế / Shipper 2 (COURIER)
IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'courier2@fptpost.vn')
    INSERT INTO users (
        public_id, email, phone, password_hash, status,
        email_verified_at, phone_verified_at, fpt_badge, is_deleted,
        created_at, updated_at, version, failed_login_count, login_locked_until
    ) VALUES (
        'A0000000-0000-0000-0000-000000000006', 'courier2@fptpost.vn', '+84908889900',
        '$2a$12$sqcULeI0cOilwI7ig1NWieaCqxyNZClp5og1aPGtJKyfHzK8P/SH2', 'ACTIVE',
        SYSUTCDATETIME(), SYSUTCDATETIME(), 1, 0,
        SYSUTCDATETIME(), SYSUTCDATETIME(), 0, 0, NULL
    );


-- ----------------------------------------------------------------------------------------------
-- 3. BẢNG PHÂN QUYỀN USER_ROLES (DB-002)
-- ----------------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM user_roles ur JOIN users u ON ur.user_id = u.id JOIN roles r ON ur.role_id = r.id WHERE u.email = 'admin@fptpost.vn' AND r.code = 'ADMIN')
    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE u.email = 'admin@fptpost.vn' AND r.code = 'ADMIN';

IF NOT EXISTS (SELECT 1 FROM user_roles ur JOIN users u ON ur.user_id = u.id JOIN roles r ON ur.role_id = r.id WHERE u.email = 'moderator@fptpost.vn' AND r.code = 'MODERATOR')
    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE u.email = 'moderator@fptpost.vn' AND r.code = 'MODERATOR';

IF NOT EXISTS (SELECT 1 FROM user_roles ur JOIN users u ON ur.user_id = u.id JOIN roles r ON ur.role_id = r.id WHERE u.email = 'sender@fptpost.vn' AND r.code = 'SENDER')
    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE u.email = 'sender@fptpost.vn' AND r.code = 'SENDER';

IF NOT EXISTS (SELECT 1 FROM user_roles ur JOIN users u ON ur.user_id = u.id JOIN roles r ON ur.role_id = r.id WHERE u.email = 'sender2@fptpost.vn' AND r.code = 'SENDER')
    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE u.email = 'sender2@fptpost.vn' AND r.code = 'SENDER';

IF NOT EXISTS (SELECT 1 FROM user_roles ur JOIN users u ON ur.user_id = u.id JOIN roles r ON ur.role_id = r.id WHERE u.email = 'courier@fptpost.vn' AND r.code = 'COURIER')
    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE u.email = 'courier@fptpost.vn' AND r.code = 'COURIER';

IF NOT EXISTS (SELECT 1 FROM user_roles ur JOIN users u ON ur.user_id = u.id JOIN roles r ON ur.role_id = r.id WHERE u.email = 'courier2@fptpost.vn' AND r.code = 'COURIER')
    INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE u.email = 'courier2@fptpost.vn' AND r.code = 'COURIER';


-- ----------------------------------------------------------------------------------------------
-- 4. BẢNG HỒ SƠ NGƯỜI DÙNG USER_PROFILES (DB-003)
-- ----------------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM user_profiles up JOIN users u ON up.user_id = u.id WHERE u.email = 'admin@fptpost.vn')
    INSERT INTO user_profiles (user_id, full_name, adult_confirmed_at)
    SELECT u.id, N'Lê Quản Trị Admin', SYSUTCDATETIME() FROM users u WHERE u.email = 'admin@fptpost.vn';

IF NOT EXISTS (SELECT 1 FROM user_profiles up JOIN users u ON up.user_id = u.id WHERE u.email = 'moderator@fptpost.vn')
    INSERT INTO user_profiles (user_id, full_name, adult_confirmed_at)
    SELECT u.id, N'Hoàng Kiểm Duyệt', SYSUTCDATETIME() FROM users u WHERE u.email = 'moderator@fptpost.vn';

IF NOT EXISTS (SELECT 1 FROM user_profiles up JOIN users u ON up.user_id = u.id WHERE u.email = 'sender@fptpost.vn')
    INSERT INTO user_profiles (user_id, full_name, adult_confirmed_at)
    SELECT u.id, N'Nguyễn Văn Sender', SYSUTCDATETIME() FROM users u WHERE u.email = 'sender@fptpost.vn';

IF NOT EXISTS (SELECT 1 FROM user_profiles up JOIN users u ON up.user_id = u.id WHERE u.email = 'sender2@fptpost.vn')
    INSERT INTO user_profiles (user_id, full_name, adult_confirmed_at)
    SELECT u.id, N'Phạm Thị Khách Hàng', SYSUTCDATETIME() FROM users u WHERE u.email = 'sender2@fptpost.vn';

IF NOT EXISTS (SELECT 1 FROM user_profiles up JOIN users u ON up.user_id = u.id WHERE u.email = 'courier@fptpost.vn')
    INSERT INTO user_profiles (user_id, full_name, adult_confirmed_at)
    SELECT u.id, N'Trần Văn Shipper', SYSUTCDATETIME() FROM users u WHERE u.email = 'courier@fptpost.vn';

IF NOT EXISTS (SELECT 1 FROM user_profiles up JOIN users u ON up.user_id = u.id WHERE u.email = 'courier2@fptpost.vn')
    INSERT INTO user_profiles (user_id, full_name, adult_confirmed_at)
    SELECT u.id, N'Lê Tốc Hành Shipper', SYSUTCDATETIME() FROM users u WHERE u.email = 'courier2@fptpost.vn';


-- ----------------------------------------------------------------------------------------------
-- 5. BẢNG ĐIỀU KHOẢN & SỰ ĐỒNG THUẬN CONSENTS (DB-004)
-- ----------------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM consents c JOIN users u ON c.user_id = u.id WHERE u.email = 'sender@fptpost.vn' AND c.policy_type = 'TERMS_OF_SERVICE')
    INSERT INTO consents (user_id, policy_type, policy_version, accepted_at, ip)
    SELECT u.id, 'TERMS_OF_SERVICE', 'draft-2026-10-09', SYSUTCDATETIME(), '127.0.0.1' FROM users u WHERE u.email = 'sender@fptpost.vn';

IF NOT EXISTS (SELECT 1 FROM consents c JOIN users u ON c.user_id = u.id WHERE u.email = 'sender@fptpost.vn' AND c.policy_type = 'PRIVACY_POLICY')
    INSERT INTO consents (user_id, policy_type, policy_version, accepted_at, ip)
    SELECT u.id, 'PRIVACY_POLICY', 'draft-2026-10-09', SYSUTCDATETIME(), '127.0.0.1' FROM users u WHERE u.email = 'sender@fptpost.vn';

IF NOT EXISTS (SELECT 1 FROM consents c JOIN users u ON c.user_id = u.id WHERE u.email = 'courier@fptpost.vn' AND c.policy_type = 'TERMS_OF_SERVICE')
    INSERT INTO consents (user_id, policy_type, policy_version, accepted_at, ip)
    SELECT u.id, 'TERMS_OF_SERVICE', 'draft-2026-10-09', SYSUTCDATETIME(), '127.0.0.1' FROM users u WHERE u.email = 'courier@fptpost.vn';


-- ----------------------------------------------------------------------------------------------
-- 6. BẢNG VÍ TIỀN WALLETS (DB-024)
-- ----------------------------------------------------------------------------------------------
-- 6.1 Ví người gửi sender@fptpost.vn (Có sẵn 5,000,000 VND, đang ký quỹ 350,000 VND)
IF NOT EXISTS (SELECT 1 FROM wallets w JOIN users u ON w.user_id = u.id WHERE u.email = 'sender@fptpost.vn')
    INSERT INTO wallets (user_id, available, escrow_held, pending_withdraw, version, created_at, updated_at)
    SELECT u.id, 5000000, 350000, 0, 0, SYSUTCDATETIME(), SYSUTCDATETIME() FROM users u WHERE u.email = 'sender@fptpost.vn';

-- 6.2 Ví người gửi sender2@fptpost.vn (Có sẵn 2,500,000 VND)
IF NOT EXISTS (SELECT 1 FROM wallets w JOIN users u ON w.user_id = u.id WHERE u.email = 'sender2@fptpost.vn')
    INSERT INTO wallets (user_id, available, escrow_held, pending_withdraw, version, created_at, updated_at)
    SELECT u.id, 2500000, 0, 0, 0, SYSUTCDATETIME(), SYSUTCDATETIME() FROM users u WHERE u.email = 'sender2@fptpost.vn';

-- 6.3 Ví tài xế courier@fptpost.vn (Có sẵn 1,250,000 VND, đang chờ rút 500,000 VND)
IF NOT EXISTS (SELECT 1 FROM wallets w JOIN users u ON w.user_id = u.id WHERE u.email = 'courier@fptpost.vn')
    INSERT INTO wallets (user_id, available, escrow_held, pending_withdraw, version, created_at, updated_at)
    SELECT u.id, 1250000, 0, 500000, 0, SYSUTCDATETIME(), SYSUTCDATETIME() FROM users u WHERE u.email = 'courier@fptpost.vn';

-- 6.4 Ví tài xế courier2@fptpost.vn (Có sẵn 800,000 VND)
IF NOT EXISTS (SELECT 1 FROM wallets w JOIN users u ON w.user_id = u.id WHERE u.email = 'courier2@fptpost.vn')
    INSERT INTO wallets (user_id, available, escrow_held, pending_withdraw, version, created_at, updated_at)
    SELECT u.id, 800000, 0, 0, 0, SYSUTCDATETIME(), SYSUTCDATETIME() FROM users u WHERE u.email = 'courier2@fptpost.vn';


-- ----------------------------------------------------------------------------------------------
-- 7. BẢNG ĐƠN HÀNG VẬN CHUYỂN ORDERS (DB-014)
-- ----------------------------------------------------------------------------------------------
-- 7.1 Đơn ORD-TEST-001 (PENDING - Chờ thanh toán)
IF NOT EXISTS (SELECT 1 FROM orders WHERE order_code = 'ORD-TEST-001')
    INSERT INTO orders (
        order_code, sender_id, courier_id, pickup_address, delivery_address,
        receiver_name, receiver_phone, product_name, product_description, quantity,
        weight_kg, declared_value, price_suggested, price_agreed, distance_km,
        delivery_note, status, created_at, updated_at
    )
    SELECT
        'ORD-TEST-001', u.id, NULL,
        N'Khu Công Nghệ Cao Hòa Lạc, Thạch Thất, Hà Nội', N'Tòa nhà Keangnam Landmark 72, Nam Từ Liêm, Hà Nội',
        N'Trần Minh Tuấn', '0912345678', N'Tài liệu hợp đồng kinh tế và hồ sơ thầu', N'Tài liệu quan trọng niêm phong bao thư A4', 1,
        0.500, 150000.00, 35000.00, 35000.00, 28.50,
        N'Giao giờ hành chính, gọi trước 15 phút', 'PENDING', SYSUTCDATETIME(), SYSUTCDATETIME()
    FROM users u WHERE u.email = 'sender@fptpost.vn';

-- 7.2 Đơn ORD-TEST-002 (PENDING_ESCROW - Đang chờ ký quỹ)
IF NOT EXISTS (SELECT 1 FROM orders WHERE order_code = 'ORD-TEST-002')
    INSERT INTO orders (
        order_code, sender_id, courier_id, pickup_address, delivery_address,
        receiver_name, receiver_phone, product_name, product_description, quantity,
        weight_kg, declared_value, price_suggested, price_agreed, distance_km,
        delivery_note, status, created_at, updated_at
    )
    SELECT
        'ORD-TEST-002', u.id, NULL,
        N'Đại học FPT, Hòa Lạc, Hà Nội', N'Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội',
        N'Lê Quỳnh Anh', '0987654321', N'Gói quà lưu niệm công nghệ FPT', N'Áo thun polo FPT và bình giữ nhiệt cao cấp', 1,
        1.200, 250000.00, 45000.00, 45000.00, 32.00,
        N'Bọc xốp chống va đập', 'PENDING_ESCROW', SYSUTCDATETIME(), SYSUTCDATETIME()
    FROM users u WHERE u.email = 'sender@fptpost.vn';

-- 7.3 Đơn ORD-TEST-003 (FINDING_SHIPPER - Đã ký quỹ, đang tìm tài xế trên sàn)
IF NOT EXISTS (SELECT 1 FROM orders WHERE order_code = 'ORD-TEST-003')
    INSERT INTO orders (
        order_code, sender_id, courier_id, pickup_address, delivery_address,
        receiver_name, receiver_phone, product_name, product_description, quantity,
        weight_kg, declared_value, price_suggested, price_agreed, distance_km,
        delivery_note, status, created_at, updated_at
    )
    SELECT
        'ORD-TEST-003', u.id, NULL,
        N'Làng Đại học Thủ Đức, TP. Hồ Chí Minh', N'Tòa nhà Bitexco, Quận 1, TP. Hồ Chí Minh',
        N'Hoàng Gia Bảo', '0933445566', N'Linh kiện vi mạch điện tử IoT', N'Mạch vi điều khiển ESP32 và cảm biến nhiệt độ', 2,
        0.800, 500000.00, 60000.00, 60000.00, 18.20,
        N'Hàng điện tử dễ vỡ, tránh ẩm ướt', 'FINDING_SHIPPER', SYSUTCDATETIME(), SYSUTCDATETIME()
    FROM users u WHERE u.email = 'sender@fptpost.vn';

-- 7.4 Đơn ORD-TEST-004 (SHIPPER_ASSIGNED - Tài xế đã nhận đơn, đang chuẩn bị lấy hàng)
IF NOT EXISTS (SELECT 1 FROM orders WHERE order_code = 'ORD-TEST-004')
    INSERT INTO orders (
        order_code, sender_id, courier_id, pickup_address, delivery_address,
        receiver_name, receiver_phone, product_name, product_description, quantity,
        weight_kg, declared_value, price_suggested, price_agreed, distance_km,
        delivery_note, status, created_at, updated_at
    )
    SELECT
        'ORD-TEST-004', u_sen.id, u_cou.id,
        N'Chợ Bến Thành, Quận 1, TP. Hồ Chí Minh', N'Khu Thảo Điền, Quận 2, TP. Thủ Đức',
        N'Ngô Bích Thủy', '0944556677', N'Hộp bánh đặc sản truyền thống', N'Bánh cốm đậu xanh và kẹo lạc mè', 3,
        2.000, 350000.00, 50000.00, 50000.00, 9.50,
        N'Giao trước 18h tối nay', 'SHIPPER_ASSIGNED', SYSUTCDATETIME(), SYSUTCDATETIME()
    FROM users u_sen CROSS JOIN users u_cou
    WHERE u_sen.email = 'sender@fptpost.vn' AND u_cou.email = 'courier@fptpost.vn';

-- 7.5 Đơn ORD-TEST-005 (IN_DELIVERY - Đang trên đường vận chuyển)
IF NOT EXISTS (SELECT 1 FROM orders WHERE order_code = 'ORD-TEST-005')
    INSERT INTO orders (
        order_code, sender_id, courier_id, pickup_address, delivery_address,
        receiver_name, receiver_phone, product_name, product_description, quantity,
        weight_kg, declared_value, price_suggested, price_agreed, distance_km,
        delivery_note, status, created_at, updated_at
    )
    SELECT
        'ORD-TEST-005', u_sen.id, u_cou.id,
        N'FPT Tower, Phạm Văn Bạch, Cầu Giấy, Hà Nội', N'Đường Nguyễn Văn Cừ, Long Biên, Hà Nội',
        N'Vũ Đức Thịnh', '0966778899', N'Điện thoại thông minh bưu phẩm bảo đảm', N'Smartphone nguyên seal có bảo hiểm giá trị cao', 1,
        0.600, 12000000.00, 70000.00, 70000.00, 14.00,
        N'Đồng kiểm cùng người nhận trước khi ký', 'IN_DELIVERY', SYSUTCDATETIME(), SYSUTCDATETIME()
    FROM users u_sen CROSS JOIN users u_cou
    WHERE u_sen.email = 'sender@fptpost.vn' AND u_cou.email = 'courier@fptpost.vn';

-- 7.6 Đơn ORD-TEST-006 (COMPLETED - Đã giao thành công & giải ngân)
IF NOT EXISTS (SELECT 1 FROM orders WHERE order_code = 'ORD-TEST-006')
    INSERT INTO orders (
        order_code, sender_id, courier_id, pickup_address, delivery_address,
        receiver_name, receiver_phone, product_name, product_description, quantity,
        weight_kg, declared_value, price_suggested, price_agreed, distance_km,
        delivery_note, status, created_at, updated_at
    )
    SELECT
        'ORD-TEST-006', u_sen.id, u_cou.id,
        N'Thư viện Đại học FPT Đà Nẵng', N'Quận Hải Châu, TP. Đà Nẵng',
        N'Đặng Hoàng Yến', '0977889900', N'Bộ sách giáo trình lập trình Cloud & AI', N'Trọn bộ 4 cuốn giáo trình đại học', 1,
        3.500, 450000.00, 80000.00, 80000.00, 11.80,
        N'Đã giao và đối soát biên nhận', 'COMPLETED', DATEADD(HOUR, -5, SYSUTCDATETIME()), SYSUTCDATETIME()
    FROM users u_sen CROSS JOIN users u_cou
    WHERE u_sen.email = 'sender2@fptpost.vn' AND u_cou.email = 'courier@fptpost.vn';

-- 7.7 Đơn ORD-TEST-007 (CANCELLED - Đã hủy bởi người gửi & hoàn tiền)
IF NOT EXISTS (SELECT 1 FROM orders WHERE order_code = 'ORD-TEST-007')
    INSERT INTO orders (
        order_code, sender_id, courier_id, pickup_address, delivery_address,
        receiver_name, receiver_phone, product_name, product_description, quantity,
        weight_kg, declared_value, price_suggested, price_agreed, distance_km,
        delivery_note, status, created_at, updated_at
    )
    SELECT
        'ORD-TEST-007', u.id, NULL,
        N'Số 10 Phố Huế, Hoàn Kiếm, Hà Nội', N'Số 88 Cầu Giấy, Hà Nội',
        N'Trịnh Thu Hằng', '0922334455', N'Hàng mẫu may mặc xuất khẩu', N'Mẫu vải cotton cao cấp', 1,
        1.000, 200000.00, 40000.00, 40000.00, 8.00,
        N'Người gửi hủy do đổi thời gian giao', 'CANCELLED', DATEADD(DAY, -1, SYSUTCDATETIME()), SYSUTCDATETIME()
    FROM users u WHERE u.email = 'sender@fptpost.vn';


-- ----------------------------------------------------------------------------------------------
-- 8. BẢNG ẢNH ĐƠN HÀNG ORDER_IMAGES (DB-015)
-- ----------------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM order_images oi JOIN orders o ON oi.order_id = o.id WHERE o.order_code = 'ORD-TEST-001')
    INSERT INTO order_images (order_id, image_url, created_at)
    SELECT o.id, 'https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?w=800', SYSUTCDATETIME()
    FROM orders o WHERE o.order_code = 'ORD-TEST-001';

IF NOT EXISTS (SELECT 1 FROM order_images oi JOIN orders o ON oi.order_id = o.id WHERE o.order_code = 'ORD-TEST-003')
    INSERT INTO order_images (order_id, image_url, created_at)
    SELECT o.id, 'https://images.unsplash.com/photo-1518770660439-4636190af475?w=800', SYSUTCDATETIME()
    FROM orders o WHERE o.order_code = 'ORD-TEST-003';

IF NOT EXISTS (SELECT 1 FROM order_images oi JOIN orders o ON oi.order_id = o.id WHERE o.order_code = 'ORD-TEST-005')
    INSERT INTO order_images (order_id, image_url, created_at)
    SELECT o.id, 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=800', SYSUTCDATETIME()
    FROM orders o WHERE o.order_code = 'ORD-TEST-005';


-- ----------------------------------------------------------------------------------------------
-- 9. BẢNG TIỀN KÝ QUỸ ESCROWS (DB-028)
-- ----------------------------------------------------------------------------------------------
-- 9.1 Ký quỹ cho ORD-TEST-003 (500,000 VND - Trạng thái HELD, thanh toán qua VNPAY)
IF NOT EXISTS (SELECT 1 FROM escrows e JOIN orders o ON e.order_id = o.id WHERE o.order_code = 'ORD-TEST-003')
    INSERT INTO escrows (order_id, sender_id, amount, status, payment_method, release_at, frozen, created_at, updated_at)
    SELECT o.id, o.sender_id, 500000, 'HELD', 'VNPAY', NULL, 0, SYSUTCDATETIME(), SYSUTCDATETIME()
    FROM orders o WHERE o.order_code = 'ORD-TEST-003';

-- 9.2 Ký quỹ cho ORD-TEST-004 (350,000 VND - Trạng thái HELD, thanh toán qua VÍ)
IF NOT EXISTS (SELECT 1 FROM escrows e JOIN orders o ON e.order_id = o.id WHERE o.order_code = 'ORD-TEST-004')
    INSERT INTO escrows (order_id, sender_id, amount, status, payment_method, release_at, frozen, created_at, updated_at)
    SELECT o.id, o.sender_id, 350000, 'HELD', 'WALLET', NULL, 0, SYSUTCDATETIME(), SYSUTCDATETIME()
    FROM orders o WHERE o.order_code = 'ORD-TEST-004';

-- 9.3 Ký quỹ cho ORD-TEST-005 (70,000 VND - Trạng thái HELD, thanh toán qua VÍ)
IF NOT EXISTS (SELECT 1 FROM escrows e JOIN orders o ON e.order_id = o.id WHERE o.order_code = 'ORD-TEST-005')
    INSERT INTO escrows (order_id, sender_id, amount, status, payment_method, release_at, frozen, created_at, updated_at)
    SELECT o.id, o.sender_id, 70000, 'HELD', 'WALLET', NULL, 0, SYSUTCDATETIME(), SYSUTCDATETIME()
    FROM orders o WHERE o.order_code = 'ORD-TEST-005';

-- 9.4 Ký quỹ cho ORD-TEST-006 (450,000 VND - Trạng thái RELEASED, đã giải ngân cho shipper)
IF NOT EXISTS (SELECT 1 FROM escrows e JOIN orders o ON e.order_id = o.id WHERE o.order_code = 'ORD-TEST-006')
    INSERT INTO escrows (order_id, sender_id, amount, status, payment_method, release_at, frozen, created_at, updated_at)
    SELECT o.id, o.sender_id, 450000, 'RELEASED', 'WALLET', SYSUTCDATETIME(), 0, DATEADD(HOUR, -5, SYSUTCDATETIME()), SYSUTCDATETIME()
    FROM orders o WHERE o.order_code = 'ORD-TEST-006';

-- 9.5 Ký quỹ cho ORD-TEST-007 (200,000 VND - Trạng thái REFUNDED, đã hoàn tiền người gửi)
IF NOT EXISTS (SELECT 1 FROM escrows e JOIN orders o ON e.order_id = o.id WHERE o.order_code = 'ORD-TEST-007')
    INSERT INTO escrows (order_id, sender_id, amount, status, payment_method, release_at, frozen, created_at, updated_at)
    SELECT o.id, o.sender_id, 200000, 'REFUNDED', 'VNPAY', NULL, 0, DATEADD(DAY, -1, SYSUTCDATETIME()), SYSUTCDATETIME()
    FROM orders o WHERE o.order_code = 'ORD-TEST-007';


-- ----------------------------------------------------------------------------------------------
-- 10. BẢNG GIAO DỊCH THANH TOÁN VNPAY PAYMENT_TRANSACTIONS (DB-029)
-- ----------------------------------------------------------------------------------------------
-- 10.1 Giao dịch PENDING chờ thanh toán ký quỹ ORD-TEST-001
IF NOT EXISTS (SELECT 1 FROM payment_transactions WHERE txn_ref = 'ORD-TEST-001')
    INSERT INTO payment_transactions (
        txn_ref, user_id, order_id, purpose, order_code, amount, order_info,
        bank_code, bank_tran_no, card_type, transaction_no, response_code,
        transaction_status, status, ip_address, pay_date, expires_at, created_at, updated_at
    )
    SELECT
        'ORD-TEST-001', o.sender_id, o.id, 'ORDER_ESCROW', o.order_code, 150000.00,
        'Thanh toan ky quy don hang ORD-TEST-001', 'NCB', NULL, NULL, NULL, NULL,
        NULL, 'PENDING', '127.0.0.1', NULL, DATEADD(MINUTE, 15, SYSUTCDATETIME()),
        SYSUTCDATETIME(), SYSUTCDATETIME()
    FROM orders o WHERE o.order_code = 'ORD-TEST-001';

-- 10.2 Giao dịch SUCCESS đã thanh toán ký quỹ ORD-TEST-003
IF NOT EXISTS (SELECT 1 FROM payment_transactions WHERE txn_ref = 'ORD-TEST-003')
    INSERT INTO payment_transactions (
        txn_ref, user_id, order_id, purpose, order_code, amount, order_info,
        bank_code, bank_tran_no, card_type, transaction_no, response_code,
        transaction_status, status, ip_address, pay_date, expires_at, created_at, updated_at
    )
    SELECT
        'ORD-TEST-003', o.sender_id, o.id, 'ORDER_ESCROW', o.order_code, 500000.00,
        'Thanh toan ky quy don hang ORD-TEST-003', 'NCB', 'VNP14567890', 'ATM', '14567890', '00',
        '00', 'SUCCESS', '127.0.0.1', '20261013180000', DATEADD(MINUTE, 15, SYSUTCDATETIME()),
        SYSUTCDATETIME(), SYSUTCDATETIME()
    FROM orders o WHERE o.order_code = 'ORD-TEST-003';

-- 10.3 Giao dịch nạp ví thành công TOPUP-20261014-001 (5,000,000 VND)
IF NOT EXISTS (SELECT 1 FROM payment_transactions WHERE txn_ref = 'TOPUP-20261014-001')
    INSERT INTO payment_transactions (
        txn_ref, user_id, order_id, purpose, order_code, amount, order_info,
        bank_code, bank_tran_no, card_type, transaction_no, response_code,
        transaction_status, status, ip_address, pay_date, expires_at, created_at, updated_at
    )
    SELECT
        'TOPUP-20261014-001', u.id, NULL, 'WALLET_TOPUP', NULL, 5000000.00,
        'Nap tien vao vi FPTPost - sender@fptpost.vn', 'NCB', 'VNP14567891', 'ATM', '14567891', '00',
        '00', 'SUCCESS', '127.0.0.1', '20261014083000', DATEADD(MINUTE, 15, SYSUTCDATETIME()),
        SYSUTCDATETIME(), SYSUTCDATETIME()
    FROM users u WHERE u.email = 'sender@fptpost.vn';


-- ----------------------------------------------------------------------------------------------
-- 11. BẢNG GIAO DỊCH SỔ CÁI LEDGER_TRANSACTIONS (DB-026) & BÚT TOÁN KÉP LEDGER_ENTRIES (DB-027)
-- ----------------------------------------------------------------------------------------------
-- 11.1 Giao dịch Sổ cái: Nạp ví 5,000,000 VND (WALLET_TOPUP)
IF NOT EXISTS (SELECT 1 FROM ledger_transactions WHERE transaction_code = 'TXN-TOPUP-001')
    INSERT INTO ledger_transactions (
        transaction_code, type, status, idempotency_key, order_id,
        ref_type, ref_id, amount, created_by, description, created_at
    )
    SELECT
        'TXN-TOPUP-001', 'WALLET_TOPUP', 'COMPLETED', 'IDEMP-TOPUP-001-20261014', NULL,
        'VNPAY_PAYMENT', 'TOPUP-20261014-001', 5000000, u.id,
        N'Bút toán nạp 5,000,000 VND vào ví khả dụng qua cổng VNPay', SYSUTCDATETIME()
    FROM users u WHERE u.email = 'sender@fptpost.vn';

-- Bút toán kép cho TXN-TOPUP-001:
-- Nợ TK 1101 (Cổng thanh toán VNPay) 5,000,000 / Có TK 2001 (Ví khả dụng người dùng) 5,000,000
IF NOT EXISTS (SELECT 1 FROM ledger_entries le JOIN ledger_transactions lt ON le.transaction_id = lt.id WHERE lt.transaction_code = 'TXN-TOPUP-001')
BEGIN
    INSERT INTO ledger_entries (transaction_id, account_code, account_type, debit, credit, created_at)
    SELECT lt.id, '1101_GATEWAY_VNPAY', 'ASSET', 5000000, 0, SYSUTCDATETIME()
    FROM ledger_transactions lt WHERE lt.transaction_code = 'TXN-TOPUP-001';

    INSERT INTO ledger_entries (transaction_id, account_code, account_type, debit, credit, created_at)
    SELECT lt.id, '2001_USER_WALLET_AVAILABLE', 'LIABILITY', 0, 5000000, SYSUTCDATETIME()
    FROM ledger_transactions lt WHERE lt.transaction_code = 'TXN-TOPUP-001';
END;

-- 11.2 Giao dịch Sổ cái: Ký quỹ đơn hàng 350,000 VND (ORDER_ESCROW_HOLD) cho ORD-TEST-004
IF NOT EXISTS (SELECT 1 FROM ledger_transactions WHERE transaction_code = 'TXN-ESCROW-001')
    INSERT INTO ledger_transactions (
        transaction_code, type, status, idempotency_key, order_id,
        ref_type, ref_id, amount, created_by, description, created_at
    )
    SELECT
        'TXN-ESCROW-001', 'ORDER_ESCROW_HOLD', 'COMPLETED', 'IDEMP-ESCROW-001-20261014', o.id,
        'ORDER', o.order_code, 350000, o.sender_id,
        N'Ký quỹ giam giữ tiền vận chuyển cho đơn hàng ORD-TEST-004', SYSUTCDATETIME()
    FROM orders o WHERE o.order_code = 'ORD-TEST-004';

-- Bút toán kép cho TXN-ESCROW-001:
-- Nợ TK 2001 (Ví khả dụng) 350,000 / Có TK 2101 (Tài khoản Ký quỹ giam giữ) 350,000
IF NOT EXISTS (SELECT 1 FROM ledger_entries le JOIN ledger_transactions lt ON le.transaction_id = lt.id WHERE lt.transaction_code = 'TXN-ESCROW-001')
BEGIN
    INSERT INTO ledger_entries (transaction_id, account_code, account_type, debit, credit, created_at)
    SELECT lt.id, '2001_USER_WALLET_AVAILABLE', 'LIABILITY', 350000, 0, SYSUTCDATETIME()
    FROM ledger_transactions lt WHERE lt.transaction_code = 'TXN-ESCROW-001';

    INSERT INTO ledger_entries (transaction_id, account_code, account_type, debit, credit, created_at)
    SELECT lt.id, '2101_ESCROW_HOLDING', 'LIABILITY', 0, 350000, SYSUTCDATETIME()
    FROM ledger_transactions lt WHERE lt.transaction_code = 'TXN-ESCROW-001';
END;

-- 11.3 Giao dịch Sổ cái: Giải ngân ký quỹ cho shipper 450,000 VND (ESCROW_RELEASE) cho ORD-TEST-006
-- Phí dịch vụ sàn: 10% (45,000 VND), Tiền Shipper nhận: 90% (405,000 VND)
IF NOT EXISTS (SELECT 1 FROM ledger_transactions WHERE transaction_code = 'TXN-RELEASE-001')
    INSERT INTO ledger_transactions (
        transaction_code, type, status, idempotency_key, order_id,
        ref_type, ref_id, amount, created_by, description, created_at
    )
    SELECT
        'TXN-RELEASE-001', 'ESCROW_RELEASE', 'COMPLETED', 'IDEMP-RELEASE-001-20261014', o.id,
        'ORDER', o.order_code, 450000, o.courier_id,
        N'Giải ngân ký quỹ đơn giao thành công ORD-TEST-006 cho shipper', SYSUTCDATETIME()
    FROM orders o WHERE o.order_code = 'ORD-TEST-006';

-- Bút toán kép cho TXN-RELEASE-001:
-- Nợ TK 2101 (Ký quỹ giam giữ): 450,000
-- Có TK 2002 (Ví khả dụng Shipper): 405,000
-- Có TK 4001 (Doanh thu phí dịch vụ sàn FPTPost 10%): 45,000
-- Tổng Nợ (450,000) = Tổng Có (405,000 + 45,000) -> Cân bằng tuyệt đối!
IF NOT EXISTS (SELECT 1 FROM ledger_entries le JOIN ledger_transactions lt ON le.transaction_id = lt.id WHERE lt.transaction_code = 'TXN-RELEASE-001')
BEGIN
    INSERT INTO ledger_entries (transaction_id, account_code, account_type, debit, credit, created_at)
    SELECT lt.id, '2101_ESCROW_HOLDING', 'LIABILITY', 450000, 0, SYSUTCDATETIME()
    FROM ledger_transactions lt WHERE lt.transaction_code = 'TXN-RELEASE-001';

    INSERT INTO ledger_entries (transaction_id, account_code, account_type, debit, credit, created_at)
    SELECT lt.id, '2002_COURIER_WALLET_AVAILABLE', 'LIABILITY', 0, 405000, SYSUTCDATETIME()
    FROM ledger_transactions lt WHERE lt.transaction_code = 'TXN-RELEASE-001';

    INSERT INTO ledger_entries (transaction_id, account_code, account_type, debit, credit, created_at)
    SELECT lt.id, '4001_PLATFORM_REVENUE', 'REVENUE', 0, 45000, SYSUTCDATETIME()
    FROM ledger_transactions lt WHERE lt.transaction_code = 'TXN-RELEASE-001';
END;

-- 11.4 Giao dịch Sổ cái: Hoàn tiền ký quỹ cho người gửi 200,000 VND (ESCROW_REFUND) cho ORD-TEST-007
IF NOT EXISTS (SELECT 1 FROM ledger_transactions WHERE transaction_code = 'TXN-REFUND-001')
    INSERT INTO ledger_transactions (
        transaction_code, type, status, idempotency_key, order_id,
        ref_type, ref_id, amount, created_by, description, created_at
    )
    SELECT
        'TXN-REFUND-001', 'ESCROW_REFUND', 'COMPLETED', 'IDEMP-REFUND-001-20261014', o.id,
        'ORDER', o.order_code, 200000, o.sender_id,
        N'Hoàn tiền ký quỹ do hủy đơn hàng ORD-TEST-007 về ví người gửi', SYSUTCDATETIME()
    FROM orders o WHERE o.order_code = 'ORD-TEST-007';

-- Bút toán kép cho TXN-REFUND-001:
-- Nợ TK 2101 (Ký quỹ giam giữ): 200,000 / Có TK 2001 (Ví khả dụng người gửi): 200,000
IF NOT EXISTS (SELECT 1 FROM ledger_entries le JOIN ledger_transactions lt ON le.transaction_id = lt.id WHERE lt.transaction_code = 'TXN-REFUND-001')
BEGIN
    INSERT INTO ledger_entries (transaction_id, account_code, account_type, debit, credit, created_at)
    SELECT lt.id, '2101_ESCROW_HOLDING', 'LIABILITY', 200000, 0, SYSUTCDATETIME()
    FROM ledger_transactions lt WHERE lt.transaction_code = 'TXN-REFUND-001';

    INSERT INTO ledger_entries (transaction_id, account_code, account_type, debit, credit, created_at)
    SELECT lt.id, '2001_USER_WALLET_AVAILABLE', 'LIABILITY', 0, 200000, SYSUTCDATETIME()
    FROM ledger_transactions lt WHERE lt.transaction_code = 'TXN-REFUND-001';
END;


-- ----------------------------------------------------------------------------------------------
-- 12. BẢNG DANH MỤC HÀNG HÓA MẪU PRODUCTS (DEMO)
-- ----------------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM products WHERE name = N'Hộp Quà Lưu Niệm FPT Edu')
    INSERT INTO products (id, name, description, price, category, stock, status, created_at, updated_at)
    VALUES (
        'B0000000-0000-0000-0000-000000000001', N'Hộp Quà Lưu Niệm FPT Edu',
        N'Bình giữ nhiệt, sổ tay bìa da, bút khắc laser FPT', 250000.00, N'Quà Tặng', 100, 'ACTIVE',
        SYSUTCDATETIME(), SYSUTCDATETIME()
    );

IF NOT EXISTS (SELECT 1 FROM products WHERE name = N'Áo Khoác Gió FPTPost Rider')
    INSERT INTO products (id, name, description, price, category, stock, status, created_at, updated_at)
    VALUES (
        'B0000000-0000-0000-0000-000000000002', N'Áo Khoác Gió FPTPost Rider',
        N'Áo khoác gió phản quang chống nước cho tài xế giao hàng', 350000.00, N'Đồng Phục', 50, 'ACTIVE',
        SYSUTCDATETIME(), SYSUTCDATETIME()
    );

IF NOT EXISTS (SELECT 1 FROM products WHERE name = N'Túi Giao Hàng Giữ Nhiệt 48L')
    INSERT INTO products (id, name, description, price, category, stock, status, created_at, updated_at)
    VALUES (
        'B0000000-0000-0000-0000-000000000003', N'Túi Giao Hàng Giữ Nhiệt 48L',
        N'Túi cách nhiệt 3 lớp gắn xe máy chuyên dụng bưu tá', 420000.00, N'Thiết Bị', 30, 'ACTIVE',
        SYSUTCDATETIME(), SYSUTCDATETIME()
    );


-- ----------------------------------------------------------------------------------------------
-- 13. BẢNG DANH SÁCH ĐEN BLACKLIST (DB-043)
-- ----------------------------------------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM blacklist WHERE kind = 'PHONE' AND value_hash = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')
    INSERT INTO blacklist (kind, value_hash, reason, created_by, created_at)
    SELECT 'PHONE', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
           N'Số điện thoại nằm trong danh sách đen lừa đảo bùng hàng', u.id, SYSUTCDATETIME()
    FROM users u WHERE u.email = 'admin@fptpost.vn';

PRINT '--> [FPTPOST] Data seed script completed successfully!';
