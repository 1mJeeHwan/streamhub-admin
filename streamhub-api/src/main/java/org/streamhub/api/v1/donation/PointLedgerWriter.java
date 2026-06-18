package org.streamhub.api.v1.donation;

import org.springframework.stereotype.Component;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.member.entity.LedgerSourceType;
import org.streamhub.api.v1.member.entity.LedgerStatus;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.entity.PointLedger;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.member.repository.PointLedgerRepository;

/**
 * Appends accrual rows to the {@link PointLedger}. Shared by billing-cycle accrual and
 * one-off donation accrual so both keep an identical append path.
 *
 * <p>The member's cached {@link Member#getPointBalance()} is the single source of truth:
 * this writer mutates it and derives {@code balanceAfter} from it, exactly as
 * {@code PointService.grant} does — so the ledger running balance and the member balance
 * can never diverge regardless of which path (manual grant vs. donation accrual) wrote last.
 */
@Component
public class PointLedgerWriter {

    private final PointLedgerRepository pointLedgerRepository;
    private final MemberRepository memberRepository;

    public PointLedgerWriter(PointLedgerRepository pointLedgerRepository,
                             MemberRepository memberRepository) {
        this.pointLedgerRepository = pointLedgerRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * Appends one accrual row for a member, updating the cached member balance in the same
     * transaction, and returns the resulting balance.
     *
     * @param memberId   member receiving the points
     * @param delta      signed point change (accrual is positive)
     * @param reason     human-readable ledger note
     * @param donationId source donation id (nullable)
     * @return the running balance after this append
     */
    public long append(Long memberId, long delta, String reason, Long donationId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        try {
            member.addPoint(delta);
        } catch (IllegalStateException e) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "포인트 잔액이 부족합니다");
        }
        // Flush so any subsequent MyBatis read (outside the persistence context) sees it.
        memberRepository.saveAndFlush(member);

        long balanceAfter = member.getPointBalance();
        pointLedgerRepository.save(PointLedger.builder()
                .memberId(memberId)
                .delta(delta)
                .balanceAfter(balanceAfter)
                .reason(reason)
                .sourceType(LedgerSourceType.DONATION)
                .status(LedgerStatus.ACTIVE)
                .donationId(donationId)
                .build());
        return balanceAfter;
    }
}
