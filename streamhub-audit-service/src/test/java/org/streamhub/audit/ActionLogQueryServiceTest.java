package org.streamhub.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Read-side unit tests: blank filters collapse to null (ignored by the query), defaults apply when
 * page/size are missing, and the JPA {@link Page} maps cleanly to the {@link ActionLogPage} contract.
 */
@ExtendWith(MockitoExtension.class)
class ActionLogQueryServiceTest {

    @Mock private ActionLogRepository repository;

    private ActionLogQueryService service() {
        return new ActionLogQueryService(repository);
    }

    private ActionLog entity() {
        return ActionLog.builder()
                .adminId(7L).adminName("관리자").action("MEMBER_APPROVE")
                .targetType("MEMBER").targetId("42").detail("회원 승인").ip("1.2.3.4")
                .build();
    }

    @Test
    void list_mapsPage_andAppliesDefaults() {
        Page<ActionLog> page = new PageImpl<>(List.of(entity()), PageRequest.of(0, 10), 1);
        when(repository.search(isNull(), isNull(), eq(PageRequest.of(0, 10)))).thenReturn(page);

        ActionLogPage result = service().list(null, null, "  ", null);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.totalPage()).isEqualTo(1);
        assertThat(result.contents()).hasSize(1);
        assertThat(result.contents().get(0).action()).isEqualTo("MEMBER_APPROVE");
        assertThat(result.contents().get(0).adminName()).isEqualTo("관리자");
    }

    @Test
    void list_passesFiltersAndPaging_through() {
        when(repository.search(eq("LOGIN"), eq("kim"), eq(PageRequest.of(2, 25))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 25), 0));

        service().list(2, 25, "LOGIN", "kim");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(eq("LOGIN"), eq("kim"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
    }
}
