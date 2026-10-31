package com.fptpost.modules.identity.exception;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.identity.dto.response.OtpChallengeResponse;
import lombok.Getter;

/**
 * Expected Identity outcomes, optionally carrying a Retry-After duration.
 * Login, refresh and OTP verification explicitly commit these outcomes so failed
 * attempts and replay revocations persist. Other AppExceptions still roll back.
 */
@Getter
public class IdentityException extends AppException {
    private final long retryAfterSeconds;
    private final OtpChallengeResponse verification;

    public IdentityException(IdentityErrorCode code) {
        this(code, 0);
    }

    public IdentityException(IdentityErrorCode code, long retryAfterSeconds) {
        super(code);
        this.retryAfterSeconds = retryAfterSeconds;
        this.verification = null;
    }

    public IdentityException(IdentityErrorCode code, OtpChallengeResponse verification) {
        super(code);
        this.retryAfterSeconds = 0;
        this.verification = verification;
    }
}
