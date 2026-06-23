package org.streamhub.api.v1.pub.me.point;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.entity.PointLedger;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.member.repository.PointLedgerRepository;
import org.streamhub.api.v1.pub.me.point.dto.MemberPointsDto;
import org.streamhub.api.v1.pub.me.point.dto.PointLedgerItem;

/**
 * Reads the authenticated member's own point summary ("내 포인트"): the cached balance plus a
 * paginated, newest-first slice of the {@code POINT_LEDGER}. The member id is resolved by the
 * controller from the Bearer member token, so this service is already scoped to one member.
 */
@Service
public class MemberPointService {

    private final MemberRepository memberRepository;
    private final PointLedgerRepository pointLedgerRepository;

    public MemberPointService(MemberRepository memberRepository,
                              PointLedgerRepository pointLedgerRepository) {
        this.memberRepository = memberRepository;
        this.pointLedgerRepository = pointLedgerRepository;
    }

    /**
     * Returns the member's current balance and a page of ledger entries (newest first).
     *
     * @param memberId   authenticated member id
     * @param pageNumber zero-based page index
     * @param pageSize   page size (must be &gt; 0)
     * @return the balance + paginated ledger payload
     * @throws ApiException {@code NOT_FOUND} if the member does not exist
     */
    @Transactional(readOnly = true)
    public MemberPointsDto points(Long memberId, int pageNumber, int pageSize) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));

        long totalCount = pointLedgerRepository.countByMemberId(memberId);
        int size = clampPageSize(pageSize);
        Pageable pageable = PageRequest.of(clampPageNumber(pageNumber), size);
        List<PointLedgerItem> items = pointLedgerRepository
                .findByMemberIdOrderByIdDesc(memberId, pageable)
                .stream()
                .map(this::toItem)
                .toList();

        ResInfinityList<PointLedgerItem> ledger = ResInfinityList.of(items, totalCount, size);
        return new MemberPointsDto(member.getPointBalance(), ledger);
    }

    private static int clampPageNumber(int pageNumber) {
        return Math.max(pageNumber, 0);
    }

    private static int clampPageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }

    private PointLedgerItem toItem(PointLedger ledger) {
        return new PointLedgerItem(
                ledger.getId(),
                ledger.getDelta(),
                ledger.getSourceType() != null ? ledger.getSourceType().name() : null,
                ledger.getReason(),
                ledger.getCreatedAt());
    }
}
