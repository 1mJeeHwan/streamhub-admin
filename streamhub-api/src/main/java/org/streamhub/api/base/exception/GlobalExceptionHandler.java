package org.streamhub.api.base.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResultDTO<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        return ResponseEntity.status(ResultCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(ResultDTO.error(ResultCode.METHOD_NOT_ALLOWED));
    }

    /**
     * Translates a unique/foreign-key constraint violation into a generic invalid-request
     * response. The raw SQL and constraint name stay in the server log only — never echoed to
     * the client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResultDTO<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ResultCode.INVALID_PARAMETER.getHttpStatus())
                .body(ResultDTO.error(ResultCode.INVALID_PARAMETER));
    }

    /**
     * Translates an optimistic-lock failure (a concurrent update lost the {@code @Version} race)
     * into an invalid-request response. The conflicting entity details stay in the server log only.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ResultDTO<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking failure: {}", ex.getMessage());
        return ResponseEntity.status(ResultCode.INVALID_PARAMETER.getHttpStatus())
                .body(ResultDTO.error(ResultCode.INVALID_PARAMETER));
    }

    /** Translates a malformed/unreadable request body into an invalid-request response. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResultDTO<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.status(ResultCode.INVALID_PARAMETER.getHttpStatus())
                .body(ResultDTO.error(ResultCode.INVALID_PARAMETER));
    }

    /** Translates a path/query parameter with the wrong type into an invalid-request response. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResultDTO<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Argument type mismatch: {}", ex.getMessage());
        return ResponseEntity.status(ResultCode.INVALID_PARAMETER.getHttpStatus())
                .body(ResultDTO.error(ResultCode.INVALID_PARAMETER));
    }

    /** Translates a missing required request parameter into an invalid-request response. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResultDTO<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getMessage());
        return ResponseEntity.status(ResultCode.INVALID_PARAMETER.getHttpStatus())
                .body(ResultDTO.error(ResultCode.INVALID_PARAMETER));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultDTO<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(ResultCode.INTERNAL_ERROR.getHttpStatus())
                .body(ResultDTO.error(ResultCode.INTERNAL_ERROR));
    }
}
