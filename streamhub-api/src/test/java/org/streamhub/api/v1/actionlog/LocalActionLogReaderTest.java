package org.streamhub.api.v1.actionlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.v1.actionlog.dto.ActionLogItem;
import org.streamhub.api.v1.actionlog.dto.ActionLogSearchRequest;
import org.streamhub.api.v1.actionlog.mapper.ActionLogMapper;

/**
 * Local reader: blank filters collapse to null (so the MyBatis WHERE drops them), offset/size derive
 * from the request, and the mapper results are wrapped in a {@link ResInfinityList}.
 */
@ExtendWith(MockitoExtension.class)
class LocalActionLogReaderTest {

    @Mock private ActionLogMapper actionLogMapper;

    @Test
    void list_blankFiltersBecomeNull_andPaginates() {
        ActionLogItem item = new ActionLogItem();
        item.setAction("MEMBER_APPROVE");
        when(actionLogMapper.selectList(isNull(), isNull(), eq(0), eq(10))).thenReturn(List.of(item));
        when(actionLogMapper.countList(isNull(), isNull())).thenReturn(1L);

        ResInfinityList<ActionLogItem> result =
                new LocalActionLogReader(actionLogMapper).list(new ActionLogSearchRequest(0, 10, "  ", null));

        assertThat(result.getContents()).hasSize(1);
        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getTotalPage()).isEqualTo(1);
    }
}
