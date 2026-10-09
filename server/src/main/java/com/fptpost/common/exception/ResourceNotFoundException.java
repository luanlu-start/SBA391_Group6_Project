package com.fptpost.common.exception;

public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(GlobalErrorCode.RESOURCE_NOT_FOUND,
              String.format("%s with %s = '%s' not found", resourceName, fieldName, fieldValue));
    }

    public ResourceNotFoundException(String message) {
        super(GlobalErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
