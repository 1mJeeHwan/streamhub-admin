package org.streamhub.api.v1.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.streamhub.api.v1.visit.entity.DeviceType;
import org.streamhub.api.v1.visit.entity.VisitLog;
import org.streamhub.api.v1.visit.repository.VisitLogRepository;

/**
 * Real visit recording (접속 통계): a page view persists one VisitLog keyed by the client IP masked
 * to its first two octets, so the per-IP stat reflects actual visitors (not just seeded demo rows).
 */
class VisitServiceTest {

    private final VisitLogRepository repo = Mockito.mock(VisitLogRepository.class);
    private final VisitService service = new VisitService(repo);

    @Test
    void maskIp_keepsFirstTwoOctets() {
        assertThat(VisitService.maskIp("211.45.130.22")).isEqualTo("211.45.*.*");
        assertThat(VisitService.maskIp("10.0.0.1")).isEqualTo("10.0.*.*");
        assertThat(VisitService.maskIp("2001:db8:abcd:12::1")).isEqualTo("2001:db8:*");
        assertThat(VisitService.maskIp(null)).isNull();
        assertThat(VisitService.maskIp("  ")).isNull();
    }

    @Test
    void record_savesVisitWithMaskedIpAndPath() {
        service.record("203.0.113.55", "Mozilla/5.0 (iPhone; Mobile)", "/albums/2", "MOBILE", 7L);

        ArgumentCaptor<VisitLog> captor = ArgumentCaptor.forClass(VisitLog.class);
        verify(repo).save(captor.capture());
        VisitLog saved = captor.getValue();
        assertThat(saved.getIpMasked()).isEqualTo("203.0.*.*"); // full IP never stored
        assertThat(saved.getPath()).isEqualTo("/albums/2");
        assertThat(saved.getDeviceType()).isEqualTo(DeviceType.MOBILE);
        assertThat(saved.getMemberId()).isEqualTo(7L);
        assertThat(saved.getVisitedAt()).isNotNull();
    }
}
