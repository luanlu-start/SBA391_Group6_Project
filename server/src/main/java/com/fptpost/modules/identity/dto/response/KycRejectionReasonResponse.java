package com.fptpost.modules.identity.dto.response;

public record KycRejectionReasonResponse(String code, String label, boolean noteRequired) { }
