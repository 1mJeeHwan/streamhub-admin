package org.streamhub.api.v1.goods.inquiry;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.goods.inquiry.dto.GoodsInquiryAnswerRequest;
import org.streamhub.api.v1.goods.inquiry.dto.GoodsInquiryDto;
import org.streamhub.api.v1.goods.inquiry.dto.GoodsInquirySearchRequest;
import org.streamhub.api.v1.goods.inquiry.entity.GoodsInquiry;
import org.streamhub.api.v1.goods.inquiry.repository.GoodsInquiryRepository;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.repository.MemberRepository;

/**
 * Goods Q&A management: admin listing, detail, answering and deletion.
 *
 * <p>Goods are global, but each inquiry carries member PII (id/name), so the church scope is
 * resolved through the owning {@code MEMBER}: a CHURCH_MANAGER sees/acts on only its own church's
 * inquiries (mirrors {@code OrderService}). Guest inquiries ({@code memberId == null}) have no
 * church linkage and are visible only to unscoped operators (SYSTEM/VIEWER).
 */
@Service
public class GoodsInquiryService {

    private final GoodsInquiryRepository goodsInquiryRepository;
    private final MemberRepository memberRepository;

    public GoodsInquiryService(GoodsInquiryRepository goodsInquiryRepository,
                               MemberRepository memberRepository) {
        this.goodsInquiryRepository = goodsInquiryRepository;
        this.memberRepository = memberRepository;
    }

    /** Admin listing: newest first, optionally filtered by answer status, scoped to the operator's church. */
    @Transactional(readOnly = true)
    public List<GoodsInquiryDto> list(GoodsInquirySearchRequest request, AdminPrincipal principal) {
        List<GoodsInquiry> inquiries = (request != null && request.getAnswerStatus() != null)
                ? goodsInquiryRepository.findByAnswerStatusOrderByIdDesc(request.getAnswerStatus())
                : goodsInquiryRepository.findAllByOrderByIdDesc();
        return scopeToChurch(inquiries, principal).stream().map(GoodsInquiryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public GoodsInquiryDto detail(Long id, AdminPrincipal principal) {
        return GoodsInquiryDto.from(loadInScope(id, principal));
    }

    @Transactional
    public GoodsInquiryDto answer(Long id, GoodsInquiryAnswerRequest request, AdminPrincipal principal) {
        GoodsInquiry inquiry = loadInScope(id, principal);
        inquiry.answer(request.getAnswerContent());
        goodsInquiryRepository.saveAndFlush(inquiry);
        return GoodsInquiryDto.from(inquiry);
    }

    @Transactional
    public void delete(Long id, AdminPrincipal principal) {
        goodsInquiryRepository.delete(loadInScope(id, principal));
    }

    // --- helpers -----------------------------------------------------------

    /** Loads an inquiry and verifies it is within the operator's church. */
    private GoodsInquiry loadInScope(Long id, AdminPrincipal principal) {
        GoodsInquiry inquiry = goodsInquiryRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureInScope(inquiry, principal);
        return inquiry;
    }

    /** Verifies the inquiry's owning member is in the operator's church (unscoped bypasses). */
    private void ensureInScope(GoodsInquiry inquiry, AdminPrincipal principal) {
        if (principal.isUnscoped()) {
            return;
        }
        if (inquiry.getMemberId() == null) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        Member member = memberRepository.findById(inquiry.getMemberId())
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (!member.getChurchId().equals(principal.churchId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }

    /** Filters the listing to inquiries owned by members of the operator's church (unscoped sees all). */
    private List<GoodsInquiry> scopeToChurch(List<GoodsInquiry> inquiries, AdminPrincipal principal) {
        if (principal.isUnscoped()) {
            return inquiries;
        }
        Set<Long> memberIds = inquiries.stream()
                .map(GoodsInquiry::getMemberId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> ownChurchMemberIds = memberIds.isEmpty()
                ? Set.of()
                : memberRepository.findAllByIdIn(List.copyOf(memberIds)).stream()
                        .filter(member -> member.getChurchId().equals(principal.churchId()))
                        .map(Member::getId)
                        .collect(Collectors.toSet());
        return inquiries.stream()
                .filter(inquiry -> inquiry.getMemberId() != null
                        && ownChurchMemberIds.contains(inquiry.getMemberId()))
                .toList();
    }
}
