package org.streamhub.api.v1.pub.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.jwt.JwtTokenProvider;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.member.entity.Church;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.entity.UserStatus;
import org.streamhub.api.v1.member.repository.ChurchRepository;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.pub.auth.dto.MemberAuthResponse;
import org.streamhub.api.v1.pub.auth.dto.MemberInfo;
import org.streamhub.api.v1.pub.auth.dto.MemberLoginRequest;

/**
 * End-user (member) authentication for the public site. Issues member-scoped JWTs that
 * are isolated from admin tokens (see {@link JwtTokenProvider#createMemberAccessToken}).
 * Only {@link UserStatus#CONFIRMED} members may log in.
 */
@Service
public class MemberAuthService {

    private final MemberRepository memberRepository;
    private final ChurchRepository churchRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public MemberAuthService(
            MemberRepository memberRepository,
            ChurchRepository churchRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider) {
        this.memberRepository = memberRepository;
        this.churchRepository = churchRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional(readOnly = true)
    public MemberAuthResponse login(MemberLoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException(ResultCode.LOGIN_FAILED));
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new ApiException(ResultCode.LOGIN_FAILED);
        }
        if (member.getUserStatus() != UserStatus.CONFIRMED) {
            throw new ApiException(ResultCode.FORBIDDEN, "승인 대기 중이거나 비활성화된 계정입니다");
        }
        String token = tokenProvider.createMemberAccessToken(member);
        return new MemberAuthResponse(token, tokenProvider.getMemberExpSeconds(), toInfo(member));
    }

    @Transactional(readOnly = true)
    public MemberInfo me(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        return toInfo(member);
    }

    private MemberInfo toInfo(Member member) {
        String churchName = churchRepository.findById(member.getChurchId())
                .map(Church::getName)
                .orElse(null);
        return new MemberInfo(
                member.getId(), member.getName(), member.getEmail(),
                member.getPhone(), churchName, member.getCreatedAt());
    }
}
