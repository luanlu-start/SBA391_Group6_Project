package com.group6.project.common.exception;

public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(ErrorCode.RESOURCE_NOT_FOUND, 
              String.format("%s with %s = '%s' not found", resourceName, fieldName, fieldValue));
    }

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
