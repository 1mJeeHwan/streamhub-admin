package org.streamhub.api.v1.worship;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.member.entity.Church;
import org.streamhub.api.v1.member.repository.ChurchRepository;
import org.streamhub.api.v1.worship.adapter.PostcodeProvider;
import org.streamhub.api.v1.worship.adapter.SmsNotifier;
import org.streamhub.api.v1.worship.dto.ChurchOptionDto;
import org.streamhub.api.v1.worship.dto.RegistrationFamilyDto;
import org.streamhub.api.v1.worship.dto.WorshipRegisterRequest;
import org.streamhub.api.v1.worship.dto.WorshipRegisterResponse;
import org.streamhub.api.v1.worship.dto.WorshipRegistrationDetail;
import org.streamhub.api.v1.worship.dto.WorshipRegistrationListItem;
import org.streamhub.api.v1.worship.dto.WorshipSearchRequest;
import org.streamhub.api.v1.worship.dto.WorshipStatusChangeRequest;
import org.streamhub.api.v1.worship.entity.BaptismType;
import org.streamhub.api.v1.worship.entity.RegistrationFamily;
import org.streamhub.api.v1.worship.entity.RegistrationStatus;
import org.streamhub.api.v1.worship.entity.WorshipRegistration;
import org.streamhub.api.v1.worship.mapper.WorshipMapper;
import org.streamhub.api.v1.worship.repository.RegistrationFamilyRepository;
import org.streamhub.api.v1.worship.repository.WorshipRegistrationRepository;

/**
 * Worship/new-family registration (C2): public create (registration + dynamic family rows),
 * admin paginated search (MyBatis), detail assembly, and the registration state machine
 * ({@link #changeStatus}). External postcode/SMS integrations are behind seams
 * ({@link PostcodeProvider}, {@link SmsNotifier}); the default beans are demo/test no-ops.
 */
@Service
public class WorshipService {

    /**
     * Authoritative registration-status transition map (spec §3.4). The frontend keeps a UX
     * mirror; this map is the single source of truth and rejects illegal transitions.
     */
    private static final Map<RegistrationStatus, Set<RegistrationStatus>> TRANSITIONS = buildTransitions();

    /** Max family rows per registration (spec §2.3). */
    private static final int MAX_FAMILY = 5;

    private static final DateTimeFormatter REG_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** How many times a {@code reg_no} collision is retried before giving up (spec §2.3 robustness). */
    static final int REG_NO_MAX_ATTEMPTS = 5;

    private final WorshipMapper worshipMapper;
    private final WorshipRegistrationRepository worshipRegistrationRepository;
    private final RegistrationFamilyRepository registrationFamilyRepository;
    private final ChurchRepository churchRepository;
    private final PostcodeProvider postcodeProvider;
    private final SmsNotifier smsNotifier;
    private final ActionLogPublisher actionLogPublisher;
    private final WorshipRegistrationWriter worshipRegistrationWriter;

    public WorshipService(
            WorshipMapper worshipMapper,
            WorshipRegistrationRepository worshipRegistrationRepository,
            RegistrationFamilyRepository registrationFamilyRepository,
            ChurchRepository churchRepository,
            PostcodeProvider postcodeProvider,
            SmsNotifier smsNotifier,
            ActionLogPublisher actionLogPublisher,
            WorshipRegistrationWriter worshipRegistrationWriter) {
        this.worshipMapper = worshipMapper;
        this.worshipRegistrationRepository = worshipRegistrationRepository;
        this.registrationFamilyRepository = registrationFamilyRepository;
        this.churchRepository = churchRepository;
        this.postcodeProvider = postcodeProvider;
        this.smsNotifier = smsNotifier;
        this.actionLogPublisher = actionLogPublisher;
        this.worshipRegistrationWriter = worshipRegistrationWriter;
    }

    /** Open churches for the public form's church selector (only {@code openYn='Y'}). */
    @Transactional(readOnly = true)
    public List<ChurchOptionDto> listOpenChurches() {
        return churchRepository.findByOpenYn("Y").stream()
                .filter(c -> "Y".equals(c.getUseYn())) // hidden churches must not accept registrations either
                .map(ChurchOptionDto::from)
                .toList();
    }

    /**
     * Creates a registration plus its dynamic family rows (≤5) in one transaction, then fires
     * the (no-op) received notification and an action log. Public/unauthenticated: there is no
     * security context, so the action log is published with an explicit "신청자" operator.
     *
     * <p>The persistence step is delegated to {@link WorshipRegistrationWriter} so a {@code reg_no}
     * unique collision (concurrent same-day applicants, public + unauthenticated) is retried with a
     * fresh number up to {@link #REG_NO_MAX_ATTEMPTS} times instead of surfacing as a 500.
     *
     * @throws ApiException {@code NOT_FOUND} if the church is missing, or {@code INVALID_PARAMETER}
     *                      for a closed church, missing privacy consent, or &gt;5 family rows;
     *                      {@code INTERNAL_ERROR} if a unique number could not be allocated after
     *                      {@link #REG_NO_MAX_ATTEMPTS} attempts
     */
    public WorshipRegisterResponse create(WorshipRegisterRequest request) {
        Church church = churchRepository.findById(request.churchId())
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (!"Y".equals(church.getOpenYn()) || !"Y".equals(church.getUseYn())) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "등록을 받지 않는 교회입니다");
        }
        if (!"Y".equals(request.privacyAgreed())) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "개인정보 동의가 필요합니다");
        }
        List<RegistrationFamilyDto> families =
                request.families() == null ? List.of() : request.families();
        if (families.size() > MAX_FAMILY) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "가족은 최대 5명까지 입력할 수 있습니다");
        }

        PostcodeProvider.PostcodeResult address =
                postcodeProvider.resolve(request.zipcode(), request.addr1());

        WorshipRegistration saved = saveWithUniqueRegNo(request, address, families);

        // Notification seam — demo/test no-op (no real SMS dispatched).
        smsNotifier.notifyRegistrationReceived(saved.getPhone(), saved.getRegNo());
        // Public call: no security context, so attribute the action to the applicant explicitly.
        actionLogPublisher.publishAs(null, "신청자", "WORSHIP_RECEIVED", "WORSHIP",
                String.valueOf(saved.getId()), saved.getName());

        return new WorshipRegisterResponse(saved.getId(), saved.getRegNo());
    }

    /**
     * Persists the registration + family rows, regenerating the {@code reg_no} and re-attempting
     * (each in its own transaction) on a {@link DataIntegrityViolationException} unique collision.
     */
    private WorshipRegistration saveWithUniqueRegNo(
            WorshipRegisterRequest request,
            PostcodeProvider.PostcodeResult address,
            List<RegistrationFamilyDto> families) {
        for (int attempt = 1; attempt <= REG_NO_MAX_ATTEMPTS; attempt++) {
            String regNo = buildRegNo(LocalDateTime.now());
            try {
                return worshipRegistrationWriter.insertWithRegNo(
                        regNo,
                        assignedRegNo -> buildRegistration(request, address, assignedRegNo),
                        registrationId -> buildFamilyRows(registrationId, families));
            } catch (DataIntegrityViolationException collision) {
                if (attempt == REG_NO_MAX_ATTEMPTS) {
                    throw new ApiException(ResultCode.INTERNAL_ERROR, "접수번호 발급에 실패했습니다. 잠시 후 다시 시도해 주세요");
                }
                // Same-day race: another applicant took this number — regenerate and retry.
            }
        }
        // Unreachable: the loop either returns or throws on the final attempt.
        throw new ApiException(ResultCode.INTERNAL_ERROR);
    }

    private WorshipRegistration buildRegistration(
            WorshipRegisterRequest request, PostcodeProvider.PostcodeResult address, String regNo) {
        return WorshipRegistration.builder()
                .churchId(request.churchId())
                .regNo(regNo)
                .status(RegistrationStatus.RECEIVED)
                .name(request.name())
                .gender(request.gender())
                .birthDate(request.birthDate())
                .phone(request.phone())
                .email(request.email())
                .zipcode(address.zipcode())
                .addr1(address.addr1())
                .addr2(request.addr2())
                .registerDept(request.registerDept())
                .churchExperience(defaultYn(request.churchExperience()))
                .prevChurch(request.prevChurch())
                .baptismType(request.baptismType() == null ? BaptismType.NONE : request.baptismType())
                .leaderName(request.leaderName())
                .leaderPhone(request.leaderPhone())
                .privacyAgreed("Y")
                .testMode("Y")
                .build();
    }

    private List<RegistrationFamily> buildFamilyRows(Long registrationId, List<RegistrationFamilyDto> families) {
        List<RegistrationFamily> familyRows = new ArrayList<>();
        for (int i = 0; i < families.size(); i++) {
            RegistrationFamilyDto family = families.get(i);
            familyRows.add(RegistrationFamily.builder()
                    .registrationId(registrationId)
                    .name(family.name())
                    .relation(family.relation())
                    .birthDate(family.birthDate())
                    .sort(i + 1)
                    .build());
        }
        return familyRows;
    }

    /**
     * Paginated registration search. Registrations carry an explicit {@code church_id}; the filter
     * is pinned to the operator's church for CHURCH_MANAGER, ignoring any attacker-supplied
     * {@code request.churchId()}. SYSTEM operators honor the requested church (null = all).
     *
     * @param request   search/pagination filters
     * @param principal authenticated operator providing the church scope
     * @return the filtered, paginated registration list
     */
    @Transactional(readOnly = true)
    public ResInfinityList<WorshipRegistrationListItem> list(WorshipSearchRequest request, AdminPrincipal principal) {
        String searchField = blankToNull(request.searchField());
        String keyword = blankToNull(request.keyword());
        String status = request.status() == null ? null : request.status().name();
        Long churchId = scopedChurchId(request.churchId(), principal);
        int size = request.pageSizeOrDefault();

        List<WorshipRegistrationListItem> rows = worshipMapper.selectList(
                searchField, keyword, status, churchId,
                request.fromDate(), request.toDate(), request.offset(), size);
        long total = worshipMapper.countList(
                searchField, keyword, status, churchId,
                request.fromDate(), request.toDate());
        return ResInfinityList.of(rows, total, size);
    }

    /**
     * Registration detail. Verifies the registration's church is in the operator's scope first so a
     * CHURCH_MANAGER cannot read another church's registration.
     *
     * @param id        registration id
     * @param principal authenticated operator providing the church scope
     * @return the registration detail with family rows
     */
    @Transactional(readOnly = true)
    public WorshipRegistrationDetail getDetail(Long id, AdminPrincipal principal) {
        ensureRegistrationInScope(id, principal);
        WorshipRegistrationDetail detail = worshipMapper.selectDetail(id);
        if (detail == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        detail.setFamilies(registrationFamilyRepository.findByRegistrationIdOrderBySortAscIdAsc(id).stream()
                .map(RegistrationFamilyDto::from)
                .toList());
        return detail;
    }

    /**
     * Transitions a registration through the state machine, optionally updating the admin memo.
     * Firing the (no-op) contacted notification on {@code → CONTACTED} happens in the same
     * transaction.
     *
     * @throws ApiException {@code NOT_FOUND} if the registration is missing,
     *                      {@code INVALID_PARAMETER} for an illegal transition, or
     *                      {@code FORBIDDEN} if the registration is outside the operator's church
     */
    @Transactional
    public WorshipRegistrationDetail changeStatus(Long id, WorshipStatusChangeRequest request, AdminPrincipal principal) {
        WorshipRegistration registration = worshipRegistrationRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureChurchInScope(registration.getChurchId(), principal);
        RegistrationStatus from = registration.getStatus();
        RegistrationStatus to = request.status();
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "허용되지 않는 상태 전이입니다");
        }
        if (request.memo() != null && !request.memo().isBlank()) {
            registration.updateMemo(request.memo());
        }
        registration.changeStatus(to);
        worshipRegistrationRepository.saveAndFlush(registration);

        if (to == RegistrationStatus.CONTACTED) {
            // Notification seam — demo/test no-op (no real SMS dispatched).
            smsNotifier.notifyContacted(registration.getPhone(), registration.getRegNo());
        }
        actionLogPublisher.publish(
                "WORSHIP_" + to.name(), "WORSHIP", String.valueOf(id), registration.getRegNo());
        return getDetail(id, principal);
    }

    // --- helpers -----------------------------------------------------------

    /** Resolves the church filter: CHURCH_MANAGER is pinned to its own church. */
    private Long scopedChurchId(Long requestedChurchId, AdminPrincipal principal) {
        return principal.isSystem() ? requestedChurchId : principal.churchId();
    }

    /** Loads the registration and verifies its church is in the operator's scope. */
    private void ensureRegistrationInScope(Long registrationId, AdminPrincipal principal) {
        if (principal.isSystem()) {
            return;
        }
        WorshipRegistration registration = worshipRegistrationRepository.findById(registrationId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureChurchInScope(registration.getChurchId(), principal);
    }

    /** Verifies the church belongs to the operator's scope (SYSTEM bypasses). */
    private void ensureChurchInScope(Long churchId, AdminPrincipal principal) {
        if (!principal.isSystem() && !churchId.equals(principal.churchId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }

    /**
     * Builds a {@code WR-yyyyMMdd-NNNN} candidate, advancing the suffix past any number already
     * persisted today. This closes single-threaded collisions; concurrent same-day races (two
     * threads reading the same max before either commits) are handled by the insert-retry in
     * {@link #saveWithUniqueRegNo}.
     */
    private String buildRegNo(LocalDateTime now) {
        String day = now.format(REG_NO_DATE);
        int seq = 1;
        String candidate;
        do {
            candidate = "WR-" + day + "-" + String.format("%04d", seq);
            seq++;
        } while (worshipRegistrationRepository.existsByRegNo(candidate));
        return candidate;
    }

    private String defaultYn(String value) {
        return "Y".equals(value) ? "Y" : "N";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Map<RegistrationStatus, Set<RegistrationStatus>> buildTransitions() {
        Map<RegistrationStatus, Set<RegistrationStatus>> map = new EnumMap<>(RegistrationStatus.class);
        map.put(RegistrationStatus.RECEIVED,
                Set.of(RegistrationStatus.CONTACTED, RegistrationStatus.CANCELED));
        map.put(RegistrationStatus.CONTACTED,
                Set.of(RegistrationStatus.COMPLETED, RegistrationStatus.CANCELED));
        map.put(RegistrationStatus.COMPLETED, Set.of());
        map.put(RegistrationStatus.CANCELED, Set.of());
        return map;
    }
}
