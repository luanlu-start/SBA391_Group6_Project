package com.fptpost.modules.identity.service;

import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.dto.RegistrationPoliciesResponse;
import com.fptpost.modules.identity.dto.RegistrationPoliciesResponse.Policy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service @RequiredArgsConstructor
public class RegistrationPoliciesService {
    private final RegistrationProperties properties;

    public RegistrationPoliciesResponse getPolicies() {
        return new RegistrationPoliciesResponse(
                new Policy(properties.getTermsVersion(), "Điều khoản sử dụng", List.of(
                        "FPTPost kết nối người gửi hàng và người giao hàng tiện đường. Người sử dụng phải đủ 18 tuổi và cung cấp thông tin chính xác.",
                        "Không sử dụng dịch vụ để giao hàng cấm hoặc thực hiện hành vi gian lận. Tài khoản chờ xác thực chưa được tạo đơn, nhận đơn hoặc giao dịch ví.",
                        "Bản điều khoản này dùng cho môi trường phát triển; nội dung chính thức cần được nhóm phê duyệt trước khi cung cấp dịch vụ.")),
                new Policy(properties.getPrivacyVersion(), "Chính sách dữ liệu cá nhân", List.of(
                        "Khi đăng ký, FPTPost thu thập họ tên, email và số điện thoại để tạo tài khoản và gửi mã xác thực.",
                        "Mật khẩu được lưu dưới dạng băm. Hệ thống lưu phiên bản chính sách, thời điểm đồng ý và địa chỉ IP làm bằng chứng đồng ý.",
                        "Thông tin KYC và vị trí được thu thập ở các bước riêng. Chính sách chính thức về truy cập, lưu giữ và quyền dữ liệu cần được phê duyệt trước khi cung cấp dịch vụ.")),
                properties.getPasswordMinLength());
    }
}
