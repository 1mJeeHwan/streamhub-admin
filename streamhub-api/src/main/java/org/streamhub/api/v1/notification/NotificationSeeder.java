package org.streamhub.api.v1.notification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.notification.entity.NotificationChannel;
import org.streamhub.api.v1.notification.entity.NotificationLog;
import org.streamhub.api.v1.notification.entity.NotificationStatus;
import org.streamhub.api.v1.notification.repository.NotificationLogRepository;

/**
 * Seeds the notification-center send-log demo dataset (알림센터 발송 로그). Log only — no real
 * message is ever sent; every row is fictional and recipients are masked (PII guard).
 * Idempotent (skips when the table already holds rows). The fixed-seed {@link Random} keeps the
 * dataset <em>shape</em> (channel mix ~50/30/20 SMS/PUSH/EMAIL, status mix ~85/10/5
 * SUCCESS/FAIL/PENDING) reproducible; absolute dates are anchored to {@link LocalDateTime#now()}
 * so the 45-day window rolls forward to stay current.
 */
@Slf4j
@Component
@Order(18)
public class NotificationSeeder implements CommandLineRunner {

    private static final long SEED = 918L;
    private static final int TARGET_LOGS = 120;
    private static final int WINDOW_DAYS = 45;

    private static final String[] TITLES = {
            "[그레이스온] 새 설교 영상이 등록되었습니다",
            "정기후원 결제 안내",
            "주문이 발송되었습니다",
            "[그레이스온] 이번 주 주보가 도착했습니다",
            "새가족 등록을 환영합니다",
            "출석 체크 리마인드",
            "후원 영수증 발급 안내"
    };
    private static final String[] CONTENTS = {
            "오늘 등록된 새 설교 영상을 앱에서 확인해 보세요.",
            "이번 달 정기후원 결제가 정상적으로 진행될 예정입니다.",
            "주문하신 상품이 발송되었습니다. 배송 조회를 확인해 주세요.",
            "이번 주 주보와 묵상 자료가 업데이트되었습니다.",
            "새가족 등록이 완료되었습니다. 다음 안내를 확인해 주세요.",
            "오늘 예배 출석 체크를 잊지 마세요.",
            "연말정산용 후원 영수증을 발급해 드립니다."
    };
    private static final String[] FAIL_REASONS = {"수신거부", "번호 오류", "토큰 만료"};

    private final NotificationLogRepository notificationLogRepository;

    public NotificationSeeder(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    @Override
    public void run(String... args) {
        if (notificationLogRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Random rnd = new Random(SEED);

        List<NotificationLog> logs = new ArrayList<>();
        for (int i = 0; i < TARGET_LOGS; i++) {
            NotificationChannel channel = resolveChannel(rnd);
            NotificationStatus status = resolveStatus(rnd);
            LocalDateTime createdAt = createdAt(now, rnd);
            int topic = rnd.nextInt(TITLES.length);

            logs.add(NotificationLog.builder()
                    .channel(channel)
                    .targetMasked(maskedTarget(channel, rnd))
                    .title(TITLES[topic])
                    .content(CONTENTS[topic])
                    .status(status)
                    .failReason(status == NotificationStatus.FAIL
                            ? FAIL_REASONS[rnd.nextInt(FAIL_REASONS.length)] : null)
                    .sentAt(status == NotificationStatus.PENDING ? null : createdAt)
                    .createdAt(createdAt)
                    .build());
        }
        notificationLogRepository.saveAll(logs);
        log.info("Seeded {} notification logs (log-only demo data)", logs.size());
    }

    /** Channel mix: ~50% SMS, ~30% PUSH, ~20% EMAIL. */
    private NotificationChannel resolveChannel(Random rnd) {
        int r = rnd.nextInt(100);
        if (r < 50) {
            return NotificationChannel.SMS;
        }
        if (r < 80) {
            return NotificationChannel.PUSH;
        }
        return NotificationChannel.EMAIL;
    }

    /** Status mix: ~85% SUCCESS, ~10% FAIL, ~5% PENDING. */
    private NotificationStatus resolveStatus(Random rnd) {
        int r = rnd.nextInt(100);
        if (r < 85) {
            return NotificationStatus.SUCCESS;
        }
        if (r < 95) {
            return NotificationStatus.FAIL;
        }
        return NotificationStatus.PENDING;
    }

    /** Masked recipient per channel: phone for SMS/PUSH, email for EMAIL (PII guard). */
    private String maskedTarget(NotificationChannel channel, Random rnd) {
        if (channel == NotificationChannel.EMAIL) {
            char first = (char) ('a' + rnd.nextInt(26));
            return first + "***@streamhub.test";
        }
        return String.format("010-****-%04d", 1000 + rnd.nextInt(9000));
    }

    /** A {@link LocalDateTime} within the last {@link #WINDOW_DAYS} days. */
    private LocalDateTime createdAt(LocalDateTime now, Random rnd) {
        return now.minusDays(rnd.nextInt(WINDOW_DAYS + 1))
                .withHour(9 + rnd.nextInt(12))
                .withMinute(rnd.nextInt(60))
                .withSecond(0)
                .withNano(0);
    }
}
