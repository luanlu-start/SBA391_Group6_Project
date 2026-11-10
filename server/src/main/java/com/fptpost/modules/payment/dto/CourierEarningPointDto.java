package com.fptpost.modules.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierEarningPointDto {
    private String label;
    private LocalDate date;
    private int orderCount;
    private long grossAmount;
    private long commissionAmount;
    private long netAmount;
}
