package com.fptpost.modules.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalImportResultResponse {
    private int processed;
    private int paid;
    private int failed;
    private List<WithdrawalImportErrorResponse> errors;
}
