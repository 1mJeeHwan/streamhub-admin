package org.streamhub.api.base.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Code-defined permission matrix mapping a role to its {@code resource:action} permissions.
 *
 * <p>Permissions are granted as Spring authorities at authentication time (see
 * {@code JwtTokenProvider}), so controllers can gate with
 * {@code @PreAuthorize("hasAuthority('order:read')")} / {@code 'order:write'}. The role string is
 * also granted as an authority for backward compatibility with role-based checks.
 *
 * <ul>
 *   <li>{@code SYSTEM} — every operational and system resource, read + write.</li>
 *   <li>{@code CHURCH_MANAGER} — every operational resource read + write (data is church-scoped at
 *       the service layer); no system resources.</li>
 *   <li>{@code VIEWER} — every operational resource read-only; no writes, no system resources.</li>
 * </ul>
 */
public final class RolePermissions {

    private static final String READ = ":read";
    private static final String WRITE = ":write";

    /**
     * Operational resources a manager/viewer may touch (one per admin domain/page). Mirrors the
     * controllers currently open to SYSTEM + CHURCH_MANAGER — VIEWER additionally gets read on these.
     */
    private static final List<String> OPERATIONAL = List.of(
            "order", "payment", "subscription", "member", "point", "content", "album", "goods",
            "coupon", "campaign", "banner", "board", "community", "worship", "visit", "store",
            "church", "sms", "notification", "analytics", "chat", "dashboard", "inquiry", "media",
            "announcement", "playlist");

    /**
     * System-only resources (SYSTEM gets read+write; MANAGER/VIEWER get nothing). Mirrors the
     * controllers currently restricted to SYSTEM: audit log, security monitoring, log archival,
     * admin accounts, donations, and subscription plans.
     */
    private static final List<String> SYSTEM_ONLY = List.of(
            "security", "actionlog", "logarchive", "admin", "donation", "subscription-plan");

    private RolePermissions() {
    }

    /** The permission set granted to {@code role} (empty for an unknown role). */
    public static Set<String> forRole(String role) {
        Set<String> perms = new HashSet<>();
        if (AuthoritiesConstants.SYSTEM.equals(role)) {
            addReadWrite(perms, OPERATIONAL);
            addReadWrite(perms, SYSTEM_ONLY);
        } else if (AuthoritiesConstants.CHURCH_MANAGER.equals(role)) {
            addReadWrite(perms, OPERATIONAL);
        } else if (AuthoritiesConstants.VIEWER.equals(role)) {
            for (String resource : OPERATIONAL) {
                perms.add(resource + READ);
            }
        }
        return perms;
    }

    private static void addReadWrite(Set<String> perms, List<String> resources) {
        for (String resource : resources) {
            perms.add(resource + READ);
            perms.add(resource + WRITE);
        }
    }
}
