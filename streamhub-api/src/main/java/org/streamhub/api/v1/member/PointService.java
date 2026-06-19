package org.streamhub.api.v1.member;

import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.member.dto.PointGrantRequest;
import org.streamhub.api.v1.member.dto.PointLedgerListItem;
import org.streamhub.api.v1.member.dto.PointLedgerSearchRequest;
import org.streamhub.api.v1.member.entity.LedgerSourceType;
import org.streamhub.api.v1.member.entity.LedgerStatus;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.entity.PointLedger;
import org.streamhub.api.v1.member.mapper.PointMapper;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.member.repository.PointLedgerRepository;

/**
 * Grace-point ledger operations: paginated/per-member listing (MyBatis), manual
 * grant/deduction synchronised with the member's cached balance in one transaction,
 * and the scheduled expiry batch that recovers due accruals.
 *
 * <p>The ledger is the single source of truth; {@link Member#getPointBalance()} caches
 * the running sum. Reads/writes are scoped to the operator's church for CHURCH_MANAGER
 * operators; SYSTEM operators see everything (mirrors {@code MemberService}).
 */
@Slf4j
@Service
public class PointService {

    private final PointMapper pointMapper;
    private final PointLedgerRepository pointLedgerRepository;
    private final MemberRepository memberRepository;
    private final ActionLogPublisher actionLogPublisher;

    public PointService(PointMapper pointMapper,
                        PointLedgerRepository pointLedgerRepository,
                        MemberRepository memberRepository,
                        ActionLogPublisher actionLogPublisher) {
        this.pointMapper = pointMapper;
        this.pointLedgerRepository = pointLedgerRepository;
        this.memberRepository = memberRepository;
        this.actionLogPublisher = actionLogPublisher;
    }

    @Transactional(readOnly = true)
    public ResInfinityList<PointLedgerListItem> list(PointLedgerSearchRequest request, AdminPrincipal principal) {
        Long churchId = scopedChurchId(request.churchId(), principal);
        int size = request.pageSizeOrDefault();

        List<PointLedgerListItem> contents = pointMapper.selectList(
                blankToNull(request.keyword()), request.memberId(), churchId, request.offset(), size);
        long total = pointMapper.countList(blankToNull(request.keyword()), request.memberId(), churchId);
        return ResInfinityList.of(contents, total, size);
    }

    /** Ledger entries for one member (detail point tab). Verifies the member is in scope first. */
    @Transactional(readOnly = true)
    public ResInfinityList<PointLedgerListItem> listByMember(
            Long memberId, int pageNumber, int pageSize, AdminPrincipal principal) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureInScope(member.getChurchId(), principal);

        int size = pageSize <= 0 ? 10 : pageSize;
        int offset = (pageNumber < 0 ? 0 : pageNumber) * size;
        List<PointLedgerListItem> contents = pointMapper.selectList(null, memberId, null, offset, size);
        long total = pointMapper.countList(null, memberId, null);
        return ResInfinityList.of(contents, total, size);
    }

    /**
     * Manual grant/deduction: adjusts the member's cached balance and appends the ledger
     * entry in a single transaction. A negative delta deducts; the balance may not go
     * below zero (translated to {@code INVALID_PARAMETER}).
     */
    @Transactional
    public PointLedgerListItem grant(PointGrantRequest request, AdminPrincipal principal) {
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureInScope(member.getChurchId(), principal);

        // Atomic guarded UPDATE: a deduction that would underflow affects 0 rows.
        if (memberRepository.adjustBalance(member.getId(), request.delta()) == 0) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "포인트 잔액이 부족합니다");
        }
        // adjustBalance cleared the persistence context; re-read the freshly persisted
        // balance so the ledger's balanceAfter never diverges from the member balance.
        long balanceAfter = memberRepository.findById(member.getId())
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND))
                .getPointBalance();

        LocalDateTime expireAt = request.expireDays() == null
                ? null
                : LocalDateTime.now().plusDays(request.expireDays());
        PointLedger ledger = pointLedgerRepository.saveAndFlush(PointLedger.builder()
                .memberId(member.getId())
                .delta(request.delta())
                .balanceAfter(balanceAfter)
                .reason(request.reason())
                .sourceType(LedgerSourceType.MANUAL)
                .status(LedgerStatus.ACTIVE)
                .expireAt(expireAt)
                .createdAt(LocalDateTime.now())
                .build());

        actionLogPublisher.publish(
                request.delta() >= 0 ? "POINT_GRANT" : "POINT_DEDUCT",
                "MEMBER", String.valueOf(member.getId()),
                request.delta() + "P: " + request.reason());

        return pointMapper.selectById(ledger.getId());
    }

    /**
     * Expiry batch: recovers due active accruals, recording the recovery as a separate
     * ledger row and transitioning the original accrual to {@code EXPIRED}. Already-spent
     * points are never clawed below zero ({@code min(delta, balance)}).
     */
    @Transactional
    public void expirePoints() {
        List<PointLedger> due = pointLedgerRepository
                .findByStatusAndExpireAtBefore(LedgerStatus.ACTIVE, LocalDateTime.now());
        for (PointLedger entry : due) {
            if (entry.getDelta() > 0) {
                Member member = memberRepository.findById(entry.getMemberId()).orElse(null);
                if (member != null) {
                    long recover = Math.min(entry.getDelta(), member.getPointBalance());
                    // Atomic deduction; affects 0 rows if a concurrent spend already drained
                    // the balance below the recovery amount, in which case no recovery row.
                    if (recover > 0 && memberRepository.adjustBalance(member.getId(), -recover) == 1) {
                        long balanceAfter = memberRepository.findById(member.getId())
                                .map(Member::getPointBalance)
                                .orElse(0L);
                        pointLedgerRepository.save(PointLedger.builder()
                                .memberId(member.getId())
                                .delta(-recover)
                                .balanceAfter(balanceAfter)
                                .reason("포인트 만료 회수")
                                .sourceType(LedgerSourceType.EXPIRE)
                                .status(LedgerStatus.ACTIVE)
                                .expireAt(null)
                                .createdAt(LocalDateTime.now())
                                .build());
                    }
                }
            }
            // adjustBalance's context clear detaches entry, so persist the EXPIRED
            // transition explicitly (merge) rather than relying on dirty-checking.
            entry.markExpired();
            pointLedgerRepository.save(entry);
        }
        log.info("Expired {} ledger entries", due.size());
    }

    // --- helpers -----------------------------------------------------------

    /** Resolves the church filter: CHURCH_MANAGER is pinned to its own church. */
    private Long scopedChurchId(Long requestedChurchId, AdminPrincipal principal) {
        return principal.isSystem() ? requestedChurchId : principal.churchId();
    }

    private void ensureInScope(Long churchId, AdminPrincipal principal) {
        if (!principal.isSystem() && !churchId.equals(principal.churchId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
