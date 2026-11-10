package com.fptpost.modules.deliveryreturn.exception;

import com.fptpost.common.exception.AppException;

/** Only invalid-code counters are committed on this exception; business failures still roll back. */
public class ReturnCodeRejectedException extends AppException {
    public ReturnCodeRejectedException() { super(DeliveryReturnErrorCode.RETURN_CODE_INVALID); }
}
