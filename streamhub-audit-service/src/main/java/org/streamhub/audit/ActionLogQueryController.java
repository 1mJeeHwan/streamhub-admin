package org.streamhub.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The audit service's read API. The monolith's '감사 로그' screen calls this instead of querying the
 * audit table directly — the data is owned here (DB-per-service). An internal service endpoint: the
 * trust boundary (operator authz) stays at the monolith's controller / the API gateway; this service
 * is reached server-to-server, not from the public internet.
 */
@RestController
public class ActionLogQueryController {

    private final ActionLogQueryService queryService;

    public ActionLogQueryController(ActionLogQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/v1/action-logs")
    public ActionLogPage list(@RequestParam(required = false) Integer pageNumber,
                              @RequestParam(required = false) Integer pageSize,
                              @RequestParam(required = false) String action,
                              @RequestParam(required = false) String keyword) {
        return queryService.list(pageNumber, pageSize, action, keyword);
    }
}
