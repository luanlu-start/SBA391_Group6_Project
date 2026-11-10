package com.fptpost.modules.payment.dto;

/** Payout batch file; returned as a download, never serialized as JSON. */
public record WithdrawalPayoutFile(String fileName, byte[] content, int count) {
}
