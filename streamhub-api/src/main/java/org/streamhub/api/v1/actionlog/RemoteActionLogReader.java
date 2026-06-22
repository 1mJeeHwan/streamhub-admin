package org.streamhub.api.v1.actionlog;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.actionlog.dto.ActionLogItem;
import org.streamhub.api.v1.actionlog.dto.ActionLogSearchRequest;

/**
 * MSA audit-log reader — fetches the page from the extracted {@code streamhub-audit-service}, which
 * owns the audit data in its own schema (DB-per-service). Active when {@code app.actionlog.source=remote}
 * (the producer-only / MSA mode where this app no longer holds the {@code ACTION_LOG} table).
 *
 * <p>A server-to-server call: the operator authorization has already been enforced by the
 * {@link ActionLogController} (and, in the full topology, the API gateway). If the audit service is
 * unreachable the read fails with a clear {@link ApiException} rather than a raw 500.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.actionlog.source", havingValue = "remote")
public class RemoteActionLogReader implements ActionLogReader {

    private final RestClient restClient;

    public RemoteActionLogReader(RestClient.Builder restClientBuilder,
                                 @Value("${app.actionlog.audit-service-url}") String auditServiceUrl) {
        this.restClient = restClientBuilder.baseUrl(auditServiceUrl).build();
    }

    @Override
    public ResInfinityList<ActionLogItem> list(ActionLogSearchRequest request) {
        int size = request.pageSizeOrDefault();
        try {
            RemotePage page = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/action-logs")
                            .queryParam("pageNumber", request.pageNumber())
                            .queryParam("pageSize", size)
                            .queryParamIfPresent("action", java.util.Optional.ofNullable(request.action()))
                            .queryParamIfPresent("keyword", java.util.Optional.ofNullable(request.keyword()))
                            .build())
                    .retrieve()
                    .body(RemotePage.class);
            List<ActionLogItem> contents = page != null && page.contents() != null ? page.contents() : List.of();
            long total = page != null ? page.totalCount() : 0L;
            return ResInfinityList.of(contents, total, size);
        } catch (RestClientException e) {
            log.warn("Audit service read failed: {}", e.getMessage());
            throw new ApiException(ResultCode.INTERNAL_ERROR, "감사 로그 서비스에 연결할 수 없습니다");
        }
    }

    /** Mirrors the audit service's {@code ActionLogPage} JSON; {@link ActionLogItem} maps field-for-field. */
    private record RemotePage(List<ActionLogItem> contents, long totalCount, int totalPage) {
    }
}
