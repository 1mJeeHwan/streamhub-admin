package org.streamhub.api.v1.actionlog;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.v1.actionlog.dto.ActionLogItem;
import org.streamhub.api.v1.actionlog.dto.ActionLogSearchRequest;
import org.streamhub.api.v1.actionlog.mapper.ActionLogMapper;

/**
 * Default audit-log reader — paginated, filterable list straight from the monolith's own
 * {@code ACTION_LOG} table (MyBatis). Active unless {@code app.actionlog.source=remote}, in which
 * case {@link RemoteActionLogReader} calls the extracted audit service instead.
 */
@Service
@ConditionalOnProperty(name = "app.actionlog.source", havingValue = "local", matchIfMissing = true)
public class LocalActionLogReader implements ActionLogReader {

    private final ActionLogMapper actionLogMapper;

    public LocalActionLogReader(ActionLogMapper actionLogMapper) {
        this.actionLogMapper = actionLogMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ResInfinityList<ActionLogItem> list(ActionLogSearchRequest request) {
        String action = blankToNull(request.action());
        String keyword = blankToNull(request.keyword());
        int size = request.pageSizeOrDefault();
        List<ActionLogItem> contents = actionLogMapper.selectList(action, keyword, request.offset(), size);
        long total = actionLogMapper.countList(action, keyword);
        return ResInfinityList.of(contents, total, size);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
