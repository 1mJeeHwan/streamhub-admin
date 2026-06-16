package org.streamhub.api.v1.admin.dto;

import org.streamhub.api.v1.admin.entity.AdminAccount;

/**
 * Authenticated operator's identity, returned by {@code GET /v1/admin/me}.
 */
public record MeResponse(Long id, String loginId, String name, String role, Long churchId) {

    public static MeResponse from(AdminAccount admin) {
        return new MeResponse(
                admin.getId(),
                admin.getLoginId(),
                admin.getName(),
                admin.getRole().name(),
                admin.getChurchId());
    }
}
