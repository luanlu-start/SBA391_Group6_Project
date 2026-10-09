package com.fptpost.modules.identity.exception;

import com.fptpost.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter @RequiredArgsConstructor
public enum IdentityErrorCode implements ErrorCode {
    EMAIL_ALREADY_USED(3001, "Email đã được sử dụng. Bạn có thể đăng nhập hoặc đặt lại mật khẩu.", HttpStatus.CONFLICT),
    PHONE_ALREADY_USED(3002, "Số điện thoại đã được sử dụng.", HttpStatus.CONFLICT),
    PASSWORD_INVALID(3003, "Mật khẩu phải có chữ hoa, chữ thường, chữ số; không trùng email/SĐT và tối đa 72 byte.", HttpStatus.BAD_REQUEST),
    POLICY_CHANGED(3004, "Chính sách đã thay đổi. Vui lòng tải lại và đọc phiên bản mới.", HttpStatus.CONFLICT),
    REGISTRATION_NOT_ALLOWED(3005, "Không thể đăng ký với thông tin này.", HttpStatus.FORBIDDEN),
    OTP_DELIVERY_FAILED(3006, "Chưa thể gửi email xác thực. Vui lòng thử lại sau.", HttpStatus.SERVICE_UNAVAILABLE),
    REGISTRATION_CONFLICT(3007, "Thông tin đăng ký đã được sử dụng. Vui lòng kiểm tra lại.", HttpStatus.CONFLICT),
    AUTH_RATE_LIMITED(3008, "Bạn gửi quá nhiều yêu cầu. Vui lòng thử lại sau một phút.", HttpStatus.TOO_MANY_REQUESTS);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}
