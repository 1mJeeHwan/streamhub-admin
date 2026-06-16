package org.streamhub.api.base.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.response.ResultDTO;

/**
 * Translates exceptions into {@link ResultDTO} responses with the appropriate HTTP status.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ResultDTO<Void>> handleApiException(ApiException ex) {
        ResultCode code = ex.getResultCode();
        log.warn("ApiException: {} - {}", code.getCode(), ex.getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ResultDTO.error(code, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResultDTO<Void>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : ResultCode.INVALID_PARAMETER.getMessage();
        return ResponseEntity.status(ResultCode.INVALID_PARAMETER.getHttpStatus())
                .body(ResultDTO.error(ResultCode.INVALID_PARAMETER, message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResultDTO<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ResultCode.FORBIDDEN.getHttpStatus())
                .body(ResultDTO.error(ResultCode.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultDTO<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(ResultCode.INTERNAL_ERROR.getHttpStatus())
                .body(ResultDTO.error(ResultCode.INTERNAL_ERROR));
    }
}
