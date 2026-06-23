package org.streamhub.api.v1.inquiry;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.base.security.AuthoritiesConstants;
import org.streamhub.api.v1.inquiry.dto.InquiryAnswerRequest;
import org.streamhub.api.v1.inquiry.entity.CustomerInquiry;
import org.streamhub.api.v1.inquiry.entity.InquiryCategory;
import org.streamhub.api.v1.inquiry.repository.CustomerInquiryRepository;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.repository.MemberRepository;

/**
 * Unit tests for {@link InquiryService} church scoping (cross-church IDOR + member PII): a
 * CHURCH_MANAGER may only read/mutate customer inquiries owned by members in its own church;
 * SYSTEM bypasses; guest inquiries (null memberId) are forbidden to a scoped operator.
 */
@ExtendWith(MockitoExtension.class)
class InquiryScopingTest {

    @Mock
    private CustomerInquiryRepository customerInquiryRepository;
    @Mock
    private MemberRepository memberRepository;

    private static final AdminPrincipal SYSTEM = new AdminPrincipal(1L, AuthoritiesConstants.SYSTEM, null);
    private static final AdminPrincipal MANAGER_100 =
            new AdminPrincipal(2L, AuthoritiesConstants.CHURCH_MANAGER, 100L);

    private InquiryService service() {
        return new InquiryService(customerInquiryRepository, memberRepository);
    }

    private CustomerInquiry inquiry(Long memberId) {
        CustomerInquiry inquiry = CustomerInquiry.builder()
                .memberId(memberId).memberName("홍길동").category(InquiryCategory.ETC)
                .title("문의").content("내용").build();
        ReflectionTestUtils.setField(inquiry, "id", 7L);
        return inquiry;
    }

    private Member memberInChurch(Long memberId, Long churchId) {
        Member member = Member.builder().churchId(churchId).email("x@x.com").name("x").build();
        ReflectionTestUtils.setField(member, "id", memberId);
        return member;
    }

    @Test
    void detail_onInquiryInAnotherChurch_isForbidden() {
        when(customerInquiryRepository.findById(7L)).thenReturn(Optional.of(inquiry(5L)));
        when(memberRepository.findById(5L)).thenReturn(Optional.of(memberInChurch(5L, 200L)));

        assertThatThrownBy(() -> service().detail(7L, MANAGER_100))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.FORBIDDEN);
    }

    @Test
    void answer_onGuestInquiry_isForbiddenForManager() {
        when(customerInquiryRepository.findById(7L)).thenReturn(Optional.of(inquiry(null)));

        assertThatThrownBy(() -> service().answer(7L, new InquiryAnswerRequest("답변"), MANAGER_100))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.FORBIDDEN);

        verify(customerInquiryRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void detail_forSystem_bypassesScope() {
        when(customerInquiryRepository.findById(7L)).thenReturn(Optional.of(inquiry(5L)));

        service().detail(7L, SYSTEM);

        verify(memberRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }
}
