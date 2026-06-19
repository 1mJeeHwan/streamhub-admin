package org.streamhub.api.v1.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.member.dto.PointGrantRequest;
import org.streamhub.api.v1.member.dto.PointLedgerListItem;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.entity.PointLedger;
import org.streamhub.api.v1.member.entity.UserStatus;
import org.streamhub.api.v1.member.mapper.PointMapper;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.member.repository.PointLedgerRepository;

/**
 * Verifies that {@link PointService#grant} routes balance changes through the atomic
 * {@code MemberRepository.adjustBalance} guard (M1): insufficient balance is rejected and
 * the ledger's {@code balanceAfter} reflects the re-read post-update balance, so the
 * ledger running balance equals the member balance.
 */
@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private PointMapper pointMapper;

    @Mock
    private PointLedgerRepository pointLedgerRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ActionLogPublisher actionLogPublisher;

    @InjectMocks
    private PointService pointService;

    private static final AdminPrincipal SYSTEM = new AdminPrincipal(1L, "SYSTEM", null);

    private Member member(long id, long balance) {
        Member m = Member.builder()
                .churchId(3L)
                .email("m" + id + "@test")
                .password("hash")
                .name("회원" + id)
                .phone("010")
                .userStatus(UserStatus.CONFIRMED)
                .liveYn("N")
                .pointBalance(balance)
                .createdAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    @Test
    void grant_whenAdjustAffectsZeroRows_isRejectedAsInsufficientBalance() {
        Member m = member(10L, 50L);
        when(memberRepository.findById(10L)).thenReturn(Optional.of(m));
        // Deduction of 100 from a balance of 50 underflows: the guarded UPDATE affects 0 rows.
        when(memberRepository.adjustBalance(10L, -100L)).thenReturn(0);

        assertThatThrownBy(() ->
                pointService.grant(new PointGrantRequest(10L, -100L, "차감", null), SYSTEM))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.INVALID_PARAMETER);

        verify(pointLedgerRepository, never()).saveAndFlush(any());
    }

    @Test
    void grant_credit_recordsLedgerBalanceAfterFromReReadBalance() {
        // Pre-grant balance 50; after a +30 grant the persisted balance is 80.
        Member before = member(10L, 50L);
        Member after = member(10L, 80L);
        when(memberRepository.findById(10L))
                .thenReturn(Optional.of(before))  // scope/existence read
                .thenReturn(Optional.of(after));  // post-adjust re-read for balanceAfter
        when(memberRepository.adjustBalance(10L, 30L)).thenReturn(1);
        when(pointLedgerRepository.saveAndFlush(any())).thenAnswer(inv -> {
            PointLedger ledger = inv.getArgument(0);
            ReflectionTestUtils.setField(ledger, "id", 99L);
            return ledger;
        });
        when(pointMapper.selectById(99L)).thenReturn(new PointLedgerListItem());

        pointService.grant(new PointGrantRequest(10L, 30L, "적립", null), SYSTEM);

        ArgumentCaptor<PointLedger> captor = ArgumentCaptor.forClass(PointLedger.class);
        verify(pointLedgerRepository).saveAndFlush(captor.capture());
        PointLedger written = captor.getValue();
        assertThat(written.getDelta()).isEqualTo(30L);
        // balanceAfter must equal the re-read post-update balance, not the stale 50.
        assertThat(written.getBalanceAfter()).isEqualTo(80L);
        verify(memberRepository).adjustBalance(eq(10L), eq(30L));
    }

    @Test
    void grant_deduct_recordsLedgerBalanceAfterFromReReadBalance() {
        Member before = member(10L, 100L);
        Member after = member(10L, 60L);
        when(memberRepository.findById(10L))
                .thenReturn(Optional.of(before))
                .thenReturn(Optional.of(after));
        when(memberRepository.adjustBalance(10L, -40L)).thenReturn(1);
        when(pointLedgerRepository.saveAndFlush(any())).thenAnswer(inv -> {
            PointLedger ledger = inv.getArgument(0);
            ReflectionTestUtils.setField(ledger, "id", 100L);
            return ledger;
        });
        when(pointMapper.selectById(100L)).thenReturn(new PointLedgerListItem());

        pointService.grant(new PointGrantRequest(10L, -40L, "차감", null), SYSTEM);

        ArgumentCaptor<PointLedger> captor = ArgumentCaptor.forClass(PointLedger.class);
        verify(pointLedgerRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getBalanceAfter()).isEqualTo(60L);
    }

    @Test
    void grant_missingMember_isNotFound() {
        when(memberRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                pointService.grant(new PointGrantRequest(404L, 10L, "적립", null), SYSTEM))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.NOT_FOUND);

        verify(memberRepository, never()).adjustBalance(any(), org.mockito.ArgumentMatchers.anyLong());
    }
}
