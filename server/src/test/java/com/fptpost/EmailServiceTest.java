package com.fptpost;

import com.fptpost.common.mail.config.MailProperties;
import com.fptpost.common.mail.config.MailTemplateConfig;
import com.fptpost.common.mail.dto.EmailRequest;
import com.fptpost.common.mail.enums.EmailTemplateType;
import com.fptpost.common.mail.exception.EmailDeliveryException;
import com.fptpost.common.mail.service.EmailService;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.exceptions.TemplateInputException;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmailServiceTest {
    private JavaMailSender mailSender;
    private MailProperties mailProperties;
    private TemplateEngine templateEngine;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        mailProperties = new MailProperties();
        mailProperties.setFrom("mail@example.com");
        mailProperties.setFromName("FPTPost Test");
        templateEngine = new MailTemplateConfig().emailTemplateEngine(mailProperties);
        emailService = new EmailService(mailSender, templateEngine, mailProperties);
        when(mailSender.createMimeMessage()).thenAnswer(
                invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void rendersUtf8SubjectHtmlAndPlainTextAndEscapesUserContent() throws Exception {
        String fullName = "Nguyễn <img src=x onerror=alert(1)>";
        EmailRequest request = request(fullName);
        emailService.sendEmail(request);

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("FPTPost Test — Mã xác thực đăng ký");
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("mail@example.com");
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("FPTPost Test");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("recipient@example.com");
        String html = content(message, "text/html");
        String text = content(message, "text/plain");
        assertThat(html).contains("012345", "FPTPost Test", "&lt;img", "7", "phút")
                .doesNotContain("<img src=x", "th:text", "${", "[(${", "123456");
        assertThat(text).contains(fullName, "012345", "Mã có hiệu lực 7 phút", "FPTPost Test")
                .doesNotContain("[(${", "123456");
        assertThat(request.toString()).doesNotContain("012345", "recipient@example.com", fullName);
    }

    @Test
    void rejectsMissingTemplateVariablesBeforeSending() {
        EmailRequest request = request("Nguyễn Văn An");
        request.setVariables(Map.of("fullName", "Nguyễn Văn An"));
        assertThatThrownBy(() -> emailService.sendEmail(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mailSender);
    }

    @Test
    void smtpFailureDoesNotRetainProviderSecrets() {
        doThrow(new MailSendException("recipient@example.com OTP 012345")).when(mailSender)
                .send(any(MimeMessage.class));
        assertThatThrownBy(() -> emailService.sendEmail(request("Nguyễn Văn An")))
                .isInstanceOf(EmailDeliveryException.class).hasNoCause()
                .hasMessage("Unable to prepare or send email");
    }

    @Test
    void templateFailurePreventsDeliveryAndDoesNotRetainTemplateData() {
        TemplateEngine brokenEngine = mock(TemplateEngine.class);
        when(brokenEngine.process(anyString(), any(org.thymeleaf.context.IContext.class)))
                .thenThrow(new TemplateInputException("secret template data OTP 012345"));
        EmailService service = new EmailService(mailSender, brokenEngine, mailProperties);
        assertThatThrownBy(() -> service.sendEmail(request("Nguyễn Văn An")))
                .isInstanceOf(EmailDeliveryException.class).hasNoCause()
                .hasMessage("Unable to prepare or send email");
        verifyNoInteractions(mailSender);
    }

    private EmailRequest request(String fullName) {
        return EmailRequest.builder()
                .email("recipient@example.com")
                .templateType(EmailTemplateType.VERIFY_EMAIL)
                .variables(Map.of("fullName", fullName, "otp", "012345", "expirationMinutes", 7))
                .build();
    }

    private String content(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return (String) part.getContent();
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String content = content(multipart.getBodyPart(i), mimeType);
                if (content != null) {
                    return content;
                }
            }
        }
        return null;
    }
}
