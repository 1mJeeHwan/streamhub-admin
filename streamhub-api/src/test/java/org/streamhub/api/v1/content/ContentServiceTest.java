package org.streamhub.api.v1.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.base.security.AuthoritiesConstants;
import org.streamhub.api.base.storage.StorageService;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.content.dto.ContentCreateRequest;
import org.streamhub.api.v1.content.dto.ContentDetail;
import org.streamhub.api.v1.content.dto.ContentSearchRequest;
import org.streamhub.api.v1.content.entity.ContentStatus;
import org.streamhub.api.v1.content.entity.Channel;
import org.streamhub.api.v1.content.entity.Content;
import org.streamhub.api.v1.content.mapper.ContentMapper;
import org.streamhub.api.v1.content.repository.ChannelRepository;
import org.streamhub.api.v1.content.repository.ContentFileRepository;
import org.streamhub.api.v1.content.repository.ContentHashtagRepository;
import org.streamhub.api.v1.content.repository.ContentRepository;
import org.streamhub.api.v1.content.repository.HashtagRepository;

/**
 * Guards the Content multi-tenancy scoping (H3): a CHURCH_MANAGER must not reach another church's
 * content, and a manager whose token carries no church scope must fail closed rather than leak
 * every church. SYSTEM bypass is exercised by the existing public/integration paths.
 */
@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

    @Mock private ContentMapper contentMapper;
    @Mock private ContentRepository contentRepository;
    @Mock private ChannelRepository channelRepository;
    @Mock private HashtagRepository hashtagRepository;
    @Mock private HashtagWriter hashtagWriter;
    @Mock private ContentHashtagRepository contentHashtagRepository;
    @Mock private ContentFileRepository contentFileRepository;
    @Mock private StorageService storageService;
    @Mock private ActionLogPublisher actionLogPublisher;
    @Mock private org.streamhub.api.v1.member.repository.MemberRepository memberRepository;
    @Mock private org.streamhub.api.v1.notification.NotificationService notificationService;

    /** CHURCH_MANAGER pinned to church 100. */
    private static final AdminPrincipal MANAGER_100 =
            new AdminPrincipal(2L, AuthoritiesConstants.CHURCH_MANAGER, 100L);

    private ContentService service() {
        return new ContentService(
                contentMapper, contentRepository, channelRepository, hashtagRepository,
                hashtagWriter, contentHashtagRepository, contentFileRepository,
                storageService, actionLogPublisher, memberRepository, notificationService);
    }

    private Channel channelInChurch(Long churchId) {
        Channel channel = mock(Channel.class);
        when(channel.getChurchId()).thenReturn(churchId);
        return channel;
    }

    @Test
    void getDetail_onAnotherChurchContent_isForbidden() {
        ContentDetail detail = mock(ContentDetail.class);
        when(detail.getChannelId()).thenReturn(9L);
        when(contentMapper.selectDetail(5L)).thenReturn(detail);
        Channel otherChurchChannel = channelInChurch(200L);
        when(channelRepository.findById(9L)).thenReturn(Optional.of(otherChurchChannel));

        assertThatThrownBy(() -> service().getDetail(5L, MANAGER_100))
                .isInstanceOf(ApiException.class);

        verify(storageService, never()).publicUrl(any()); // denied before any assembly
    }

    @Test
    void delete_onAnotherChurchContent_isForbidden() {
        Content content = mock(Content.class);
        when(content.getChannelId()).thenReturn(9L);
        when(contentRepository.findById(5L)).thenReturn(Optional.of(content));
        Channel otherChurchChannel = channelInChurch(200L);
        when(channelRepository.findById(9L)).thenReturn(Optional.of(otherChurchChannel));

        assertThatThrownBy(() -> service().delete(5L, MANAGER_100))
                .isInstanceOf(ApiException.class);

        verify(contentRepository, never()).delete(any()); // nothing destroyed cross-tenant
    }

    @Test
    void create_notifyOnPublish_notifiesOwnChurchMembers_targetedNotBroadcast() {
        // Channel 9 belongs to church 100 (the manager's own) — used by ensureChannelInScope, the
        // notify path, and the getDetail tail.
        Channel ownChannel = channelInChurch(100L);
        when(channelRepository.findById(9L)).thenReturn(Optional.of(ownChannel));
        Content saved = mock(Content.class);
        when(saved.getId()).thenReturn(5L);
        when(saved.getChannelId()).thenReturn(9L);
        when(saved.getStatus()).thenReturn(ContentStatus.PUBLISHED);
        when(saved.getTitle()).thenReturn("주일예배 실황");
        when(contentRepository.save(any())).thenReturn(saved);
        org.streamhub.api.v1.member.entity.Member m1 = mock(org.streamhub.api.v1.member.entity.Member.class);
        org.streamhub.api.v1.member.entity.Member m2 = mock(org.streamhub.api.v1.member.entity.Member.class);
        when(m1.getId()).thenReturn(11L);
        when(m2.getId()).thenReturn(22L);
        when(memberRepository.findAllByChurchId(100L)).thenReturn(java.util.List.of(m1, m2));
        ContentDetail detail = mock(ContentDetail.class);
        when(detail.getChannelId()).thenReturn(9L); // getDetail tail: in-scope for MANAGER_100
        when(contentMapper.selectDetail(5L)).thenReturn(detail);

        ContentCreateRequest request = new ContentCreateRequest(
                "주일예배 실황", null, org.streamhub.api.v1.content.entity.ContentType.VIDEO, 9L,
                null, null, null, ContentStatus.PUBLISHED, null, true);
        service().create(request, MANAGER_100);

        var captor = org.mockito.ArgumentCaptor.forClass(
                org.streamhub.api.v1.notification.dto.NotificationSendRequest.class);
        verify(notificationService).send(captor.capture(), eq(MANAGER_100));
        var sent = captor.getValue();
        // Must be TARGETED to this church's members — never BROADCAST (all-church leak).
        assertThat(sent.scope()).isEqualTo(org.streamhub.api.v1.notification.entity.NotificationScope.TARGETED);
        assertThat(sent.memberIds()).containsExactly(11L, 22L);
    }

    @Test
    void list_forManagerWithoutChurchScope_failsClosed() {
        // A non-SYSTEM principal with a null churchId must not degrade the mapper filter to IS NULL
        // (which would return every church's content); it must be rejected outright.
        AdminPrincipal managerNoChurch =
                new AdminPrincipal(3L, AuthoritiesConstants.CHURCH_MANAGER, null);
        ContentSearchRequest request = new ContentSearchRequest(0, 10, null, null, null, null, null, null);

        assertThatThrownBy(() -> service().list(request, managerNoChurch))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(contentMapper); // never queried with an open filter
    }

    @Test
    void list_forManager_passesOwnChurchToMapper() {
        ContentSearchRequest request = new ContentSearchRequest(0, 10, null, null, null, null, null, null);
        when(contentMapper.selectList(any(), any(), any(), any(), eq(100L), any(), anyInt(), anyInt()))
                .thenReturn(java.util.List.of());
        when(contentMapper.countList(any(), any(), any(), any(), eq(100L))).thenReturn(0L);

        service().list(request, MANAGER_100);

        // The manager's own churchId (100) is pushed into the query, never null/open.
        verify(contentMapper).selectList(any(), any(), any(), any(), eq(100L), any(), anyInt(), anyInt());
    }
}
