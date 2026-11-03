package com.hzmwmt.types.exception;

import com.hzmwmt.types.enums.ResponseCode;

/**
 * Signals that a server-side request quota has been exhausted.
 *
 * <p>The business response code remains compatible with existing clients while
 * the global HTTP exception handler can reliably distinguish rate limiting
 * (HTTP 429) from an ordinary repeated/conflicting operation (HTTP 409).</p>
 */
public class RateLimitException extends AppException {

    private static final long serialVersionUID = 2751660166035116865L;

    public RateLimitException(String message) {
        super(ResponseCode.REPEATED_OPERATION_EXCEPTION.getCode(), message);
    }
}
