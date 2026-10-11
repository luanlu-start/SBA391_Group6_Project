package com.fptpost.common.mail.service;

import com.fptpost.common.mail.config.MailProperties;
import com.fptpost.common.mail.dto.EmailRequest;
import com.fptpost.common.mail.exception.EmailDeliveryException;
import jakarta.mail.MessagingException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateEngineException;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EmailService {
    JavaMailSender mailSender;
    TemplateEngine emailTemplateEngine;
    MailProperties mailProperties;

    public void sendEmail(EmailRequest request) {
        validateRequest(request);
        Context context = new Context(Locale.forLanguageTag("vi"));
        context.setVariables(request.getVariables());
        context.setVariable("appName", mailProperties.getFromName());
        String templateName = request.getTemplateType().getTemplateName();

        try {
            String subject = emailTemplateEngine.process("subject/" + templateName, context).strip();
            if (subject.isBlank() || subject.contains("\r") || subject.contains("\n")) {
                throw new EmailDeliveryException();
            }
            String text = emailTemplateEngine.process("text/" + templateName, context);
            String html = emailTemplateEngine.process("html/" + templateName, context);

            var message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setValidateAddresses(true);
            helper.setFrom(mailProperties.getFrom(), mailProperties.getFromName());
            helper.setTo(request.getEmail());
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException | TemplateEngineException exception) {
            throw new EmailDeliveryException();
        }
    }

    private void validateRequest(EmailRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()
                || request.getTemplateType() == null || request.getVariables() == null) {
            throw new IllegalArgumentException("Email recipient, template and variables are required");
        }
        for (String variable : request.getTemplateType().getRequiredVariables()) {
            Object value = request.getVariables().get(variable);
            if (value == null || value instanceof String text && text.isBlank()) {
                throw new IllegalArgumentException("Missing email template variable: " + variable);
            }
        }
    }
}
