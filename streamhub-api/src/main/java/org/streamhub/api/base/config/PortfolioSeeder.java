package org.streamhub.api.base.config;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
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
import org.streamhub.api.v1.order.repository.OrderItemRepository;
import org.streamhub.api.v1.order.repository.OrderReceiptRepository;
import org.streamhub.api.v1.order.repository.OrderRepository;

/**
 * Seeds the commerce / membership demo dataset (goods, orders, subscriptions, donations,
 * and the grace-point ledger) on top of the reference data produced by {@link DataInitializer}.
 *
 * <p>Runs after {@code DataInitializer} (which is {@code @Order(1)}) so the church / member /
 * content rows already exist. Every seed step is idempotent — it skips when its target table
 * already holds rows — and fully deterministic: each step draws from a fixed-seed
 * {@link Random} so a fresh database always materialises the identical "six months of
 * operations" picture. All media URLs use verified hosts only (Picsum {@code seed} URLs for
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
            OrderReceiptRepository orderReceiptRepository) {
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

            String trackingNo = (status == OrderStatus.SHIPPING || status == OrderStatus.DONE)
                    ? String.format("CJ%010d", 1_000_000L + i)
                    : null;
            String shipCompany = trackingNo != null ? "CJ대한통운" : null;

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
}
