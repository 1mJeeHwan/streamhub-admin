package org.streamhub.api.v1.inquiry;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.inquiry.dto.InquiryAnswerRequest;
import org.streamhub.api.v1.inquiry.dto.InquiryDto;
import org.streamhub.api.v1.inquiry.dto.InquirySearchRequest;
import org.streamhub.api.v1.inquiry.entity.CustomerInquiry;
import org.streamhub.api.v1.inquiry.entity.InquiryStatus;
import org.streamhub.api.v1.inquiry.repository.CustomerInquiryRepository;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.repository.MemberRepository;

/**
 * 1:1 customer inquiry management: a status/category-filtered listing plus the answer,
 * close, and delete operations an operator works through. The demo dataset is small, so
 * the listing loads all matching rows and orders them in memory — no pagination needed.
 *
 * <p>Inquiries carry no church column, so the church scope is resolved through the owning
 * {@code MEMBER}: a CHURCH_MANAGER may only read/mutate inquiries whose member is in its own
 * church (mirrors {@code OrderService}). Guest/anonymized inquiries ({@code memberId == null})
 * have no church linkage and are visible only to unscoped operators (SYSTEM/VIEWER).
 */
@Service
public class InquiryService {

    private final CustomerInquiryRepository customerInquiryRepository;
    private final MemberRepository memberRepository;

    public InquiryService(CustomerInquiryRepository customerInquiryRepository,
                          MemberRepository memberRepository) {
        this.customerInquiryRepository = customerInquiryRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * Admin listing. Applies the optional status/category filter, scopes to the operator's
     * church, then orders newest first. When no status filter is given, OPEN inquiries (the
     * unanswered queue) are surfaced ahead of the rest so operators see pending work first.
     */
    @Transactional(readOnly = true)
    public List<InquiryDto> list(InquirySearchRequest request, AdminPrincipal principal) {
        InquiryStatus status = request != null ? request.status() : null;
        List<CustomerInquiry> inquiries = scopeToChurch(findFiltered(request), principal);
        Comparator<CustomerInquiry> byCreatedDesc =
                Comparator.comparing(CustomerInquiry::getCreatedAt).reversed();
        Comparator<CustomerInquiry> ordering = status != null
                ? byCreatedDesc
                : Comparator.comparing((CustomerInquiry i) -> i.getStatus() == InquiryStatus.OPEN ? 0 : 1)
                        .thenComparing(byCreatedDesc);
        return inquiries.stream()
                .sorted(ordering)
                .map(InquiryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InquiryDto detail(Long id, AdminPrincipal principal) {
        CustomerInquiry inquiry = loadInScope(id, principal);
        return InquiryDto.from(inquiry);
    }

    @Transactional
    public InquiryDto answer(Long id, InquiryAnswerRequest request, AdminPrincipal principal) {
        CustomerInquiry inquiry = loadInScope(id, principal);
        inquiry.answer(request.answerContent());
        customerInquiryRepository.saveAndFlush(inquiry);
        return InquiryDto.from(inquiry);
    }

    @Transactional
    public InquiryDto close(Long id, AdminPrincipal principal) {
        CustomerInquiry inquiry = loadInScope(id, principal);
        inquiry.close();
        customerInquiryRepository.saveAndFlush(inquiry);
        return InquiryDto.from(inquiry);
    }

    @Transactional
    public void delete(Long id, AdminPrincipal principal) {
        CustomerInquiry inquiry = loadInScope(id, principal);
        customerInquiryRepository.delete(inquiry);
    }

    // --- helpers -----------------------------------------------------------

    /** Loads an inquiry and verifies it is within the operator's church. */
    private CustomerInquiry loadInScope(Long id, AdminPrincipal principal) {
        CustomerInquiry inquiry = customerInquiryRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureInScope(inquiry, principal);
        return inquiry;
    }

    /**
     * Verifies the inquiry's owning member is in the operator's church (unscoped bypasses).
     * A guest inquiry (null memberId) has no church and is forbidden to a scoped operator.
     */
    private void ensureInScope(CustomerInquiry inquiry, AdminPrincipal principal) {
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
    private List<CustomerInquiry> scopeToChurch(List<CustomerInquiry> inquiries, AdminPrincipal principal) {
        if (principal.isUnscoped()) {
            return inquiries;
        }
        Set<Long> memberIds = inquiries.stream()
                .map(CustomerInquiry::getMemberId)
                .filter(java.util.Objects::nonNull)
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

    private List<CustomerInquiry> findFiltered(InquirySearchRequest request) {
        if (request == null) {
            return customerInquiryRepository.findAll();
        }
        if (request.status() != null && request.category() != null) {
            return customerInquiryRepository.findByStatusAndCategory(request.status(), request.category());
        }
        if (request.status() != null) {
            return customerInquiryRepository.findByStatus(request.status());
        }
        if (request.category() != null) {
            return customerInquiryRepository.findByCategory(request.category());
        }
        return customerInquiryRepository.findAll();
    }
}
