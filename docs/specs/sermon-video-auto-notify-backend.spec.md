# 예배 영상 자동 업로드 + 공지 발송 — 백엔드 스펙

## 0. 배경 / 범위

`/office-hours` 검증 세션 결과, 소형 교회 B2B2C SaaS 방향의 수요 검증(Talk-to-15)은 사용자 결정으로 스킵하고 바로 Wedge MVP로 진행하기로 함. 웨지 기능: **콘텐츠(예배 영상) 등록 시 해당 교회 회원에게 자동으로 공지를 발송**하는 것.

원본 검증 계획(`~/.gstack/projects/1mJeeHwan-graceon/imjihwan-chore-springboot4-upgrade-design-20260701-145846.md`)에 명시된 리스크를 그대로 인지한 채 진행: Q1~Q3(수요·현재 대안·타겟 인물) 미검증 상태. 배포 후 최소 1명 이상의 실사용자 피드백은 반드시 받을 것.

**신규 엔티티 없음, 신규 컨트롤러 없음, DB 마이그레이션 없음.** 기존 `content` 도메인과 `notification` 도메인에 필드 1개 + 위임 호출 1개만 추가한다. 과도한 신규 패키지 생성 금지.

---

## 1. 조사로 확인된 사실

- `Content`(영상/음원)는 `Channel`을 통해 이미 `Church`에 연결되어 있음(`ensureChannelInScope` 패턴, `ContentService.java`) — 새 `churchId` 필드 불필요.
- 업로드는 이미 2단계: `POST /v1/content/upload`(멀티파트 → key/url) → `POST /v1/content`(메타데이터 생성, `ContentCreateRequest`).
- **중요 발견**: `NotificationService.send()`의 `NotificationScope.BROADCAST`는 **교회 구분 없이 전체 회원**에게 간다(`targetMasked = "전체 회원"`, 스코프 필터링 없음 — `NotificationSendRequest` 주석에 명시: "memberIds ignored"). 교회 담당자가 콘텐츠를 올렸다고 전체 서비스 회원에게 알림이 가면 안 되므로, **BROADCAST를 그대로 쓰면 안 된다.**
- `TARGETED` 스코프는 이미 `memberIds`에 대해 존재 검증 + `principal.churchId()` 일치 검증을 수행함(`NotificationService.send()` 68~78행대). 이 경로를 재사용하면 "이 교회 회원에게만"이 별도 신규 스코프 없이 해결된다.
- `MemberRepository`에는 `countByChurchId(Long)`은 있으나 `findAllByChurchId(Long)`(id 목록 조회용)이 없음 — 1개 추가 필요.

---

## 2. 데이터 모델

**신규 엔티티/컬럼 없음.** `Content`, `NotificationLog`, `Member` 스키마 변경 없음.

---

## 3. 백엔드

### 3.1 생성/수정 파일 목록

**신규 (0)** — 없음

**기존 수정 (5)**
- `streamhub-api/src/main/java/org/streamhub/api/v1/content/dto/ContentCreateRequest.java` — 필드 1개 추가: `boolean notifyOnPublish`
- `streamhub-api/src/main/java/org/streamhub/api/v1/content/ContentService.java` — `create()`에 위임 호출 추가 (§3.3)
- `streamhub-api/src/main/java/org/streamhub/api/v1/content/ContentController.java` — `create()` 메서드 `@PreAuthorize` 확장 (§3.2)
- `streamhub-api/src/main/java/org/streamhub/api/v1/member/repository/MemberRepository.java` — `findAllByChurchId(Long)` 추가
- `streamhub-api/src/main/java/org/streamhub/api/v1/notification/NotificationService.java` — 변경 없음(재사용만). 단, `send()`를 `ContentService`에서 호출할 수 있도록 `ContentService`에 `NotificationService` 의존성 주입 추가

### 3.2 API 엔드포인트 — 변경 없음, 권한만 확장

새 엔드포인트 없음. 기존 `POST /v1/content`(create)를 그대로 쓰되, `notifyOnPublish=true`인 요청은 `notification:write` 권한도 요구한다. `content:write`만 가진 역할이 부작용으로 알림을 트리거하지 못하도록 SpEL로 조건부 검사:

```java
@PreAuthorize("hasAuthority('content:write') "
        + "and (#request.notifyOnPublish() == false or hasAuthority('notification:write'))")
@PostMapping
public ResultDTO<ContentDetail> create(
        @Valid @RequestBody ContentCreateRequest request,
        @AuthenticationPrincipal AdminPrincipal principal) {
    return ResultDTO.ok(contentService.create(request, principal));
}
```

| 메서드 | 경로 | 요청 | 응답 | 권한 | 상태 |
|---|---|---|---|---|---|
| POST | `/v1/content` | `ContentCreateRequest`(+`notifyOnPublish`) | `ResultDTO<ContentDetail>` | `content:write` (+`notification:write` if `notifyOnPublish`) | 확장 |

### 3.3 핵심 로직 의사코드 — `ContentService.create()`

기존 메서드 끝(`return getDetail(...)` 직전)에 조건부 분기 추가:

```java
public ContentDetail create(ContentCreateRequest request, AdminPrincipal principal) {
    ensureChannelInScope(request.channelId(), principal);
    Content content = Content.builder()
            .channelId(request.channelId())
            .type(request.type())
            .title(request.title())
            .description(request.description())
            .thumbnailKey(request.thumbnailKey())
            .mediaUrl(request.mediaUrl())
            .durationSec(request.durationSec())
            .status(request.status() == null ? ContentStatus.DRAFT : request.status())
            .build();
    Content saved = contentRepository.save(content);
    applyHashtags(saved.getId(), request.hashtags());
    recordThumbnailFile(saved.getId(), request.thumbnailKey());
    actionLogPublisher.publish("CONTENT_CREATE", "CONTENT", String.valueOf(saved.getId()), request.title());

    if (request.notifyOnPublish()) {                                   // 신규
        notifyChurchMembers(saved, request.channelId(), principal);    // 신규
    }

    return getDetail(saved.getId(), principal);
}

// 신규 private 헬퍼
private void notifyChurchMembers(Content content, Long channelId, AdminPrincipal principal) {
    Channel channel = channelRepository.findById(channelId)
            .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
    Long churchId = channel.getChurchId();

    List<Long> memberIds = memberRepository.findAllByChurchId(churchId).stream()
            .map(Member::getId)
            .toList();
    if (memberIds.isEmpty()) {
        return; // 알릴 회원이 없으면 조용히 스킵 — 실패로 취급하지 않음(콘텐츠 등록 자체는 이미 성공)
    }

    notificationService.send(new NotificationSendRequest(
            NotificationChannel.PUSH,
            NotificationScope.TARGETED,
            "새 영상이 등록되었습니다",
            content.getTitle(),
            memberIds
    ), principal);
}
```

> **왜 콘텐츠 등록과 같은 트랜잭션에 안 묶나**: `notificationService.send()`는 자체 `@Transactional` 경계를 가진 별도 저장(로그 테이블)이다. 알림 발송이 실패해도 콘텐츠 등록 자체는 이미 성공한 상태로 유지되어야 한다(사용자가 영상은 못 보고 등록도 실패했다고 착각하면 안 됨). 그래서 예외를 삼키지 않되(문제가 있으면 500으로 드러나야 QA 가능), 같은 `@Transactional` 안에 묶어 롤백 결합은 시키지 않는다 — `ContentService.create()`가 이미 `@Transactional`이라면 알림 실패 시 콘텐츠까지 롤백되므로, **알림 발송 실패를 허용할지(로그만 남기고 콘텐츠는 유지) 여부는 배포 전 결정 필요**(Open Question, §6).

### 3.4 `MemberRepository` delta

```java
List<Member> findAllByChurchId(Long churchId);
```

`countByChurchId`와 동일한 파생 쿼리 패턴, 신규 어노테이션 불필요.

### 3.5 `ContentCreateRequest` delta

```java
public record ContentCreateRequest(
        @NotBlank(message = "제목을 입력하세요") String title,
        String description,
        @NotNull(message = "유형은 필수입니다") ContentType type,
        @NotNull(message = "채널은 필수입니다") Long channelId,
        String mediaUrl,
        Integer durationSec,
        String thumbnailKey,
        ContentStatus status,
        List<String> hashtags,
        boolean notifyOnPublish) {   // 신규, 기본값 false(record라 프론트에서 명시 필요)
}
```

> Java record는 필드 기본값이 없다 — 프론트에서 이 필드를 생략하면 JSON 역직렬화 시 `false`(primitive boolean 기본값)로 채워진다. 명시적 검증 불필요.

---

## 4. 구현 순서 체크리스트

- [ ] `MemberRepository.findAllByChurchId(Long)` 추가
- [ ] `ContentCreateRequest`에 `notifyOnPublish` 필드 추가
- [ ] `ContentService`에 `NotificationService`, `MemberRepository` 의존성 주입(생성자)
- [ ] `ContentService.notifyChurchMembers()` private 메서드 추가
- [ ] `ContentService.create()`에서 `notifyOnPublish` 분기 호출
- [ ] `ContentController.create()` `@PreAuthorize` SpEL 확장
- [ ] §6 Open Question(트랜잭션 결합 여부) 결정 후 반영
- [ ] `./mvnw test` 그린 확인
- [ ] Colima 기동 후 `/v3/api-docs`에서 `ContentCreateRequest.notifyOnPublish` 필드 노출 확인 → **배포**

---

## 5. 위험 / 주의

- **BROADCAST 오용 금지**: 이 기능에서 절대 `NotificationScope.BROADCAST`를 쓰지 않는다 — 전체 서비스 회원에게 알림이 나가는 사고로 이어진다. 반드시 `TARGETED` + 교회 스코프 memberIds.
- **대량 회원 교회**: `findAllByChurchId`가 페이징 없이 전체 로드 — 현재 시드/데모 규모(교회당 수십~수백 명)에서는 문제없음. 실사용 확대 시 배치 처리 고려(지금은 YAGNI).
- **알림 실패 시 콘텐츠 롤백 여부**: §3.3에서 언급한 Open Question. 기본은 "콘텐츠는 살아있고 알림만 실패 로그"를 권장하되, `ContentService.create()`가 이미 `@Transactional`이면 자동으로 함께 롤백된다 — 배포 전 QA로 실제 동작 확인 필수.
- **`notification:write` 권한**: `RolePermissions.java` 확인 결과 `CHURCH_MANAGER`는 이미 `notification`을 operational resource 목록(35행대)에서 read+write로 보유 — 추가 권한 부여 불필요, `content:write` + `notification:write`를 동시에 가진 CHURCH_MANAGER가 그대로 이 기능을 쓸 수 있다.
- **5개 미만 파일 변경**: 총 5개 파일 수정, 신규 파일 0개 — CLAUDE.md "5개 이상 파일 변경 시 승인" 기준에 해당하지 않으나, 보고 후 진행.
