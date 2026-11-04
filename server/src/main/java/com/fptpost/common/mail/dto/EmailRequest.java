package com.fptpost.common.mail.dto;

import com.fptpost.common.mail.enums.EmailTemplateType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailRequest {
    @NotBlank
    @Email
    String email;

    @NotNull
    EmailTemplateType templateType;

    @NotNull
    @Builder.Default
    Map<String, Object> variables = Map.of();

    @Override
    public String toString() {
        return "EmailRequest[redacted]";
    }
}
