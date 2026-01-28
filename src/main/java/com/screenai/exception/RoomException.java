package com.screenai.exception;

import com.screenai.dto.ErrorResponse.ErrorCode;

/**
 * Custom exception for room-related errors.
 */
public class RoomException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String details;

    public RoomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public RoomException(ErrorCode errorCode, String details) {
        super(errorCode.getMessage() + ": " + details);
        this.errorCode = errorCode;
        this.details = details;
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
