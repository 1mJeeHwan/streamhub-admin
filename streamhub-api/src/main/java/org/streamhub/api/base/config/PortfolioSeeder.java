package org.streamhub.api.base.config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.album.entity.Album;
import org.streamhub.api.v1.album.entity.AlbumGenre;
import org.streamhub.api.v1.album.entity.AlbumStatus;
import org.streamhub.api.v1.album.entity.MusicSource;
import org.streamhub.api.v1.album.entity.Track;
import org.streamhub.api.v1.album.repository.AlbumRepository;
import org.streamhub.api.v1.album.repository.TrackRepository;
import org.streamhub.api.v1.donation.entity.Donation;
import org.streamhub.api.v1.donation.entity.DonationStatus;
import org.streamhub.api.v1.donation.entity.DonationType;
import org.streamhub.api.v1.donation.entity.Subscription;
import org.streamhub.api.v1.donation.entity.SubscriptionPlan;
import org.streamhub.api.v1.donation.entity.SubscriptionStatus;
import org.streamhub.api.v1.donation.repository.DonationRepository;
import org.streamhub.api.v1.donation.repository.SubscriptionPlanRepository;
import org.streamhub.api.v1.donation.repository.SubscriptionRepository;
import org.streamhub.api.v1.goods.entity.GoodsCategory;
import org.streamhub.api.v1.goods.entity.GoodsImage;
import org.streamhub.api.v1.goods.entity.GoodsItem;
import org.streamhub.api.v1.goods.entity.GoodsOption;
import org.streamhub.api.v1.goods.entity.GoodsStatus;
import org.streamhub.api.v1.goods.repository.GoodsCategoryRepository;
import org.streamhub.api.v1.goods.repository.GoodsImageRepository;
import org.streamhub.api.v1.goods.repository.GoodsItemRepository;
import org.streamhub.api.v1.goods.repository.GoodsOptionRepository;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.entity.MemberGrade;
import org.streamhub.api.v1.member.entity.PointLedger;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.member.repository.PointLedgerRepository;
import org.streamhub.api.v1.order.entity.Order;
import org.streamhub.api.v1.order.entity.OrderItem;
import org.streamhub.api.v1.order.entity.OrderReceipt;
import org.streamhub.api.v1.order.entity.OrderStatus;
import org.streamhub.api.v1.order.entity.ReceiptKind;
import org.streamhub.api.v1.member.entity.Region;
import org.streamhub.api.v1.member.repository.RegionRepository;
import org.streamhub.api.v1.order.entity.PayStatus;
import org.streamhub.api.v1.order.repository.OrderItemRepository;
import org.streamhub.api.v1.order.repository.OrderReceiptRepository;
import org.streamhub.api.v1.order.repository.OrderRepository;
import org.streamhub.api.v1.sms.SmsChannelPolicy;
import org.streamhub.api.v1.sms.entity.SmsChannel;
import org.streamhub.api.v1.sms.entity.SmsKind;
import org.streamhub.api.v1.sms.entity.SmsMessage;
import org.streamhub.api.v1.sms.entity.SmsStatus;
import org.streamhub.api.v1.sms.repository.SmsMessageRepository;
import org.streamhub.api.v1.store.entity.Store;
import org.streamhub.api.v1.store.repository.StoreRepository;

/**
 * Seeds the commerce / membership demo dataset (goods, orders, subscriptions, donations,
 * and the grace-point ledger) on top of the reference data produced by {@link DataInitializer}.
 *
 * <p>Runs after {@code DataInitializer} (which is {@code @Order(1)}) so the church / member /
 * content rows already exist. Every seed step is idempotent — it skips when its target table
 * already holds rows. Each step also draws from a per-step fixed-seed {@link Random}, so the
 * <em>shape</em> of the dataset (counts, status mix, Pareto sale distribution, line selection) is
 * reproducible across runs. The absolute timestamps are <em>not</em> fixed: every row is anchored
 * to {@link LocalDateTime#now()} at seed time, so the window rolls forward to keep "the most recent
 * {@value #WINDOW_DAYS} days of operations" current relative to today (a fresh seed tomorrow shifts
 * every date one day later). All media URLs use verified hosts only (Picsum {@code seed} URLs for
 * goods thumbnails); existing content media URLs are left untouched.
 */
@Slf4j
@Component
@org.springframework.core.annotation.Order(2)
public class PortfolioSeeder implements CommandLineRunner {

    /** Deterministic baseline window: the most recent 180 days. */
    private static final int WINDOW_DAYS = 180;

    /**
     * How many of the seeded subscriptions start within the last 7 days (one per day, offset
     * 0..N-1) so the dashboard "new subscriptions" KPI — which counts subscriptions started
     * today / this week — is non-zero. Offset 0 lands today, guaranteeing {@code current > 0}.
     */
    private static final int RECENT_SUBSCRIPTION_COUNT = 5;

    /** Per-step fixed seeds so each step is reproducible regardless of the others. */
    private static final long SEED_GOODS = 1001L;
    private static final long SEED_SUBSCRIPTIONS = 1003L;
    private static final long SEED_DONATIONS = 1004L;
    private static final long SEED_ORDERS = 1005L;

    private static final int GOODS_COUNT = 64;
    private static final int GOODS_PER_CATEGORY = 16;
    private static final int TARGET_DONATIONS = 1400;
    private static final int TARGET_ORDERS = 1700;

    /**
     * Demo shipment data (NOT real shipments). SHIPPING/DONE orders get a format-valid CJ대한통운
     * (code {@code 04}) invoice so the buy→배송조회 demo renders a timeline out of the box. The
     * numbers are clearly synthetic (a fixed {@code 6} prefix + a high running serial), so a real
     * courier API reports "invalid invoice" — the UI degrades gracefully — while the mock provider
     * returns a full sample timeline.
     */
    private static final String DEMO_CARRIER_CODE = "04";
    private static final long DEMO_INVOICE_BASE = 50_000_000_000L;

    private static final String[] CATEGORY_NAMES = {"음반", "도서", "의류", "소품"};
    private static final String[][] GOODS_NOUNS = {
            {"찬양 앨범", "워십 라이브", "성가대 음반", "어쿠스틱 찬양", "캐럴 모음", "오르간 연주집", "복음성가 베스트", "묵상 음악"},
            {"묵상집", "큐티 노트", "신앙 에세이", "설교 모음집", "성경 통독 가이드", "기도 수첩", "어린이 성경", "청년 신앙서"},
            {"워십 티셔츠", "교회 후드티", "성가대 가운", "찬양팀 조끼", "은혜 맨투맨", "선교 점퍼", "수련회 단체복", "기념 폴로셔츠"},
            {"머그컵", "에코백", "스티커 세트", "다이어리", "키링", "북마크", "펜 세트", "텀블러"},
    };
    private static final String[] GOODS_ADJ = {"프리미엄", "한정판", "베이직", "데일리", "시그니처", "클래식", "모던", "빈티지"};

    private static final String[] OPTION_LABELS = {"기본", "S", "M", "L", "XL", "블랙", "화이트", "네이비"};

    private static final String[] SURNAMES = {"김", "이", "박", "최", "정", "강", "조", "윤", "장", "임"};
    private static final String[] GIVEN_NAMES = {"민준", "서연", "도윤", "지우", "예준", "하은", "주원", "지호", "수아", "지민"};

    private static final String[] PLAN_NAMES = {"브론즈", "실버", "골드", "후원천사"};
    private static final MemberGrade[] PLAN_GRADES = {
            MemberGrade.BRONZE, MemberGrade.SILVER, MemberGrade.GOLD, MemberGrade.ANGEL
    };
    private static final long[] PLAN_PRICES = {5_000L, 15_000L, 30_000L, 50_000L};
    private static final int[] PLAN_POINT_RATES = {1, 2, 3, 5};

    // --- C3 album/store seed -----------------------------------------------

    private static final long SEED_ALBUMS = 1002L;
    private static final long SEED_STORES = 1008L;
    private static final long SEED_SMS = 1006L;

    private static final int ALBUM_COUNT = 24;
    private static final String GOODS_BRIDGE_CATEGORY = "음반";
    private static final int DEFAULT_NOTI_QTY = 5;

    /**
     * SoundHelix demo preview samples — same eight URLs as {@code DataInitializer.SAMPLE_AUDIOS}.
     * Duplicated (not shared) because each seeder owns its own constants. No external call is made;
     * the frontend mini-player streams these directly.
     */
    private static final String[] SAMPLE_AUDIOS = {
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
    };

    /** Fictional CCM team / worship-collective names (no real artists). */
    private static final String[] ALBUM_ARTISTS = {
            "은혜워십", "새벽기도밴드", "빛소리찬양팀", "한길워십", "소망싱어즈",
            "참빛성가대", "새순워십", "예수전한사람들", "강물워십", "하늘소리",
    };

    /** Synthesized album title fragments combined as "{형용} {명사}". */
    private static final String[] ALBUM_ADJ = {
            "은혜의", "새벽", "소망의", "찬양의", "빛나는", "거룩한", "영원한", "감사의",
    };
    private static final String[] ALBUM_NOUN = {
            "찬양", "워십 라이브", "고백", "예배", "찬송 모음", "기도", "노래", "묵상",
    };

    /** Fictional offline-store names ("○○직영점") and their demo coordinates (Seoul/Gyeonggi). */
    private static final String[][] STORE_SEED = {
            // {name, address, lat, lng}
            {"강남직영점", "서울특별시 강남구 데모로 12", "37.4979", "127.0276"},
            {"홍대직영점", "서울특별시 마포구 데모로 34", "37.5563", "126.9220"},
            {"잠실직영점", "서울특별시 송파구 데모로 56", "37.5133", "127.1000"},
            {"여의도직영점", "서울특별시 영등포구 데모로 78", "37.5219", "126.9245"},
            {"수원직영점", "경기도 수원시 영통구 데모로 90", "37.2636", "127.0286"},
            {"분당직영점", "경기도 성남시 분당구 데모로 11", "37.3826", "127.1186"},
            {"일산직영점", "경기도 고양시 일산동구 데모로 22", "37.6584", "126.7700"},
            {"부천직영점", "경기도 부천시 원미구 데모로 33", "37.5035", "126.7660"},
    };

    /** CUSTOM notice bodies (deterministic 공지 발송 demo). */
    private static final String[] SMS_NOTICES = {
            "[StreamHub] 부활절 특별예배 안내 (테스트발송)",
            "[StreamHub] 주일 온라인 생중계 시작 안내 (테스트발송)",
            "[StreamHub] 신규 찬양 앨범 발매 소식 (테스트발송)",
            "[StreamHub] 여름 수련회 신청 안내 (테스트발송)",
            "[StreamHub] 후원 감사 및 영수증 안내 (테스트발송)",
    };

    private final GoodsCategoryRepository goodsCategoryRepository;
    private final GoodsItemRepository goodsItemRepository;
    private final GoodsOptionRepository goodsOptionRepository;
    private final GoodsImageRepository goodsImageRepository;
    private final MemberRepository memberRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DonationRepository donationRepository;
    private final PointLedgerRepository pointLedgerRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderReceiptRepository orderReceiptRepository;
    private final AlbumRepository albumRepository;
    private final TrackRepository trackRepository;
    private final StoreRepository storeRepository;
    private final RegionRepository regionRepository;
    private final SmsMessageRepository smsMessageRepository;

    public PortfolioSeeder(
            GoodsCategoryRepository goodsCategoryRepository,
            GoodsItemRepository goodsItemRepository,
            GoodsOptionRepository goodsOptionRepository,
            GoodsImageRepository goodsImageRepository,
            MemberRepository memberRepository,
            SubscriptionPlanRepository subscriptionPlanRepository,
            SubscriptionRepository subscriptionRepository,
            DonationRepository donationRepository,
            PointLedgerRepository pointLedgerRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderReceiptRepository orderReceiptRepository,
            AlbumRepository albumRepository,
            TrackRepository trackRepository,
            StoreRepository storeRepository,
            RegionRepository regionRepository,
            SmsMessageRepository smsMessageRepository) {
        this.goodsCategoryRepository = goodsCategoryRepository;
        this.goodsItemRepository = goodsItemRepository;
        this.goodsOptionRepository = goodsOptionRepository;
        this.goodsImageRepository = goodsImageRepository;
        this.memberRepository = memberRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.donationRepository = donationRepository;
        this.pointLedgerRepository = pointLedgerRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.albumRepository = albumRepository;
        this.trackRepository = trackRepository;
        this.storeRepository = storeRepository;
        this.regionRepository = regionRepository;
        this.smsMessageRepository = smsMessageRepository;
    }

    @Override
    public void run(String... args) {
        LocalDateTime now = LocalDateTime.now();
        seedGoodsCategories(now);
        List<GoodsItem> goods = seedGoods(now);
        seedMembershipPlans(now);
        List<Subscription> subscriptions = seedSubscriptions(now);
        seedDonations(now, subscriptions);
        seedOrders(now, goods);
        // C3 (CCM commerce) — albums/tracks (+ on-sale GOODS_ITEM bridge) and offline stores.
        seedAlbums(now);
        seedStores(now);
        // C4 (payment seam) — backfill MOCK approval onto existing PAID+ orders and their PAY receipts.
        seedPaymentBackfill();
        // C6 (SMS seam) — derive a deterministic send history from seeded orders/donations.
        seedSmsMessages(now);
    }

    // ---------------------------------------------------------------------
    // 1. Goods categories
    // ---------------------------------------------------------------------

    /** Seeds the four root goods categories (음반/도서/의류/소품). */
    private void seedGoodsCategories(LocalDateTime now) {
        if (goodsCategoryRepository.count() > 0) {
            return;
        }
        List<GoodsCategory> categories = new ArrayList<>();
        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            categories.add(GoodsCategory.builder()
                    .parentId(null)
                    .name(CATEGORY_NAMES[i])
                    .depth(1)
                    .sort(i + 1)
                    .useYn("Y")
                    .createdAt(now.minusDays(WINDOW_DAYS))
                    .build());
        }
        goodsCategoryRepository.saveAll(categories);
        log.info("Seeded {} goods categories", categories.size());
    }

    // ---------------------------------------------------------------------
    // 2. Goods (64 items, Pareto sale distribution, options & images)
    // ---------------------------------------------------------------------

    /**
     * Seeds 64 goods items (16 per category) with a Pareto sale-count distribution, a few
     * sold-out / low-stock items, deterministic Picsum thumbnails, and per-item options/images.
     * Returns the persisted items so {@link #seedOrders} can weight line selection by sale count.
     */
    private List<GoodsItem> seedGoods(LocalDateTime now) {
        if (goodsItemRepository.count() > 0) {
            return goodsItemRepository.findAll();
        }
        List<Long> categoryIds = goodsCategoryRepository.findAllByOrderBySortAscIdAsc().stream()
                .map(GoodsCategory::getId)
                .toList();
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        Random rnd = new Random(SEED_GOODS);

        List<GoodsItem> items = new ArrayList<>();
        for (int i = 0; i < GOODS_COUNT; i++) {
            int categoryIndex = i / GOODS_PER_CATEGORY;
            Long categoryId = categoryIds.get(categoryIndex % categoryIds.size());

            String noun = GOODS_NOUNS[categoryIndex % GOODS_NOUNS.length][i % GOODS_NOUNS[0].length];
            String adj = GOODS_ADJ[(i / 3) % GOODS_ADJ.length];
            String name = adj + " " + noun;
            String code = String.format("GD%04d", i + 1);

            // Price 8,000 ~ 45,000 KRW, rounded to the nearest 1,000.
            long price = (8_000L + (long) (rnd.nextDouble() * 37_000L)) / 1_000L * 1_000L;
            // List price 1.0x ~ 1.4x of price, rounded to 1,000.
            long listPrice = Math.round(price * (1.0 + rnd.nextInt(41) / 100.0) / 1_000.0) * 1_000L;

            int notiQty = 5;
            int stock;
            String soldOut;
            if (i % 24 == 0 || i % 37 == 0) {
                stock = 0;
                soldOut = "Y";
            } else if (i % 11 == 0) {
                stock = rnd.nextInt(notiQty); // low-stock warning: stock < notiQty
                soldOut = "N";
            } else {
                stock = 20 + rnd.nextInt(180);
                soldOut = "N";
            }

            // Pareto: the first 12 items concentrate sales; the long tail sells little or nothing.
            int saleCount = i < 12 ? 80 + rnd.nextInt(321) : rnd.nextInt(16);

            GoodsStatus status = (i % 10 == 9) ? GoodsStatus.PAUSED : GoodsStatus.SELLING;
            String useYn = status == GoodsStatus.SELLING ? "Y" : "N";

            LocalDateTime createdAt = now.minusDays((long) i * 2).minusHours(i % 24);

            String badges = buildBadges(i, saleCount, price, listPrice, now, createdAt);

            // Verified deterministic host: Picsum seed URL (always HTTP 200).
            String thumbnailKey = "https://picsum.photos/seed/goods" + (i + 1) + "/400/400";

            items.add(GoodsItem.builder()
                    .categoryId(categoryId)
                    .name(name)
                    .code(code)
                    .description(name + " — 데모 상품입니다.")
                    .price(price)
                    .listPrice(listPrice)
                    .stock(stock)
                    .notiQty(notiQty)
                    .soldOut(soldOut)
                    .useYn(useYn)
                    .status(status)
                    .saleCount(saleCount)
                    .viewCount((long) ((i * 137) % 5000))
                    .thumbnailKey(thumbnailKey)
                    .badges(badges)
                    .createdAt(createdAt)
                    .build());
        }
        List<GoodsItem> saved = goodsItemRepository.saveAll(items);

        seedGoodsOptionsAndImages(saved);

        log.info("Seeded {} goods items", saved.size());
        return saved;
    }

    /** Builds the comma-joined badge string from Pareto/recency/sale signals. */
    private String buildBadges(int index, int saleCount, long price, long listPrice,
                               LocalDateTime now, LocalDateTime createdAt) {
        List<String> badges = new ArrayList<>();
        if (index < 12 && saleCount >= 80) {
            badges.add("HIT");
        }
        if (createdAt.isAfter(now.minusDays(7))) {
            badges.add("NEW");
        }
        if (listPrice > price) {
            badges.add("SALE");
        }
        return badges.isEmpty() ? null : String.join(",", badges);
    }

    /** Seeds 0-2 options (apparel/소품 get options) and 1-3 gallery images per item. */
    private void seedGoodsOptionsAndImages(List<GoodsItem> items) {
        List<GoodsOption> options = new ArrayList<>();
        List<GoodsImage> images = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            GoodsItem item = items.get(i);
            // Apparel (category index 2) and 소품 (3) carry size/color options.
            int categoryIndex = i / GOODS_PER_CATEGORY;
            if (categoryIndex >= 2) {
                int optionCount = 2 + (i % 2); // 2 or 3 options
                for (int o = 0; o < optionCount; o++) {
                    options.add(GoodsOption.builder()
                            .itemId(item.getId())
                            .name(OPTION_LABELS[(i + o) % OPTION_LABELS.length])
                            .optionType(categoryIndex == 2 ? "사이즈" : "종류")
                            .extraPrice((long) (o * 1_000))
                            .stock(item.getStock() / (optionCount + 1))
                            .useYn("Y")
                            .sort(o + 1)
                            .build());
                }
            }
            int imageCount = 1 + (i % 3); // 1..3 gallery images
            for (int g = 0; g < imageCount; g++) {
                images.add(GoodsImage.builder()
                        .itemId(item.getId())
                        .s3Key("https://picsum.photos/seed/goods" + (i + 1) + "g" + g + "/600/600")
                        .sort(g + 1)
                        .build());
            }
        }
        goodsOptionRepository.saveAll(options);
        goodsImageRepository.saveAll(images);
        log.info("Seeded {} goods options, {} goods images", options.size(), images.size());
    }

    // ---------------------------------------------------------------------
    // 3. Membership / recurring-donation plans
    // ---------------------------------------------------------------------

    /** Seeds the four membership plans (브론즈/실버/골드/후원천사). */
    private void seedMembershipPlans(LocalDateTime now) {
        if (subscriptionPlanRepository.count() > 0) {
            return;
        }
        List<SubscriptionPlan> plans = new ArrayList<>();
        for (int i = 0; i < PLAN_NAMES.length; i++) {
            plans.add(SubscriptionPlan.builder()
                    .name(PLAN_NAMES[i])
                    .grade(PLAN_GRADES[i])
                    .price(PLAN_PRICES[i])
                    .periodMonths(1)
                    .pointRate(PLAN_POINT_RATES[i])
                    .benefit(PLAN_NAMES[i] + " 등급 전용 혜택")
                    .active("Y")
                    .createdAt(now.minusDays(WINDOW_DAYS))
                    .build());
        }
        subscriptionPlanRepository.saveAll(plans);
        log.info("Seeded {} membership plans", plans.size());
    }

    // ---------------------------------------------------------------------
    // 4. Subscriptions (lifecycle mix) + member grade backfill
    // ---------------------------------------------------------------------

    /**
     * Grants subscriptions to ~40% of members with a 65/20/15 ACTIVE/PAUSED/CANCELED mix and a
     * varied cycle count, and backfills each subscriber's {@link Member#getGrade()} from their plan.
     * Returns the persisted subscriptions so {@link #seedDonations} can replay their billing cycles.
     */
    private List<Subscription> seedSubscriptions(LocalDateTime now) {
        if (subscriptionRepository.count() > 0) {
            return subscriptionRepository.findAll();
        }
        List<Member> members = memberRepository.findAll();
        List<SubscriptionPlan> plans = subscriptionPlanRepository.findAllByOrderByPriceAscIdAsc();
        if (members.isEmpty() || plans.isEmpty()) {
            return List.of();
        }
        Random rnd = new Random(SEED_SUBSCRIPTIONS);

        List<Subscription> subscriptions = new ArrayList<>();
        List<Member> gradedMembers = new ArrayList<>();
        int subscriberSeq = 0; // running index over subscribing members, drives the recent cohort
        for (int i = 0; i < members.size(); i++) {
            // ~40% of members subscribe (deterministic stride).
            if (i % 5 >= 2) {
                continue;
            }
            Member member = members.get(i);
            SubscriptionPlan plan = plans.get(i % plans.size());

            int r = rnd.nextInt(100);
            SubscriptionStatus status = r < 65 ? SubscriptionStatus.ACTIVE
                    : r < 85 ? SubscriptionStatus.PAUSED
                    : SubscriptionStatus.CANCELED;

            int cycleNo = 1 + rnd.nextInt(7); // 1..7 cycles billed so far
            // Draw the historical jitter unconditionally so the RNG stream stays identical for
            // every later subscription regardless of the recent-cohort override below.
            int startJitterDays = rnd.nextInt(28);
            int cancelJitterDays = rnd.nextInt(20);

            boolean recent = subscriberSeq < RECENT_SUBSCRIPTION_COUNT;
            if (recent) {
                // Fresh subscriptions: started within the last week (offset 0 == today), always
                // ACTIVE on their first cycle so the "new subscriptions" KPI window is non-zero.
                status = SubscriptionStatus.ACTIVE;
                cycleNo = 1;
            }

            LocalDateTime startedAt;
            if (recent) {
                // Anchor at the start of the day N days ago (offset 0 == today's 00:00), nudged to
                // 09:00 only when that still precedes now — never lands in the future.
                LocalDateTime dayStart = now.minusDays(subscriberSeq).toLocalDate().atStartOfDay();
                LocalDateTime atNine = dayStart.plusHours(9);
                startedAt = atNine.isAfter(now) ? dayStart : atNine;
            } else {
                startedAt = now.minusMonths(cycleNo).minusDays(startJitterDays);
            }
            LocalDateTime nextBillingAt = status == SubscriptionStatus.CANCELED
                    ? null
                    : startedAt.plusMonths(cycleNo + 1L);
            LocalDateTime canceledAt = status == SubscriptionStatus.CANCELED
                    ? startedAt.plusMonths(cycleNo).plusDays(cancelJitterDays)
                    : null;
            String billingKeyMasked = String.format("bk_****%04d", 1000 + i);
            subscriberSeq++;

            subscriptions.add(Subscription.builder()
                    .memberId(member.getId())
                    .planId(plan.getId())
                    .billingKeyMasked(billingKeyMasked)
                    .status(status)
                    .cycleNo(cycleNo)
                    .nextBillingAt(nextBillingAt)
                    .startedAt(startedAt)
                    .canceledAt(canceledAt)
                    .createdAt(startedAt)
                    .build());

            // Backfill grade from the plan (point balance is set later from the ledger).
            member.changeGrade(plan.getGrade());
            gradedMembers.add(member);
        }
        List<Subscription> saved = subscriptionRepository.saveAll(subscriptions);
        memberRepository.saveAll(gradedMembers);
        log.info("Seeded {} subscriptions ({} members graded)", saved.size(), gradedMembers.size());
        return saved;
    }

    // ---------------------------------------------------------------------
    // 5. Donations (subscription cycles + one-off) + point ledger accrual
    // ---------------------------------------------------------------------

    /**
     * Seeds ~1,400 donations: subscription billing cycles replayed from each subscription's
     * {@code cycleNo} plus one-off gifts, with one-off donations accruing grace points into the
     * append-only ledger and updating the member's cached balance.
     */
    private void seedDonations(LocalDateTime now, List<Subscription> subscriptions) {
        if (donationRepository.count() > 0) {
            return;
        }
        List<Member> members = memberRepository.findAll();
        List<SubscriptionPlan> plans = subscriptionPlanRepository.findAllByOrderByPriceAscIdAsc();
        if (members.isEmpty() || plans.isEmpty()) {
            return;
        }
        Random rnd = new Random(SEED_DONATIONS);

        // 5a. Subscription billing cycles — deterministic monthly rows per subscription.
        List<Donation> subscriptionDonations = new ArrayList<>();
        for (Subscription sub : subscriptions) {
            SubscriptionPlan plan = plans.stream()
                    .filter(p -> p.getId().equals(sub.getPlanId()))
                    .findFirst()
                    .orElse(plans.get(0));
            for (int c = 1; c <= sub.getCycleNo(); c++) {
                LocalDateTime paidAt = sub.getStartedAt().plusMonths(c - 1L)
                        .withHour(9 + rnd.nextInt(13)).withMinute(rnd.nextInt(60));
                subscriptionDonations.add(Donation.builder()
                        .memberId(sub.getMemberId())
                        .subscriptionId(sub.getId())
                        .type(DonationType.SUBSCRIPTION)
                        .amount(plan.getPrice())
                        .cycleNo(c)
                        .status(DonationStatus.PAID)
                        .pointAwarded(plan.getPrice() * plan.getPointRate() / 100)
                        .testMode("Y")
                        .paidAt(paidAt)
                        .build());
            }
        }
        donationRepository.saveAll(subscriptionDonations);

        // 5b. One-off donations — fill up to the target total, time-distributed (uptrend + spikes).
        int onceTarget = Math.max(0, TARGET_DONATIONS - subscriptionDonations.size());
        List<Donation> onceDonations = new ArrayList<>();
        for (int i = 0; i < onceTarget; i++) {
            Member member = members.get((i * 7) % members.size());
            long amount = (5_000L + (long) (rnd.nextDouble() * 95_000L)) / 1_000L * 1_000L;
            LocalDateTime paidAt = distributedDateTime(now, rnd, true);
            onceDonations.add(Donation.builder()
                    .memberId(member.getId())
                    .subscriptionId(null)
                    .type(DonationType.ONCE)
                    .amount(amount)
                    .cycleNo(null)
                    .status(DonationStatus.PAID)
                    .pointAwarded(amount / 100) // 1% grace-point accrual
                    .testMode("Y")
                    .paidAt(paidAt)
                    .build());
        }
        List<Donation> savedOnce = donationRepository.saveAll(onceDonations);

        // 5c. Point ledger accrual for one-off donations (running balanceAfter per member).
        seedPointLedgerForDonations(savedOnce, members);

        log.info("Seeded {} donations ({} subscription cycles, {} one-off)",
                subscriptionDonations.size() + savedOnce.size(),
                subscriptionDonations.size(), savedOnce.size());
    }

    /** Appends one accrual ledger row per one-off donation and refreshes the member balance cache. */
    private void seedPointLedgerForDonations(List<Donation> donations, List<Member> members) {
        java.util.Map<Long, Long> running = new java.util.HashMap<>();
        java.util.Map<Long, Member> memberById = new java.util.HashMap<>();
        for (Member m : members) {
            memberById.put(m.getId(), m);
        }
        List<PointLedger> ledgers = new ArrayList<>();
        for (Donation donation : donations) {
            long delta = donation.getPointAwarded();
            if (delta <= 0) {
                continue;
            }
            long balanceAfter = running.merge(donation.getMemberId(), delta, Long::sum);
            ledgers.add(PointLedger.builder()
                    .memberId(donation.getMemberId())
                    .delta(delta)
                    .balanceAfter(balanceAfter)
                    .reason("후원 적립")
                    .donationId(donation.getId())
                    .createdAt(donation.getPaidAt())
                    .build());
        }
        pointLedgerRepository.saveAll(ledgers);

        // Sync each member's cached point balance to their final running balance.
        List<Member> updated = new ArrayList<>();
        for (java.util.Map.Entry<Long, Long> entry : running.entrySet()) {
            Member member = memberById.get(entry.getKey());
            if (member != null) {
                member.addPoint(entry.getValue());
                updated.add(member);
            }
        }
        memberRepository.saveAll(updated);
        log.info("Seeded {} point-ledger rows ({} member balances synced)", ledgers.size(), updated.size());
    }

    // ---------------------------------------------------------------------
    // 6. Orders (status distribution, time series, receipts)
    // ---------------------------------------------------------------------

    /**
     * Seeds ~1,700 orders with a 70/15/10/5 DONE/SHIPPING·READY/PAID/CANCEL·RETURN status mix,
     * an uptrending + weekend-spiked time series, 1-3 sale-count-weighted line items each, and a
     * payment receipt (plus a refund receipt for cancel/return).
     */
    private void seedOrders(LocalDateTime now, List<GoodsItem> goods) {
        if (orderRepository.count() > 0) {
            return;
        }
        List<Member> members = memberRepository.findAll();
        if (members.isEmpty() || goods.isEmpty()) {
            return;
        }
        Random rnd = new Random(SEED_ORDERS);

        // Build a sale-count-weighted index so popular goods are picked more often (Pareto).
        int[] weightedGoodsIndex = buildWeightedGoodsIndex(goods);

        // Per-day running sequence for the order number suffix.
        java.util.Map<String, Integer> daySeq = new java.util.HashMap<>();

        for (int i = 0; i < TARGET_ORDERS; i++) {
            Member member = members.get((i * 13) % members.size());
            LocalDateTime orderedAt = distributedDateTime(now, rnd, false);

            int lineCount = 1 + rnd.nextInt(3); // 1..3 lines
            List<OrderItem> lines = new ArrayList<>();
            long goodsTotal = 0L;
            for (int l = 0; l < lineCount; l++) {
                GoodsItem g = goods.get(weightedGoodsIndex[rnd.nextInt(weightedGoodsIndex.length)]);
                int qty = 1 + rnd.nextInt(3);
                long unitPrice = g.getPrice();
                long lineTotal = unitPrice * qty;
                goodsTotal += lineTotal;
                lines.add(OrderItem.builder()
                        .goodsId(g.getId())
                        .goodsName(g.getName())
                        .unitPrice(unitPrice)
                        .qty(qty)
                        .lineTotal(lineTotal)
                        .build());
            }

            long shipFee = goodsTotal >= 50_000L ? 0L : 3_000L;
            long couponDiscount = (i % 7 == 0) ? 2_000L : 0L;
            long pointUsed = (i % 9 == 0) ? Math.min(goodsTotal, 1_000L * (1 + rnd.nextInt(5))) : 0L;
            long total = Math.max(0L, goodsTotal + shipFee - couponDiscount - pointUsed);

            String payMethod = (i % 4 == 0) ? "BANK" : "CARD";
            OrderStatus status = resolveStatus(now, orderedAt, rnd);

            // Demo (NOT real) shipment: only SHIPPING/DONE orders carry an invoice, so the buy→배송조회
            // demo shows a timeline. Format-valid but clearly synthetic; courier API → "invalid
            // invoice" (graceful), mock provider → full sample timeline. Idempotent by construction:
            // this is a one-time seed (guarded by count() above), and the value is only set for
            // shipped orders — pre-shipment orders stay null so the pending state is exercised too.
            String trackingNo = demoTrackingNo(status, i);
            // Store the carrier CODE (04 = CJ대한통운) so the admin carrier dropdown and the
            // delivery-tracking API can use it directly (C8). Display name resolved from the list.
            String shipCompany = trackingNo != null ? DEMO_CARRIER_CODE : null;

            String orderNo = buildOrderNo(orderedAt, daySeq);

            String name = SURNAMES[i % SURNAMES.length]
                    + GIVEN_NAMES[(i / SURNAMES.length) % GIVEN_NAMES.length];
            String maskedPhone = String.format("010-****-%04d", 1000 + (i % 9000));

            Order order = Order.builder()
                    .orderNo(orderNo)
                    .memberId(member.getId())
                    .status(status)
                    .orderedName(name)
                    .orderedPhone(maskedPhone)
                    .receiverName(name)
                    .receiverPhone(maskedPhone)
                    .receiverAddr("서울특별시 강남구 데모로 " + (1 + i % 200) + "길 " + (1 + i % 50))
                    .goodsTotal(goodsTotal)
                    .shipFee(shipFee)
                    .couponDiscount(couponDiscount)
                    .pointUsed(pointUsed)
                    .total(total)
                    .payMethod(payMethod)
                    .trackingNo(trackingNo)
                    .shipCompany(shipCompany)
                    .orderedAt(orderedAt)
                    .build();
            Order savedOrder = orderRepository.save(order);

            // Persist lines now that the order id exists.
            List<OrderItem> boundLines = new ArrayList<>();
            for (OrderItem line : lines) {
                boundLines.add(OrderItem.builder()
                        .orderId(savedOrder.getId())
                        .goodsId(line.getGoodsId())
                        .goodsName(line.getGoodsName())
                        .unitPrice(line.getUnitPrice())
                        .qty(line.getQty())
                        .lineTotal(line.getLineTotal())
                        .build());
            }
            orderItemRepository.saveAll(boundLines);

            // Receipts: one PAY, plus REFUND for cancel/return.
            List<OrderReceipt> receipts = new ArrayList<>();
            receipts.add(OrderReceipt.builder()
                    .orderId(savedOrder.getId())
                    .kind(ReceiptKind.PAY)
                    .amount(total)
                    .method(payMethod)
                    .memo("결제 완료")
                    .createdAt(orderedAt)
                    .build());
            if (status == OrderStatus.CANCEL || status == OrderStatus.RETURN) {
                receipts.add(OrderReceipt.builder()
                        .orderId(savedOrder.getId())
                        .kind(ReceiptKind.REFUND)
                        .amount(total)
                        .method(payMethod)
                        .memo(status == OrderStatus.CANCEL ? "주문 취소 환불" : "반품 환불")
                        .createdAt(orderedAt.plusDays(1 + (i % 3)))
                        .build());
            }
            orderReceiptRepository.saveAll(receipts);
        }
        log.info("Seeded {} orders (with items and receipts)", TARGET_ORDERS);
    }

    /**
     * Demo (NOT real) tracking number for a shipped order, or {@code null} when the order has not
     * shipped yet. Format-valid ({@code 6} + 11-digit running serial) so the buy→배송조회 demo shows
     * a timeline (mock provider) or degrades gracefully (real courier reports "invalid invoice").
     */
    private String demoTrackingNo(OrderStatus status, int index) {
        if (status != OrderStatus.SHIPPING && status != OrderStatus.DONE) {
            return null;
        }
        return String.format("6%011d", DEMO_INVOICE_BASE + index);
    }

    /** Builds a sale-count-weighted goods index array (Pareto line-item selection). */
    private int[] buildWeightedGoodsIndex(List<GoodsItem> goods) {
        List<Integer> bag = new ArrayList<>();
        for (int g = 0; g < goods.size(); g++) {
            int weight = 1 + goods.get(g).getSaleCount() / 20; // popular goods appear more often
            for (int w = 0; w < weight; w++) {
                bag.add(g);
            }
        }
        int[] arr = new int[bag.size()];
        for (int k = 0; k < bag.size(); k++) {
            arr[k] = bag.get(k);
        }
        return arr;
    }

    /** Builds the {@code yyyyMMdd-NNNNNN} order number with a per-day running sequence. */
    private String buildOrderNo(LocalDateTime orderedAt, java.util.Map<String, Integer> daySeq) {
        String day = String.format("%04d%02d%02d",
                orderedAt.getYear(), orderedAt.getMonthValue(), orderedAt.getDayOfMonth());
        int seq = daySeq.merge(day, 1, Integer::sum);
        return day + "-" + String.format("%06d", seq);
    }

    /**
     * Resolves order status from the 70/15/10/5 distribution, forbidding DONE for orders placed in
     * the last 3 days (shipping lead-time realism → PLACED/PAID/READY instead).
     */
    private OrderStatus resolveStatus(LocalDateTime now, LocalDateTime orderedAt, Random rnd) {
        boolean recent = orderedAt.isAfter(now.minusDays(3));
        int r = rnd.nextInt(100);
        if (r < 70) {
            if (recent) {
                int s = rnd.nextInt(3);
                return s == 0 ? OrderStatus.PLACED : s == 1 ? OrderStatus.PAID : OrderStatus.READY;
            }
            return OrderStatus.DONE;
        }
        if (r < 85) {
            return rnd.nextBoolean() ? OrderStatus.SHIPPING : OrderStatus.READY;
        }
        if (r < 95) {
            return OrderStatus.PAID;
        }
        return rnd.nextBoolean() ? OrderStatus.CANCEL : OrderStatus.RETURN;
    }

    // ---------------------------------------------------------------------
    // Time-series distribution helper
    // ---------------------------------------------------------------------

    /**
     * Produces a {@link LocalDateTime} within the last {@link #WINDOW_DAYS} days, biased toward
     * recent dates ({@code sqrt} weighting → uptrend) with weekend density boosts and, for
     * donations, special-offering-day spikes (mid-month & month-end).
     */
    private LocalDateTime distributedDateTime(LocalDateTime now, Random rnd, boolean donation) {
        // 1 - sqrt(u) concentrates daysAgo near 0 → density highest on recent days (true uptrend).
        int daysAgo = (int) Math.round((1.0 - Math.sqrt(rnd.nextDouble())) * WINDOW_DAYS);
        LocalDateTime when = now.minusDays(daysAgo);

        // Weekend spike: with extra probability, nudge onto the nearest weekend day.
        DayOfWeek dow = when.getDayOfWeek();
        boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        if (!isWeekend && rnd.nextInt(100) < 33) {
            int shift = (dow.getValue() >= DayOfWeek.THURSDAY.getValue())
                    ? (DayOfWeek.SUNDAY.getValue() - dow.getValue())
                    : -(dow.getValue() % 7);
            when = when.plusDays(shift);
        }

        // Special-offering spike (donations only): pull onto mid-month / month-end days.
        if (donation && rnd.nextInt(100) < 25) {
            int[] offeringDays = {14, 15, 28};
            int target = offeringDays[rnd.nextInt(offeringDays.length)];
            int maxDay = when.toLocalDate().lengthOfMonth();
            when = when.withDayOfMonth(Math.min(target, maxDay));
        }

        when = when.withHour(9 + rnd.nextInt(14)).withMinute(rnd.nextInt(60)).withSecond(0).withNano(0);
        // Weekend / offering-day nudges can land slightly in the future — clamp to earlier today.
        return when.isAfter(now)
                ? now.minusHours(1 + rnd.nextInt(8)).withSecond(0).withNano(0)
                : when;
    }

    // ---------------------------------------------------------------------
    // 7. CCM albums + tracks (+ on-sale GOODS_ITEM bridge) — C3
    // ---------------------------------------------------------------------

    /**
     * Seeds {@value #ALBUM_COUNT} CCM albums with 5-10 tracks each. On-sale albums create a 1:1
     * bridge {@code GOODS_ITEM} (category "음반") so the order domain is never touched — the same
     * pattern {@code AlbumService.syncGoodsBridge} uses. Preview URLs reuse the SoundHelix samples.
     * Idempotent: skips when albums already exist; deterministic via {@link #SEED_ALBUMS}.
     */
    private void seedAlbums(LocalDateTime now) {
        if (albumRepository.count() > 0) {
            return;
        }
        Long bridgeCategoryId = bridgeCategoryId();
        if (bridgeCategoryId == null) {
            log.warn("Skipping album seed: '{}' goods category not found", GOODS_BRIDGE_CATEGORY);
            return;
        }
        Random rnd = new Random(SEED_ALBUMS);
        AlbumGenre[] genres = AlbumGenre.values();

        int totalTracks = 0;
        int onSaleCount = 0;
        for (int i = 0; i < ALBUM_COUNT; i++) {
            AlbumGenre genre = genres[i % genres.length];
            String artist = ALBUM_ARTISTS[i % ALBUM_ARTISTS.length];
            String title = ALBUM_ADJ[i % ALBUM_ADJ.length] + " " + ALBUM_NOUN[(i / 2) % ALBUM_NOUN.length]
                    + " Vol." + (1 + i % 5);
            LocalDate releaseDate = now.minusDays((i * 23) % 900).toLocalDate();
            AlbumStatus status = (i % 9 == 0) ? AlbumStatus.HIDDEN : AlbumStatus.ON_SALE;
            // Reuse a verified Picsum cover (PortfolioSeeder already uses this host for goods).
            String coverKey = "https://picsum.photos/seed/album" + (i + 1) + "/500/500";
            LocalDateTime createdAt = now.minusDays((long) i * 5).minusHours(i % 24);

            Album album = albumRepository.save(Album.builder()
                    .title(title)
                    .artist(artist)
                    .label(artist + " 레이블")
                    .genre(genre)
                    .releaseDate(releaseDate)
                    .description(title + " — 데모 음반입니다. (실제 음원사 데이터 아님)")
                    .coverKey(coverKey)
                    .status(status)
                    .viewCount((long) ((i * 91) % 4000))
                    .source(MusicSource.SEED)
                    .createdAt(createdAt)
                    .build());

            // On-sale albums get a sale-bridge GOODS_ITEM (price 9,000~22,000, 1,000 단위).
            if (status == AlbumStatus.ON_SALE) {
                long price = (9_000L + (long) (rnd.nextDouble() * 13_000L)) / 1_000L * 1_000L;
                int stock = 20 + rnd.nextInt(180);
                GoodsItem bridge = goodsItemRepository.save(GoodsItem.builder()
                        .categoryId(bridgeCategoryId)
                        .name(album.getTitle())
                        .code(String.format("ALB%03d", i + 1))
                        .description(album.getDescription())
                        .price(price)
                        .stock(stock)
                        .notiQty(DEFAULT_NOTI_QTY)
                        .soldOut("N")
                        .useYn("Y")
                        .status(GoodsStatus.SELLING)
                        .saleCount(rnd.nextInt(60))
                        .viewCount(album.getViewCount())
                        .thumbnailKey(coverKey)
                        .createdAt(createdAt)
                        .build());
                album.linkGoodsItem(bridge.getId());
                onSaleCount++;
            }

            int trackCount = 5 + (i % 6); // 5..10 tracks
            List<Track> tracks = new ArrayList<>();
            for (int no = 1; no <= trackCount; no++) {
                int audioIdx = (int) ((album.getId() * 7 + no) % SAMPLE_AUDIOS.length);
                tracks.add(Track.builder()
                        .albumId(album.getId())
                        .trackNo(no)
                        .title(album.getTitle() + " - " + no + "번 트랙")
                        .durationSec(180 + (no * 20) % 120)
                        .previewUrl(SAMPLE_AUDIOS[audioIdx])
                        .previewStartSec((no * 15) % 60)
                        .previewLengthSec(30)
                        .source(MusicSource.SEED)
                        .build());
            }
            trackRepository.saveAll(tracks);
            album.syncTrackCount(trackCount);
            albumRepository.save(album);
            totalTracks += trackCount;
        }
        log.info("Seeded {} albums ({} on-sale w/ goods bridge), {} tracks",
                ALBUM_COUNT, onSaleCount, totalTracks);
    }

    /** Resolves the "음반" bridge category id, or null when the goods categories aren't seeded. */
    private Long bridgeCategoryId() {
        return goodsCategoryRepository.findAllByOrderBySortAscIdAsc().stream()
                .filter(c -> GOODS_BRIDGE_CATEGORY.equals(c.getName()))
                .map(GoodsCategory::getId)
                .findFirst()
                .orElse(null);
    }

    // ---------------------------------------------------------------------
    // 8. Offline retail stores — C3 store-finder
    // ---------------------------------------------------------------------

    /**
     * Seeds {@value #STORE_SEED}.length offline stores with demo coordinates. {@code regionId} is
     * resolved deterministically from the seeded REGION rows; all values are fictional (PII guard).
     * Idempotent: skips when stores already exist.
     */
    private void seedStores(LocalDateTime now) {
        if (storeRepository.count() > 0) {
            return;
        }
        List<Region> regions = regionRepository.findAll();
        if (regions.isEmpty()) {
            return;
        }
        List<Store> stores = new ArrayList<>();
        for (int i = 0; i < STORE_SEED.length; i++) {
            String[] row = STORE_SEED[i];
            Region region = regions.get(i % regions.size());
            String phone = String.format("02-0000-00%02d", i + 1); // demo virtual number
            String useYn = (i == STORE_SEED.length - 1) ? "N" : "Y"; // last one hidden (비노출 데모)
            stores.add(Store.builder()
                    .regionId(region.getId())
                    .name(row[0])
                    .address(row[1])
                    .phone(phone)
                    .lat(new BigDecimal(row[2]))
                    .lng(new BigDecimal(row[3]))
                    .openHours("매일 10:00~20:00")
                    .useYn(useYn)
                    .createdAt(now.minusDays((long) i * 10))
                    .build());
        }
        storeRepository.saveAll(stores);
        log.info("Seeded {} stores", stores.size());
    }

    // ---------------------------------------------------------------------
    // 9. Payment backfill (C4 seam) — MOCK approval on existing PAID+ orders
    // ---------------------------------------------------------------------

    /**
     * Backfills a MOCK payment approval onto every already-seeded order in a PAID-or-later status:
     * {@code payProvider="MOCK"}, {@code payStatus=APPROVED}, and the latest PAY receipt gets
     * {@code provider="MOCK"}, {@code txnId="MOCK-{orderNo}-1"}. Idempotent — skips orders already
     * marked APPROVED — so re-runs are no-ops. No real PG call is made.
     */
    private void seedPaymentBackfill() {
        List<Order> orders = orderRepository.findAll();
        List<Order> updatedOrders = new ArrayList<>();
        List<OrderReceipt> updatedReceipts = new ArrayList<>();
        for (Order order : orders) {
            if (!isPaidOrLater(order.getStatus()) || order.getPayStatus() == PayStatus.APPROVED) {
                continue;
            }
            order.applyPayRequest("MOCK", null);
            order.applyPayApprove();
            updatedOrders.add(order);

            String txnId = "MOCK-" + order.getOrderNo() + "-1";
            orderReceiptRepository.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId()).stream()
                    .filter(r -> r.getKind() == ReceiptKind.PAY)
                    .reduce((first, second) -> second) // latest PAY receipt
                    .ifPresent(receipt -> {
                        receipt.setProviderTxn("MOCK", txnId);
                        updatedReceipts.add(receipt);
                    });
        }
        if (updatedOrders.isEmpty()) {
            return;
        }
        orderRepository.saveAll(updatedOrders);
        orderReceiptRepository.saveAll(updatedReceipts);
        log.info("Backfilled MOCK payment on {} orders ({} PAY receipts)",
                updatedOrders.size(), updatedReceipts.size());
    }

    /** True when the order has been paid (PAID/READY/SHIPPING/DONE) — cancel/return excluded. */
    private boolean isPaidOrLater(OrderStatus status) {
        return status == OrderStatus.PAID || status == OrderStatus.READY
                || status == OrderStatus.SHIPPING || status == OrderStatus.DONE;
    }

    // ---------------------------------------------------------------------
    // 10. SMS send history (C6 seam) — derived from seeded orders/donations
    // ---------------------------------------------------------------------

    /**
     * Seeds an SMS send history (~120 rows) derived deterministically from seeded orders and
     * one-off donations, plus a handful of CUSTOM notices. The mock sender never performs a real
     * send — every row is {@code testMode="Y"}, {@code sender="MOCK"}, {@code status=SENT}, and the
     * recipient number is masked. Idempotent: skips when messages already exist.
     */
    private void seedSmsMessages(LocalDateTime now) {
        if (smsMessageRepository.count() > 0) {
            return;
        }
        Random rnd = new Random(SEED_SMS);
        List<SmsMessage> messages = new ArrayList<>();

        // Order-derived: PAID+ → ORDER_PAID, SHIPPING/DONE w/ tracking → ORDER_SHIPPING.
        List<Order> orders = orderRepository.findAll();
        for (int i = 0; i < orders.size() && messages.size() < 100; i++) {
            Order order = orders.get(i);
            if (i % 3 != 0 || !isPaidOrLater(order.getStatus())) {
                continue; // sample ~1/3 of paid orders to keep the history readable
            }
            boolean shipped = (order.getStatus() == OrderStatus.SHIPPING
                    || order.getStatus() == OrderStatus.DONE) && order.getTrackingNo() != null;
            SmsKind kind = shipped ? SmsKind.ORDER_SHIPPING : SmsKind.ORDER_PAID;
            String content = shipped
                    ? "[StreamHub] 주문하신 상품이 발송되었습니다. 운송장 " + order.getTrackingNo() + " (테스트발송)"
                    : "[StreamHub] 결제가 완료되었습니다. 주문번호 " + order.getOrderNo() + " (테스트발송)";
            messages.add(buildSms(content, kind, maskedTo(rnd), order.getMemberId(),
                    "ORDER", String.valueOf(order.getId()), order.getOrderedAt()));
        }

        // Donation-derived: one-off donations → DONATION_ONCE receipts.
        List<Donation> donations = donationRepository.findAll();
        for (int i = 0; i < donations.size() && messages.size() < 100; i++) {
            Donation donation = donations.get(i);
            if (donation.getType() != DonationType.ONCE || i % 17 != 0) {
                continue;
            }
            String content = "[StreamHub] 후원해 주셔서 감사합니다. 후원금 "
                    + String.format("%,d", donation.getAmount()) + "원 (테스트발송)";
            messages.add(buildSms(content, SmsKind.DONATION_ONCE, maskedTo(rnd), donation.getMemberId(),
                    "DONATION", String.valueOf(donation.getId()), donation.getPaidAt()));
        }

        // CUSTOM notices: ~20 rows cycling the notice templates.
        for (int i = 0; i < 20; i++) {
            String content = SMS_NOTICES[i % SMS_NOTICES.length];
            LocalDateTime sentAt = now.minusDays(i * 2L).minusHours(i % 12);
            messages.add(buildSms(content, SmsKind.CUSTOM, maskedTo(rnd), null, null, null, sentAt));
        }

        smsMessageRepository.saveAll(messages);
        log.info("Seeded {} SMS messages", messages.size());
    }

    /** Builds one SMS log row (mock: testMode=Y, sender=MOCK, status=SENT). */
    private SmsMessage buildSms(String content, SmsKind kind, String toNumber, Long memberId,
                                String refType, String refId, LocalDateTime sentAt) {
        // Classify through the shared runtime policy (EUC-KR > 90 bytes ⇒ LMS) so a seeded row
        // carries the exact channel a live send would have produced — no UTF-8 vs EUC-KR drift.
        SmsChannel channel = SmsChannelPolicy.resolve(content);
        return SmsMessage.builder()
                .toNumber(toNumber)
                .content(content)
                .kind(kind)
                .channel(channel)
                .sender("MOCK")
                .status(SmsStatus.SENT)
                .testMode("Y")
                .memberId(memberId)
                .refType(refType)
                .refId(refId)
                .sentAt(sentAt)
                .build();
    }

    /** Deterministic masked virtual recipient number ({@code 010-{3자리}-****}). */
    private String maskedTo(Random rnd) {
        return String.format("010-%03d-****", rnd.nextInt(1000));
    }
}
