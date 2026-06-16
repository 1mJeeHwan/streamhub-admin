package org.streamhub.api.v1.pub.auth.dto;

import java.time.LocalDateTime;

/** Public-facing member profile (no password). */
public record MemberInfo(
        Long id,
        String name,
        String email,
        String phone,
        String churchName,
        LocalDateTime createdAt) {
}
