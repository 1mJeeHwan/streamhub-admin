package org.streamhub.api.base.security;

/**
 * Authority (role) name constants used in {@code @PreAuthorize} expressions and JWT claims.
 */
public final class AuthoritiesConstants {

    /** System administrator — full access. */
    public static final String SYSTEM = "SYSTEM";

    /** Church manager — scoped to their own church's data. */
    public static final String CHURCH_MANAGER = "CHURCH_MANAGER";

    private AuthoritiesConstants() {
    }
}
