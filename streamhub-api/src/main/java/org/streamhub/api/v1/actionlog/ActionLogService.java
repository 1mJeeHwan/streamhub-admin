package org.streamhub.api.v1.actionlog;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.v1.actionlog.dto.ActionLogItem;
import org.streamhub.api.v1.actionlog.dto.ActionLogSearchRequest;
import org.streamhub.api.v1.actionlog.mapper.ActionLogMapper;

/** Read side of the audit log: paginated, filterable list. */
@Service
public class ActionLogService {

    private final ActionLogMapper actionLogMapper;

    public ActionLogService(ActionLogMapper actionLogMapper) {
        this.actionLogMapper = actionLogMapper;
    }

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
