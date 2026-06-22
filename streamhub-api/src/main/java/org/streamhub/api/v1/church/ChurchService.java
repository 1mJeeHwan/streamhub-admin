package org.streamhub.api.v1.church;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.external.discovery.ChurchDiscoveryProvider;
import org.streamhub.api.base.external.discovery.DiscoveredChurch;
import org.streamhub.api.base.external.geocode.GeocodeProvider;
import org.streamhub.api.base.external.geocode.GeocodeResult;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.base.storage.StorageService;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.church.dto.ChurchDetail;
import org.streamhub.api.v1.church.dto.ChurchListItem;
import org.streamhub.api.v1.church.dto.ChurchNearbyItem;
import org.streamhub.api.v1.church.dto.ChurchNearbyRequest;
import org.streamhub.api.v1.church.dto.ChurchSearchRequest;
import org.streamhub.api.v1.church.dto.ChurchUpsertRequest;
import org.streamhub.api.v1.church.dto.CodeLabel;
import org.streamhub.api.v1.church.dto.WorshipTimeDto;
import org.streamhub.api.v1.church.entity.Denomination;
import org.streamhub.api.v1.church.entity.WorshipTime;
import org.streamhub.api.v1.church.geo.HaversineDistance;
import org.streamhub.api.v1.church.mapper.ChurchMapper;
import org.streamhub.api.v1.church.repository.WorshipTimeRepository;
import org.streamhub.api.v1.member.entity.Church;
import org.streamhub.api.v1.member.repository.ChurchRepository;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.worship.repository.WorshipRegistrationRepository;

/**
 * Church management: admin search/CRUD (JPA + MyBatis), worship-time replacement, and
 * public location-based search. Distance is computed with {@link HaversineDistance} after a
 * MyBatis bounding-box pre-filter. Coordinates may be derived via the {@link GeocodeProvider}
 * seam when an admin supplies only an address.
 */
@Service
public class ChurchService {

    /** {@code dataSource} marker that flags a record as demo/seed data. */
    private static final String SEED_SOURCE = "SEED";
    /** {@code dataSource} marker for real-time Kakao POI discovery results (not in our DB). */
    private static final String POI_SOURCE = "KAKAO_POI";
    /** Degrees of latitude per kilometre (~111 km/deg). */
    private static final double KM_PER_LAT_DEGREE = 111.0;
    /** Hard cap on the public search radius (km) — also Kakao's max, keeps the bbox sane. */
    private static final double MAX_RADIUS_KM = 20.0;

    private final ChurchMapper churchMapper;
    private final ChurchRepository churchRepository;
    private final WorshipTimeRepository worshipTimeRepository;
    private final MemberRepository memberRepository;
    private final WorshipRegistrationRepository worshipRegistrationRepository;
    private final StorageService storageService;
    private final GeocodeProvider geocodeProvider;
    private final ChurchDiscoveryProvider discoveryProvider;
    private final ActionLogPublisher actionLogPublisher;

    public ChurchService(
            ChurchMapper churchMapper,
            ChurchRepository churchRepository,
            WorshipTimeRepository worshipTimeRepository,
            MemberRepository memberRepository,
            WorshipRegistrationRepository worshipRegistrationRepository,
            StorageService storageService,
            GeocodeProvider geocodeProvider,
            ChurchDiscoveryProvider discoveryProvider,
            ActionLogPublisher actionLogPublisher) {
        this.churchMapper = churchMapper;
        this.churchRepository = churchRepository;
        this.worshipTimeRepository = worshipTimeRepository;
        this.memberRepository = memberRepository;
        this.worshipRegistrationRepository = worshipRegistrationRepository;
        this.storageService = storageService;
        this.geocodeProvider = geocodeProvider;
        this.discoveryProvider = discoveryProvider;
        this.actionLogPublisher = actionLogPublisher;
    }

    // --- admin list / detail ------------------------------------------------

    @Transactional(readOnly = true)
    public ResInfinityList<ChurchListItem> list(ChurchSearchRequest request, AdminPrincipal principal) {
        String keyword = blankToNull(request.keyword());
        String denomination = request.denomination() == null ? null : request.denomination().name();
        String useYn = blankToNull(request.useYn());
        int size = request.pageSizeOrDefault();
        // CHURCH_MANAGER is restricted to its own church; SYSTEM/VIEWER list across all churches.
        Long ownChurchId = scopedChurchId(principal);

        List<ChurchListItem> contents = churchMapper.selectList(
                keyword, request.regionId(), denomination, useYn, ownChurchId, request.offset(), size);
        contents.forEach(item -> item.setThumbnailUrl(storageService.publicUrl(item.getThumbnailKey())));
        long total = churchMapper.countList(keyword, request.regionId(), denomination, useYn, ownChurchId);
        return ResInfinityList.of(contents, total, size);
    }

    /** Admin detail, scoped: a CHURCH_MANAGER may read only its own church (else NOT_FOUND). */
    @Transactional(readOnly = true)
    public ChurchDetail getDetail(Long id, AdminPrincipal principal) {
        ensureOwnChurch(id, principal);
        return loadDetail(id);
    }

    /** Raw detail load (no scope check) — used by public detail and create/update responses. */
    @Transactional(readOnly = true)
    public ChurchDetail loadDetail(Long id) {
        ChurchDetail detail = churchMapper.selectDetail(id);
        if (detail == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        detail.setThumbnailUrl(storageService.publicUrl(detail.getThumbnailKey()));
        detail.setDemoData(SEED_SOURCE.equals(detail.getDataSource()));
        detail.setWorshipTimes(loadWorshipTimes(id));
        detail.setMemberCount(memberRepository.countByChurchId(id));
        detail.setWorshipRegistrationCount(worshipRegistrationRepository.countByChurchId(id));
        return detail;
    }

    /** Public detail: 404 unless visible ({@code use_yn = "Y"}). Includes worship times. */
    @Transactional(readOnly = true)
    public ChurchDetail getPublicDetail(Long id) {
        ChurchDetail detail = loadDetail(id);
        if (!"Y".equals(detail.getUseYn())) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        return detail;
    }

    /** Enum → Korean-label list for the denomination select box. */
    @Transactional(readOnly = true)
    public List<CodeLabel> listDenominations() {
        List<CodeLabel> labels = new ArrayList<>();
        for (Denomination d : Denomination.values()) {
            labels.add(new CodeLabel(d.name(), denominationLabel(d)));
        }
        return labels;
    }

    // --- public location search ---------------------------------------------

    /**
     * Location-based search. With coordinates + radius: bounding-box pre-filter, then precise
     * Haversine distance, radius cut, distance sort, and in-memory paging. Without coordinates:
     * region/denomination/keyword filters with {@code createdAt desc} and DB paging.
     */
    @Transactional(readOnly = true)
    public ResInfinityList<ChurchNearbyItem> nearby(ChurchNearbyRequest request) {
        String keyword = blankToNull(request.keyword());
        String denomination = request.denomination() == null ? null : request.denomination().name();
        int size = request.pageSizeOrDefault();

        if (!request.hasLocation()) {
            List<ChurchNearbyItem> items = churchMapper.selectPublicList(
                    keyword, request.regionId(), denomination, request.offset(), size);
            items.forEach(this::fillItemUrl);
            long total = churchMapper.countPublicList(keyword, request.regionId(), denomination);
            return ResInfinityList.of(items, total, size);
        }

        double lat = request.lat();
        double lng = request.lng();
        // Reject absurd coordinates: out-of-range lat/lng would yield a meaningless bbox (and a
        // near-zero cos(lat) at the poles blows up the longitude delta). Cap the radius so a huge
        // value can't force an oversized scan.
        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "좌표 범위가 올바르지 않습니다.");
        }
        double radiusKm = Math.min(request.radiusKmOrDefault(), MAX_RADIUS_KM);
        double latDelta = radiusKm / KM_PER_LAT_DEGREE;
        double lngDelta = radiusKm / (KM_PER_LAT_DEGREE * Math.cos(Math.toRadians(lat)));

        List<ChurchNearbyItem> candidates = churchMapper.selectInBox(
                lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta,
                keyword, request.regionId(), denomination);

        List<ChurchNearbyItem> hits = new ArrayList<>();
        for (ChurchNearbyItem item : candidates) {
            if (item.getLatitude() == null || item.getLongitude() == null) {
                // Coordinate-less church (incomplete geocode): keep it in the result with a null
                // distance so it is never NPE'd nor silently dropped — it just sorts last.
                item.setDistanceKm(null);
                fillItemUrl(item);
                hits.add(item);
                continue;
            }
            double distance = HaversineDistance.km(lat, lng, item.getLatitude(), item.getLongitude());
            if (distance <= radiusKm) {
                item.setDistanceKm(round2(distance));
                fillItemUrl(item);
                hits.add(item);
            }
        }
        // Augment DB hits with real surrounding churches from the discovery provider (Kakao POI).
        mergeDiscovered(hits, lat, lng, radiusKm, keyword, denomination);

        // Nearest first; null-distance (coordinate-less) churches are ordered last.
        hits.sort(Comparator.comparing(ChurchNearbyItem::getDistanceKm,
                Comparator.nullsLast(Comparator.naturalOrder())));

        long total = hits.size();
        List<ChurchNearbyItem> page = paginate(hits, request.offset(), size);
        return ResInfinityList.of(page, total, size);
    }

    // --- admin CRUD ---------------------------------------------------------

    @Transactional
    public ChurchDetail create(ChurchUpsertRequest request, AdminPrincipal principal) {
        // New directory entries are minted by SYSTEM only — a CHURCH_MANAGER cannot create a church
        // (its own already exists) and must never be able to plant another church's record.
        if (principal != null && !principal.isSystem()) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        Double latitude = request.latitude();
        Double longitude = request.longitude();
        String dataSource = SEED_SOURCE;
        if ((latitude == null || longitude == null)) {
            GeocodeResult geocode = geocodeProvider.geocode(request.address());
            latitude = geocode.latitude();
            longitude = geocode.longitude();
            dataSource = geocode.source();
        }

        Church church = Church.builder()
                .regionId(request.regionId())
                .name(request.name())
                .openYn(defaultYn(request.openYn()))
                .denomination(request.denomination())
                .latitude(latitude)
                .longitude(longitude)
                .address(request.address())
                .addressDetail(request.addressDetail())
                .zipcode(request.zipcode())
                .phone(request.phone())
                .pastorName(request.pastorName())
                .facilities(request.facilities())
                .introduction(request.introduction())
                .homepageUrl(request.homepageUrl())
                .thumbnailKey(request.thumbnailKey())
                .dataSource(dataSource)
                .useYn(defaultYn(request.useYn()))
                .build();
        Church saved = churchRepository.save(church);
        replaceWorshipTimes(saved.getId(), request.worshipTimes());
        actionLogPublisher.publish("CHURCH_CREATE", "CHURCH", String.valueOf(saved.getId()), request.name());
        return loadDetail(saved.getId());
    }

    @Transactional
    public ChurchDetail update(Long id, ChurchUpsertRequest request, AdminPrincipal principal) {
        ensureOwnChurch(id, principal);
        Church church = churchRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        Double latitude = request.latitude();
        Double longitude = request.longitude();
        if (latitude == null || longitude == null) {
            GeocodeResult geocode = geocodeProvider.geocode(request.address());
            latitude = geocode.latitude();
            longitude = geocode.longitude();
        }
        church.update(
                request.name(), request.regionId(), request.denomination(), latitude, longitude,
                request.address(), request.addressDetail(), request.zipcode(), request.phone(),
                request.pastorName(), request.facilities(), request.introduction(),
                request.homepageUrl(), request.thumbnailKey(),
                defaultYn(request.openYn()), defaultYn(request.useYn()));
        churchRepository.saveAndFlush(church);
        replaceWorshipTimes(id, request.worshipTimes());
        actionLogPublisher.publish("CHURCH_UPDATE", "CHURCH", String.valueOf(id), request.name());
        return loadDetail(id);
    }

    @Transactional
    public void delete(Long id, AdminPrincipal principal) {
        ensureOwnChurch(id, principal);
        Church church = churchRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        worshipTimeRepository.deleteByChurchId(id);
        storageService.delete(church.getThumbnailKey());
        churchRepository.delete(church);
        actionLogPublisher.publish("CHURCH_DELETE", "CHURCH", String.valueOf(id), church.getName());
    }

    // --- helpers ------------------------------------------------------------

    /**
     * The church id a non-SYSTEM operator is confined to, or {@code null} for unscoped operators
     * (SYSTEM, VIEWER). Drives both the admin-list filter and per-row access checks.
     */
    private Long scopedChurchId(AdminPrincipal principal) {
        return (principal == null || principal.isUnscoped()) ? null : principal.churchId();
    }

    /**
     * Verifies the target church is the operator's own (SYSTEM/VIEWER bypass). A church owned by
     * another operator is rejected as {@code NOT_FOUND}, so the same response hides both a missing
     * church and another church's record (no cross-tenant enumeration) — mirrors the content scope.
     */
    private void ensureOwnChurch(Long id, AdminPrincipal principal) {
        Long ownChurchId = scopedChurchId(principal);
        if (ownChurchId != null && !ownChurchId.equals(id)) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
    }

    /** Replaces a church's worship times (delete-then-reinsert), like the goods options strategy. */
    private void replaceWorshipTimes(Long churchId, List<WorshipTimeDto> rows) {
        worshipTimeRepository.deleteByChurchId(churchId);
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int order = 0;
        for (WorshipTimeDto row : rows) {
            if (row == null || row.kind() == null) {
                continue;
            }
            worshipTimeRepository.save(WorshipTime.builder()
                    .churchId(churchId)
                    .kind(row.kind())
                    .dayLabel(row.dayLabel())
                    .startTime(row.startTime())
                    .place(row.place())
                    .target(row.target())
                    .sort(row.sort() != null ? row.sort() : order)
                    .build());
            order++;
        }
    }

    private List<WorshipTimeDto> loadWorshipTimes(Long churchId) {
        return worshipTimeRepository.findByChurchIdOrderBySortAscIdAsc(churchId).stream()
                .map(WorshipTimeDto::from)
                .toList();
    }

    private void fillItemUrl(ChurchNearbyItem item) {
        item.setThumbnailUrl(storageService.publicUrl(item.getThumbnailKey()));
    }

    /**
     * Merges real surrounding churches from the discovery provider (Kakao POI) into the DB hits.
     * Best-effort: a discovery failure never breaks the DB-backed nearby search. Skipped when a
     * denomination filter is active (POIs carry no denomination and cannot be filtered). POI rows
     * are flagged {@code dataSource="KAKAO_POI"} with a synthetic negative id and an external map
     * link; deduped against DB churches by normalised name.
     */
    private void mergeDiscovered(List<ChurchNearbyItem> hits, double lat, double lng,
            double radiusKm, String keyword, String denomination) {
        if (denomination != null) {
            return;
        }
        List<DiscoveredChurch> discovered;
        try {
            discovered = discoveryProvider.search(lat, lng, radiusKm, keyword);
        } catch (ApiException e) {
            return;
        }
        if (discovered.isEmpty()) {
            return;
        }
        Set<String> seenNames = new HashSet<>();
        for (ChurchNearbyItem item : hits) {
            seenNames.add(normalizeName(item.getName()));
        }
        for (DiscoveredChurch d : discovered) {
            if (!seenNames.add(normalizeName(d.name()))) {
                continue; // already present (DB or earlier POI)
            }
            double distance = HaversineDistance.km(lat, lng, d.latitude(), d.longitude());
            if (distance > radiusKm) {
                continue;
            }
            ChurchNearbyItem item = new ChurchNearbyItem();
            item.setId(syntheticPoiId(d.externalId()));
            item.setName(d.name());
            item.setAddress(d.address());
            item.setPhone(d.phone());
            item.setLatitude(d.latitude());
            item.setLongitude(d.longitude());
            item.setDistanceKm(round2(distance));
            item.setDataSource(POI_SOURCE);
            item.setExternalUrl(d.placeUrl());
            hits.add(item);
        }
    }

    /** Normalised church name for dedup (whitespace-stripped, lower-cased). */
    private String normalizeName(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * Stable negative id for a POI row — never collides with positive DB ids, so the frontend can
     * key/highlight markers consistently. Derived from the Kakao place id (or its hash).
     */
    private Long syntheticPoiId(String externalId) {
        if (externalId != null) {
            try {
                return -Math.abs(Long.parseLong(externalId));
            } catch (NumberFormatException ignored) {
                return -(long) Math.abs(externalId.hashCode());
            }
        }
        return null;
    }

    private List<ChurchNearbyItem> paginate(List<ChurchNearbyItem> all, int offset, int size) {
        if (offset >= all.size()) {
            return new ArrayList<>();
        }
        int to = Math.min(offset + size, all.size());
        return new ArrayList<>(all.subList(offset, to));
    }

    private String denominationLabel(Denomination d) {
        return switch (d) {
            case METHODIST -> "감리교";
            case PCK -> "장로교(통합)";
            case HAPDONG -> "장로교(합동)";
            case HOLINESS -> "성결교";
            case GOSPEL -> "순복음";
            case BAPTIST -> "침례교";
            case ETC -> "기타";
        };
    }

    private String defaultYn(String value) {
        return blankToNull(value) == null ? "Y" : value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
