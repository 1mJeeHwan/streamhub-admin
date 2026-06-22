package org.streamhub.api.v1.actionlog;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.base.util.ClientIpResolver;
import org.streamhub.api.v1.actionlog.dto.ActionLogMessage;

/**
 * Builds admin-action events and hands them to the active {@link ActionLogTransport} (SQS or Kafka,
 * selected by {@code app.eventlog.transport}); the consumer ({@link ActionLogConsumer} /
 * {@link KafkaActionLogConsumer}) persists them. Publishing is best-effort — the transport swallows
 * failures, so a messaging problem never breaks the underlying business action.
 *
 * <p>The originating client IP is captured here (in the operator's request thread) via
 * {@link ClientIpResolver} and carried on the message, so the audit row records who acted and from
 * where. Off-request publishes (schedulers, startup) carry a null IP.
 */
@Component
public class ActionLogPublisher {

    private final ActionLogTransport transport;
    private final ClientIpResolver clientIpResolver;

    public ActionLogPublisher(ActionLogTransport transport, ClientIpResolver clientIpResolver) {
        this.transport = transport;
        this.clientIpResolver = clientIpResolver;
    }

    /** Publish using the currently authenticated operator (from the security context). */
    public void publish(String action, String targetType, String targetId, String detail) {
        AdminPrincipal principal = currentPrincipal();
        Long adminId = principal != null ? principal.id() : null;
        publishAs(adminId, null, action, targetType, targetId, detail);
    }

    /** Publish with an explicit operator (e.g. on login, before the security context exists). */
    public void publishAs(Long adminId, String adminName, String action,
                          String targetType, String targetId, String detail) {
        transport.send(new ActionLogMessage(
                adminId, adminName, action, targetType, targetId, detail, currentIp()));
    }

    private AdminPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminPrincipal p) {
            return p;
        }
        return null;
    }

    /** Resolves the current request's client IP, or null when published off a request thread. */
    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return clientIpResolver.resolve(request);
        }
        return null;
    }
}
