package org.streamhub.api.base.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Standard result codes returned in every {@link ResultDTO}.
 *
 * <p>Mirrors the real platform convention: a stable string code the client can branch on,
 * decoupled from the HTTP status.
 */
@Getter
public enum ResultCode {

    SUCCESS("0000", "성공", HttpStatus.OK),
    INVALID_PARAMETER("4000", "잘못된 요청입니다", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("4010", "인증이 필요합니다", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("4011", "토큰이 만료되었습니다", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("4012", "유효하지 않은 토큰입니다", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("4030", "권한이 없습니다", HttpStatus.FORBIDDEN),
    NOT_FOUND("4040", "대상을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("4050", "허용되지 않은 HTTP 메서드입니다", HttpStatus.METHOD_NOT_ALLOWED),
    LOGIN_FAILED("4100", "아이디 또는 비밀번호가 올바르지 않습니다", HttpStatus.UNAUTHORIZED),
    INTERNAL_ERROR("5000", "서버 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ResultCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
