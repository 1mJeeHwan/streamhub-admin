package org.streamhub.api.v1.banner;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.banner.entity.Banner;
import org.streamhub.api.v1.banner.entity.BannerDevice;
import org.streamhub.api.v1.banner.entity.BannerPosition;
import org.streamhub.api.v1.banner.entity.BannerTarget;
import org.streamhub.api.v1.banner.repository.BannerRepository;

/**
 * Seeds the front-banner demo dataset. Idempotent (skips when the banner table already holds
 * rows). The fixed-seed {@link Random} makes the dataset <em>shape</em> (position/device mix,
 * visibility, ordering) reproducible across runs; the absolute dates are <em>not</em> fixed —
 * every row is anchored to {@link LocalDateTime#now()}, so some windows are active and some
 * expired relative to today. All values are demo/fictional (image URLs are placeholders).
 */
@Slf4j
@Component
@Order(15)
public class BannerSeeder implements CommandLineRunner {

    private static final long SEED = 915L;
    private static final int TARGET_BANNERS = 24;

    private static final String[] TITLES = {
            "성탄 특별예배 안내",
            "신간 찬양앨범 출시",
            "정기후원 캠페인",
            "여름 수련회 모집"
    };
    private static final String[] LINK_URLS = {"/albums", "/donation", "/churches"};

    private static final BannerPosition[] POSITIONS = BannerPosition.values();
    private static final BannerDevice[] DEVICES = BannerDevice.values();

    private final BannerRepository bannerRepository;

    public BannerSeeder(BannerRepository bannerRepository) {
        this.bannerRepository = bannerRepository;
    }

    @Override
    public void run(String... args) {
        seedPositionBanners();
        seedTabPromos();
    }

    /** Legacy main/side image banners (no content-tab target). Seeded once on an empty table. */
    private void seedPositionBanners() {
        if (bannerRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Random rnd = new Random(SEED);

        List<Banner> banners = new ArrayList<>();
        for (int i = 0; i < TARGET_BANNERS; i++) {
            BannerPosition position = POSITIONS[i % POSITIONS.length];
            BannerDevice device = DEVICES[i % DEVICES.length];

            LocalDateTime startAt = now.minusDays(rnd.nextInt(30));
            LocalDateTime endAt = startAt.plusDays(5 + rnd.nextInt(56)); // 5..60 days
            // ~20% already expired: pull the end date before now.
            if (rnd.nextInt(100) < 20) {
                endAt = now.minusDays(1 + rnd.nextInt(10));
            }
            String useYn = rnd.nextInt(100) < 80 ? "Y" : "N"; // ~80% visible

            banners.add(Banner.builder()
                    .title(TITLES[i % TITLES.length] + " " + (i + 1))
                    .position(position)
                    .device(device)
                    .imageUrl(imageUrl(position, i + 1))
                    .linkUrl(LINK_URLS[i % LINK_URLS.length])
                    .startAt(startAt)
                    .endAt(endAt)
                    .sortOrder(i)
                    .useYn(useYn)
                    .createdAt(startAt)
                    .build());
        }
        bannerRepository.saveAll(banners);
        log.info("Seeded {} banners", banners.size());
    }

    /**
     * Content-tab promo banners shown atop the user-site video/music tabs. Gradient text promos
     * (no image). Idempotent: seeded once when no tab-targeted banner exists yet.
     */
    private void seedTabPromos() {
        boolean hasTabPromo = bannerRepository.findAll().stream()
                .anyMatch(banner -> banner.getTargetType() != null);
        if (hasTabPromo) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Banner> promos = new ArrayList<>();
        promos.add(tabPromo("이번 주 예배 실황", "주일·수요 예배를 영상으로 다시 만나요",
                BannerTarget.VIDEO, "/video", 1, now));
        promos.add(tabPromo("지난 예배 다시보기", "놓친 예배, 언제든 시청하세요",
                BannerTarget.VIDEO, "/video", 2, now));
        promos.add(tabPromo("찬양 음악 신곡", "새로 업데이트된 찬양을 들어보세요",
                BannerTarget.SOUND, "/music", 1, now));
        promos.add(tabPromo("CCM 음반 모음", "은혜로운 음반을 둘러보세요",
                BannerTarget.SOUND, "/albums", 2, now));
        bannerRepository.saveAll(promos);
        log.info("Seeded {} tab promo banners", promos.size());
    }

    private Banner tabPromo(String title, String subtitle, BannerTarget target, String linkUrl,
                            int sortOrder, LocalDateTime now) {
        return Banner.builder()
                .title(title)
                .subtitle(subtitle)
                .position(BannerPosition.MAIN_TOP)
                .device(BannerDevice.ALL)
                .targetType(target)
                .linkUrl(linkUrl)
                .startAt(now.minusDays(1))
                .sortOrder(sortOrder)
                .useYn("Y")
                .createdAt(now)
                .build();
    }

    /** Placeholder image sized per placement slot (wide for main, tall for side, square for popup). */
    private String imageUrl(BannerPosition position, int n) {
        String size = switch (position) {
            case SIDE -> "300/600";
            case POPUP -> "600/600";
            default -> "1200/300";
        };
        return "https://picsum.photos/seed/banner" + n + "/" + size;
    }
}
