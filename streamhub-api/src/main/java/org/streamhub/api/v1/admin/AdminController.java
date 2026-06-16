package org.streamhub.api.v1.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.admin.dto.MeResponse;
import org.streamhub.api.v1.admin.entity.AdminAccount;
import org.streamhub.api.v1.admin.repository.AdminAccountRepository;

/**
 * Operator account endpoints. {@code /v1/admin/me} doubles as the protected-route probe
 * proving JWT auth works end-to-end.
 */
@Tag(name = "Admin", description = "운영자 계정")
@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final AdminAccountRepository adminRepository;

    public AdminController(AdminAccountRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @Operation(summary = "내 정보", description = "현재 인증된 운영자 정보를 반환한다.")
    @GetMapping("/me")
    public ResultDTO<MeResponse> me(@AuthenticationPrincipal AdminPrincipal principal) {
        AdminAccount admin = adminRepository.findById(principal.id())
                .orElseThrow(() -> new ApiException(ResultCode.UNAUTHORIZED));
        return ResultDTO.ok(MeResponse.from(admin));
    }
}
