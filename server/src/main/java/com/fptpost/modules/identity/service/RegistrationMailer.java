package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.config.RegistrationProperties;
import com.fptpost.modules.identity.exception.IdentityErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class RegistrationMailer {
    private final JavaMailSender mailSender;
    private final RegistrationProperties properties;

    public void sendOtp(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom());
        message.setTo(email);
        message.setSubject("FPTPost — Mã xác thực đăng ký");
        message.setText("Mã xác thực đăng ký FPTPost của bạn là: " + code
                + "\nMã có hiệu lực " + properties.getOtpTtlMinutes()
                + " phút. Không chia sẻ mã với người khác.\nNếu bạn không đăng ký, hãy bỏ qua email này.");
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            // Do not log the mail exception: providers can include recipient/body details.
            throw new AppException(IdentityErrorCode.OTP_DELIVERY_FAILED);
        }
    }
}
