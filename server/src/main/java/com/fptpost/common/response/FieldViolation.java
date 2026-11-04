package com.fptpost.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FieldViolation {
    private final String field;
    private final String code;
    private final String message;
}
