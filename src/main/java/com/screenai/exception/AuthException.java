package com.screenai.exception;

import com.screenai.dto.ErrorResponse.ErrorCode;

/**
 * Custom exception for authentication and authorization errors.
 */
public class AuthException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String details;

    public AuthException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public AuthException(ErrorCode errorCode, String details) {
        super(errorCode.getMessage() + ": " + details);
        this.errorCode = errorCode;
        this.details = details;
    }

    public AuthException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.details = null;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getDetails() {
        return details;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
