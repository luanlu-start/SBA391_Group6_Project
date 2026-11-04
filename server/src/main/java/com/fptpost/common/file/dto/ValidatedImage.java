package com.fptpost.common.file.dto;

public record ValidatedImage(byte[] bytes, String mediaType) {
    @Override public String toString() { return "ValidatedImage[redacted]"; }
}
