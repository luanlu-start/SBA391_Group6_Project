package com.group6.project.common.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.data.domain.Sort;

@Data
public class PageRequestDto {
    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 12;

    @NotNull
    private Sort.Direction direction = Sort.Direction.DESC;
}
