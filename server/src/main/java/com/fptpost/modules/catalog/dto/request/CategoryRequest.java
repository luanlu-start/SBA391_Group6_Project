package com.fptpost.modules.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "Mã loại hàng không được để trống")
    @Pattern(regexp = "^[A-Za-z0-9_]{2,50}$", message = "Mã loại hàng chỉ chứa chữ hoa, số hoặc gạch dưới (2-50 ký tự)")
    private String code;

    @NotBlank(message = "Tên loại hàng không được để trống")
    @Size(max = 100, message = "Tên loại hàng không vượt quá 100 ký tự")
    private String name;

    @Size(max = 500, message = "Mô tả không vượt quá 500 ký tự")
    private String description;

    @Builder.Default
    private Boolean isProhibited = false;

    @Builder.Default
    private Boolean requiresReview = false;

    @Builder.Default
    private Boolean active = true;
}
