package org.streamhub.api.base.security;

/**
 * Authenticated operator, derived from the access-token claims and set as the
 * Spring Security principal. Carries the church scope so data-access rules can
 * restrict CHURCH_MANAGER operators to their own church without a DB lookup.
 *
 * @param id       admin account id
 * @param role     {@link AuthoritiesConstants#SYSTEM} or {@link AuthoritiesConstants#CHURCH_MANAGER}
 * @param churchId owning church (null for SYSTEM)
 */
public record AdminPrincipal(Long id, String role, Long churchId) {

    public boolean isSystem() {
        return AuthoritiesConstants.SYSTEM.equals(role);
    }
}
