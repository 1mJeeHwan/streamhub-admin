package org.streamhub.api.base.response;

import lombok.Getter;

/**
 * Standard API response wrapper used by every endpoint.
 *
 * @param <T> type of the payload carried in {@code resultObject}
 */
@Getter
public class ResultDTO<T> {

    private final String resultCode;
    private final String resultMessage;
    private final T resultObject;

    private ResultDTO(String resultCode, String resultMessage, T resultObject) {
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
        this.resultObject = resultObject;
    }

    /** Success with payload. */
    public static <T> ResultDTO<T> ok(T resultObject) {
        return new ResultDTO<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), resultObject);
    }

    /** Success without payload. */
    public static ResultDTO<Void> ok() {
        return new ResultDTO<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /** Failure from a known result code. */
    public static <T> ResultDTO<T> error(ResultCode resultCode) {
        return new ResultDTO<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** Failure from a known result code with an overriding message. */
    public static <T> ResultDTO<T> error(ResultCode resultCode, String message) {
        return new ResultDTO<>(resultCode.getCode(), message, null);
    }
}
