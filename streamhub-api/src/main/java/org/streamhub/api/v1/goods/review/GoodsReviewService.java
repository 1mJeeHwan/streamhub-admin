package org.streamhub.api.v1.goods.review;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.goods.review.dto.GoodsReviewDisplayRequest;
import org.streamhub.api.v1.goods.review.dto.GoodsReviewDto;
import org.streamhub.api.v1.goods.review.dto.GoodsReviewRatingRequest;
import org.streamhub.api.v1.goods.review.dto.GoodsReviewSearchRequest;
import org.streamhub.api.v1.goods.review.entity.GoodsReview;
import org.streamhub.api.v1.goods.review.repository.GoodsReviewRepository;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.repository.MemberRepository;

/**
 * Goods review management: admin listing, display toggle, rating update and deletion.
 *
 * <p>Goods are global, but each review carries member PII (id/name), so the church scope is
 * resolved through the owning {@code MEMBER}: a CHURCH_MANAGER sees/acts on only its own church's
 * reviews (mirrors {@code OrderService}). Guest reviews ({@code memberId == null}) have no church
 * linkage and are visible only to unscoped operators (SYSTEM/VIEWER).
 */
@Service
public class GoodsReviewService {

    private final GoodsReviewRepository goodsReviewRepository;
    private final MemberRepository memberRepository;

    public GoodsReviewService(GoodsReviewRepository goodsReviewRepository,
                              MemberRepository memberRepository) {
        this.goodsReviewRepository = goodsReviewRepository;
        this.memberRepository = memberRepository;
    }

    /** Admin listing: newest first, optionally filtered by display flag, scoped to the operator's church. */
    @Transactional(readOnly = true)
    public List<GoodsReviewDto> list(GoodsReviewSearchRequest request, AdminPrincipal principal) {
        List<GoodsReview> reviews = (request != null && request.getDisplayYn() != null
                && !request.getDisplayYn().isBlank())
                ? goodsReviewRepository.findByDisplayYnOrderByIdDesc(request.getDisplayYn())
                : goodsReviewRepository.findAllByOrderByIdDesc();
        return scopeToChurch(reviews, principal).stream().map(GoodsReviewDto::from).toList();
    }

    @Transactional
    public GoodsReviewDto changeDisplay(Long id, GoodsReviewDisplayRequest request, AdminPrincipal principal) {
        GoodsReview review = loadInScope(id, principal);
        review.changeDisplayYn(request.getDisplayYn());
        goodsReviewRepository.saveAndFlush(review);
        return GoodsReviewDto.from(review);
    }

    @Transactional
    public GoodsReviewDto changeRating(Long id, GoodsReviewRatingRequest request, AdminPrincipal principal) {
        GoodsReview review = loadInScope(id, principal);
        review.changeRating(request.getRating());
        goodsReviewRepository.saveAndFlush(review);
        return GoodsReviewDto.from(review);
    }

    @Transactional
    public void delete(Long id, AdminPrincipal principal) {
        goodsReviewRepository.delete(loadInScope(id, principal));
    }

    // --- helpers -----------------------------------------------------------

    /** Loads a review and verifies it is within the operator's church. */
    private GoodsReview loadInScope(Long id, AdminPrincipal principal) {
        GoodsReview review = goodsReviewRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureInScope(review, principal);
        return review;
    }

    /** Verifies the review's owning member is in the operator's church (unscoped bypasses). */
    private void ensureInScope(GoodsReview review, AdminPrincipal principal) {
        if (principal.isUnscoped()) {
            return;
        }
        if (review.getMemberId() == null) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        Member member = memberRepository.findById(review.getMemberId())
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (!member.getChurchId().equals(principal.churchId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }

    /** Filters the listing to reviews owned by members of the operator's church (unscoped sees all). */
    private List<GoodsReview> scopeToChurch(List<GoodsReview> reviews, AdminPrincipal principal) {
        if (principal.isUnscoped()) {
            return reviews;
        }
        Set<Long> memberIds = reviews.stream()
                .map(GoodsReview::getMemberId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> ownChurchMemberIds = memberIds.isEmpty()
                ? Set.of()
                : memberRepository.findAllByIdIn(List.copyOf(memberIds)).stream()
                        .filter(member -> member.getChurchId().equals(principal.churchId()))
                        .map(Member::getId)
                        .collect(Collectors.toSet());
        return reviews.stream()
                .filter(review -> review.getMemberId() != null
                        && ownChurchMemberIds.contains(review.getMemberId()))
                .toList();
    }
}
