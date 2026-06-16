package org.streamhub.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.auth.dto.LoginRequest;
import org.streamhub.api.auth.dto.RefreshRequest;
import org.streamhub.api.auth.dto.TokenResponse;
import org.streamhub.api.base.response.ResultDTO;

/**
 * Authentication endpoints (public). Issues and rotates JWT token pairs.
 */
@Tag(name = "Auth", description = "인증 (로그인/토큰갱신/로그아웃)")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "로그인", description = "아이디/비밀번호로 access/refresh 토큰을 발급한다.")
    @PostMapping("/login")
    public ResultDTO<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResultDTO.ok(authService.login(request));
    }

    @Operation(summary = "토큰 갱신", description = "refresh 토큰으로 새 토큰 쌍을 발급한다.")
    @PostMapping("/refresh")
    public ResultDTO<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResultDTO.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "로그아웃", description = "refresh 토큰을 무효화한다.")
    @PostMapping("/logout")
    public ResultDTO<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResultDTO.ok();
    }
}
