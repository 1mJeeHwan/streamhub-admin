package org.streamhub.api.v1.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.member.dto.MemberDetail;
import org.streamhub.api.v1.member.dto.MemberSearchRequest;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.entity.UserStatus;
import org.streamhub.api.v1.member.mapper.MemberMapper;
import org.streamhub.api.v1.member.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private org.streamhub.api.v1.actionlog.ActionLogPublisher actionLogPublisher;

    @InjectMocks
    private MemberService memberService;

    private static final AdminPrincipal SYSTEM = new AdminPrincipal(1L, "SYSTEM", null);
    private static final AdminPrincipal MANAGER = new AdminPrincipal(2L, "CHURCH_MANAGER", 3L);

    private Member member(long id, long churchId, UserStatus status) {
        Member m = Member.builder()
                .churchId(churchId)
                .email("m" + id + "@test")
                .password("hash")
                .name("회원" + id)
                .phone("010")
                .userStatus(status)
                .liveYn("N")
                .createdAt(LocalDateTime.now())
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    @Test
    void list_asSystem_usesRequestedChurchFilter() {
        when(memberMapper.selectList(isNull(), isNull(), eq(5L), anyString(), anyInt(), anyInt())).thenReturn(List.of());
        when(memberMapper.countList(isNull(), isNull(), eq(5L))).thenReturn(0L);

        memberService.list(new MemberSearchRequest(0, 10, null, null, 5L, null, null), SYSTEM);

        verify(memberMapper).selectList(isNull(), isNull(), eq(5L), anyString(), eq(0), eq(10));
    }

    @Test
    void list_asChurchManager_forcesOwnChurch_ignoringRequest() {
        when(memberMapper.selectList(isNull(), isNull(), eq(3L), anyString(), anyInt(), anyInt())).thenReturn(List.of());
        when(memberMapper.countList(isNull(), isNull(), eq(3L))).thenReturn(0L);

        // Manager requests church 5, but must be pinned to their own church 3.
        memberService.list(new MemberSearchRequest(0, 10, null, null, 5L, null, null), MANAGER);

        verify(memberMapper).selectList(isNull(), isNull(), eq(3L), anyString(), anyInt(), anyInt());
    }

    @Test
    void list_whitelistedSortKey_isResolvedToColumn() {
        when(memberMapper.selectList(isNull(), isNull(), isNull(), eq("m.name ASC, m.id DESC"), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(memberMapper.countList(isNull(), isNull(), isNull())).thenReturn(0L);

        memberService.list(new MemberSearchRequest(0, 10, null, null, null, "name", "asc"), SYSTEM);

        verify(memberMapper).selectList(isNull(), isNull(), isNull(), eq("m.name ASC, m.id DESC"), eq(0), eq(10));
    }

    @Test
    void list_unknownSortKey_fallsBackToDefaultOrder() {
        when(memberMapper.selectList(isNull(), isNull(), isNull(), eq("m.created_at DESC, m.id DESC"), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(memberMapper.countList(isNull(), isNull(), isNull())).thenReturn(0L);

        // 'phantom' is not in the whitelist → default ordering.
        memberService.list(new MemberSearchRequest(0, 10, null, null, null, "phantom", "asc"), SYSTEM);

        verify(memberMapper).selectList(
                isNull(), isNull(), isNull(), eq("m.created_at DESC, m.id DESC"), eq(0), eq(10));
    }

    @Test
    void getDetail_crossChurch_isForbiddenForManager() {
        MemberDetail other = new MemberDetail();
        other.setChurchId(2L); // not the manager's church (3)
        when(memberMapper.selectDetail(9L)).thenReturn(other);

        assertThatThrownBy(() -> memberService.getDetail(9L, MANAGER))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.FORBIDDEN);
    }

    @Test
    void getDetail_missing_isNotFound() {
        when(memberMapper.selectDetail(99L)).thenReturn(null);

        assertThatThrownBy(() -> memberService.getDetail(99L, SYSTEM))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.NOT_FOUND);
    }

    @Test
    void approve_asChurchManager_onlyAffectsOwnChurchMembers() {
        Member inScope = member(1L, 3L, UserStatus.PENDING);    // manager's church
        Member outOfScope = member(2L, 2L, UserStatus.PENDING); // other church
        when(memberRepository.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(inScope, outOfScope));

        int affected = memberService.approve(List.of(1L, 2L), MANAGER);

        assertThat(affected).isEqualTo(1);
        assertThat(inScope.getUserStatus()).isEqualTo(UserStatus.CONFIRMED);
        assertThat(outOfScope.getUserStatus()).isEqualTo(UserStatus.PENDING); // untouched
    }

    @Test
    void approve_asSystem_affectsAll() {
        Member a = member(1L, 3L, UserStatus.PENDING);
        Member b = member(2L, 2L, UserStatus.PENDING);
        when(memberRepository.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(a, b));

        int affected = memberService.approve(List.of(1L, 2L), SYSTEM);

        assertThat(affected).isEqualTo(2);
        assertThat(a.getUserStatus()).isEqualTo(UserStatus.CONFIRMED);
        assertThat(b.getUserStatus()).isEqualTo(UserStatus.CONFIRMED);
    }
}
