package org.streamhub.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the audit store: filterable, paginated lookups over this service's own data. */
@Service
public class ActionLogQueryService {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ActionLogRepository repository;

    public ActionLogQueryService(ActionLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ActionLogPage list(Integer pageNumber, Integer pageSize, String action, String keyword) {
        int page = pageNumber == null || pageNumber < 0 ? 0 : pageNumber;
        int size = pageSize == null || pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize;
        Page<ActionLog> result = repository.search(
                blankToNull(action), blankToNull(keyword), PageRequest.of(page, size));
        return new ActionLogPage(
                result.map(ActionLogView::from).getContent(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
