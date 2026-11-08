package com.fptpost.modules.relay.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoverIncidentRequest {

    @NotBlank(message = "Loại sự cố không được để trống")
    private String incidentType;

    @NotBlank(message = "Mô tả chi tiết sự cố không được để trống")
    private String description;

    private String photoUrl;
}
