package org.streamhub.api.v1.member;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.base.util.SortResolver;
import org.streamhub.api.v1.member.dto.MemberDetail;
import org.streamhub.api.v1.member.dto.MemberListItem;
import org.streamhub.api.v1.member.dto.MemberSearchRequest;
import org.streamhub.api.v1.member.dto.MemberUpdateRequest;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.entity.UserStatus;
import org.streamhub.api.v1.member.mapper.MemberMapper;
import org.streamhub.api.v1.member.repository.MemberRepository;

/**
 * Member management: paginated search (MyBatis), detail/update (JPA), and bulk
 * status changes. All reads/writes are scoped to the operator's church when the
 * operator is a CHURCH_MANAGER; SYSTEM operators see everything.
 */
@Service
public class MemberService {

    /** Whitelisted sort keys (MemberListItem field → SQL column) for server-side list sorting. */
    private static final Map<String, String> MEMBER_SORT_COLUMNS = Map.of(
            "name", "m.name",
            "email", "m.email",
            "phone", "m.phone",
            "churchName", "c.name",
            "regionName", "r.name",
            "userStatus", "m.user_status",
            "liveYn", "m.live_yn",
            "createdAt", "m.created_at");

    private final MemberMapper memberMapper;
    private final MemberRepository memberRepository;
    private final org.streamhub.api.v1.actionlog.ActionLogPublisher actionLogPublisher;

    public MemberService(MemberMapper memberMapper, MemberRepository memberRepository,
                         org.streamhub.api.v1.actionlog.ActionLogPublisher actionLogPublisher) {
        this.memberMapper = memberMapper;
        this.memberRepository = memberRepository;
        this.actionLogPublisher = actionLogPublisher;
    }

    @Transactional(readOnly = true)
    public ResInfinityList<MemberListItem> list(MemberSearchRequest request, AdminPrincipal principal) {
        Long churchId = scopedChurchId(request.churchId(), principal);
        String status = request.userStatus() == null ? null : request.userStatus().name();
        int size = request.pageSizeOrDefault();
        String orderBy = SortResolver.resolve(request.sortBy(), request.sortDir(),
                MEMBER_SORT_COLUMNS, "m.id", "m.created_at DESC, m.id DESC");

        List<MemberListItem> contents =
                memberMapper.selectList(blankToNull(request.keyword()), status, churchId, orderBy, request.offset(), size);
        long total = memberMapper.countList(blankToNull(request.keyword()), status, churchId);
        return ResInfinityList.of(contents, total, size);
    }

    @Transactional(readOnly = true)
    public MemberDetail getDetail(Long id, AdminPrincipal principal) {
        MemberDetail detail = memberMapper.selectDetail(id);
        if (detail == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        ensureInScope(detail.getChurchId(), principal);
        return detail;
    }

    @Transactional
    public MemberDetail update(Long id, MemberUpdateRequest request, AdminPrincipal principal) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureInScope(member.getChurchId(), principal);
        member.updateProfile(request.name(), request.phone(), request.liveYn());
        // Flush the JPA change before the MyBatis read, which runs outside the
        // persistence context and would otherwise see stale data.
        memberRepository.saveAndFlush(member);
        actionLogPublisher.publish("MEMBER_UPDATE", "MEMBER", String.valueOf(id),
                "회원 정보 수정: " + request.name());
        return memberMapper.selectDetail(id);
    }

    @Transactional
    public int approve(List<Long> idList, AdminPrincipal principal) {
        int affected = changeStatus(idList, UserStatus.CONFIRMED, principal);
        actionLogPublisher.publish("MEMBER_APPROVE", "MEMBER", join(idList), affected + "건 승인");
        return affected;
    }

    @Transactional
    public int deny(List<Long> idList, AdminPrincipal principal) {
        int affected = changeStatus(idList, UserStatus.INACTIVE, principal);
        actionLogPublisher.publish("MEMBER_DENY", "MEMBER", join(idList), affected + "건 거부");
        return affected;
    }

    private String join(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private int changeStatus(List<Long> idList, UserStatus status, AdminPrincipal principal) {
        List<Member> members = memberRepository.findAllByIdIn(idList);
        int affected = 0;
        for (Member member : members) {
            if (!principal.isSystem() && !member.getChurchId().equals(principal.churchId())) {
                continue; // silently skip out-of-scope members
            }
            member.changeStatus(status);
            affected++;
        }
        return affected;
    }

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
