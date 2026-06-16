package org.streamhub.api.base.exception;

import lombok.Getter;
import org.streamhub.api.base.response.ResultCode;

/**
 * Domain exception carrying a {@link ResultCode}.
 *
 * <p>Thrown by services and translated to an HTTP response by
 * {@link GlobalExceptionHandler}.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ResultCode resultCode;

    public ApiException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public ApiException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
