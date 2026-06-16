# StreamHub Admin — 설계문서 (PLAN.md)

> **상태:** ✅ 확정 — Phase 0 착수 중
> **목적:** 포트폴리오 / 실력 증명 — 실제 사용한 핵심 스택을 그대로 증명하는 *동작하는 수직 슬라이스*
> **프로젝트명:** `streamhub-admin` (백엔드 `streamhub-api`, 프론트 `streamhub-web`, Java 패키지 `org.streamhub.api`)

---

## 1. 개요

실제 운영 중인 교회/스트리밍 플랫폼(레퍼런스 서비스)의 **관리자 사이트 + 내부 API**를 카피하여,
실무에서 쓴 핵심 기술을 그대로 증명하는 포트폴리오 프로젝트.

- 전부를 복제하지 않고 **3개 도메인의 수직 슬라이스**(프론트 화면 → API → DB)를 완성한다.
- 외부 유료 의존성(Vimeo, Firebase, IamPort, OpenSearch)은 **실제 SDK 코드를 살릴 수 있는 대체재**(MinIO 등)로 치환하거나 범위에서 제외한다.
- "동작하는 데모"가 핵심 — `docker-compose up` + 시드 데이터로 누구나 바로 실행 가능해야 한다.

### 선정 도메인 (난이도 곡선)

| # | 도메인 | 증명하는 기술 근육 |
|---|---|---|
| **0** | 인증/공통 인프라 (기반) | JWT, Spring Security stateless, NextAuth v5, 토큰 자동 갱신, RBAC, ResultDTO, 전역 예외처리, Redis, Swagger→Orval |
| **1** | 회원관리 | 검색폼 → 리스트 → 상세/수정 → 일괄처리. JPA+MyBatis 하이브리드, 동적 검색, 페이지네이션, 권한 캐싱 |
| **2** | 콘텐츠(영상) 관리 | 파일 업로드(MinIO=S3 SDK 그대로), 멀티미디어 메타, 다대다(해시태그), 복잡 조인 |
| **3** | 통계 대시보드 | MyBatis 집계 쿼리 + Redis 캐싱 + ApexCharts 시각화 (읽기 최적화) |

---

## 2. 기술 스택 (실제 ↔ 카피 매핑)

| 레이어 | 실제 (레퍼런스 서비스) | 카피 (streamhub) | 비고 |
|---|---|---|---|
| 백엔드 프레임워크 | Spring Boot 3.4.x / Java 21 | **동일** | |
| ORM | JPA(Hibernate 6) + MyBatis 3.0.4 | **동일** | 하이브리드 그대로 |
| 인증 | Spring Security 6 + auth0/java-jwt 4.4.0 | **동일** | stateless JWT |
| DB | MySQL 8 | **동일** | docker |
| 캐시 | Redis | **동일** | docker |
| 파일저장 | AWS S3 + CloudFront | **MinIO** (S3 호환) | AWS SDK v2 코드 **그대로 동작** |
| API 문서 | springdoc-openapi 2.8.x | **동일** | Swagger UI |
| 프론트 | Next.js 14 (App Router) / TS | **동일** | |
| 서버상태 | TanStack React Query v5 | **동일** | |
| API 클라이언트 | Orval v7 (OpenAPI 코드젠) | **동일** | Swagger → 훅 자동생성 |
| 전역상태 | Zustand | **동일** | |
| 폼 | React Hook Form + Zod | **동일** | |
| 인증(프론트) | NextAuth v5-beta | **동일** | credentials + 토큰 갱신 |
| UI | shadcn/Radix + Tailwind | **동일** | |
| 테이블 | AG Grid Enterprise | **AG Grid Community** | 라이선스 회피, 기능 충분 |
| 차트 | ApexCharts | **동일** | |
| 에디터 | CKEditor 5 | 범위 제외(도메인3 선정으로 불필요) | |

### 범위에서 제외 (의도적)
- Vimeo/BrightCove 영상 호스팅 → 영상 URL/샘플 HLS 저장으로 대체
- Firebase 푸시, IamPort/IAP 결제, OpenSearch 전문검색
- 다국어(i18n) → 한국어 단일 (포트폴리오 가독성 우선, 구조는 확장 가능하게)

---

## 3. 시스템 아키텍처

```
┌─────────────────────────────┐         ┌──────────────────────────────────┐
│   streamhub-web (Next.js)  │         │      streamhub-api (Spring Boot)   │
│                              │  HTTPS  │                                    │
│  App Router (login/member/   │ ──────▶ │  SecurityFilterChain (stateless)   │
│   content/dashboard)         │  Bearer │   └ JwtAuthenticationFilter        │
│  NextAuth v5 (credentials)   │  JWT    │  Controller → Service              │
│   └ 토큰 자동 갱신 인터셉터    │ ◀────── │   ├ Repository (JPA, 단순 CRUD)    │
│  React Query + Zustand       │ ResultDTO│   └ Mapper (MyBatis, 복잡 쿼리)    │
│  Orval 생성 훅               │         │  ResultDTO<T> 래퍼                 │
└─────────────────────────────┘         └───────────┬────────────┬──────────┘
        ▲ Orval codegen                              │            │
        └──── /v3/api-docs ◀─────────────────────────┘            │
                                              ┌──────┴───┐   ┌─────┴─────┐   ┌────────┐
                                              │  MySQL 8 │   │   Redis   │   │ MinIO  │
                                              │ (주 DB)  │   │ (캐시)    │   │ (S3)   │
                                              └──────────┘   └───────────┘   └────────┘
                                                     └─────── docker-compose ────────┘
```

**요청 흐름 예시 (회원 목록):**
1. 프론트: `useMemberList()` (Orval 훅) → `POST /api/v1/member/list` + `Authorization: Bearer {accessToken}`
2. 백엔드: `JwtAuthenticationFilter` 토큰 검증 → `@PreAuthorize` 권한 체크
3. `MemberController.list()` → `MemberService` → `MemberMapper.selectList()` (MyBatis 동적 검색)
4. 응답: `ResultDTO<ResInfinityList<MemberDTO>>` (목록 + totalCount + totalPage)
5. 401 발생 시 → 프론트가 `/auth/refresh` 호출 → 새 토큰으로 원요청 재시도

---

## 4. 프로젝트 구조

```
streamhub-admin/
├── docker-compose.yml              # MySQL + Redis + MinIO
├── PLAN.md                         # (이 문서)
├── README.md                       # 아키텍처 다이어그램 + 실행법 (Phase 4)
├── seed/
│   ├── schema.sql                  # DDL
│   └── data.sql                    # 시드 데이터 (회원/콘텐츠/시청이력)
│
├── streamhub-api/                  # Spring Boot 3.4 / Java 21
│   ├── pom.xml
│   └── src/main/
│       ├── java/org/streamhub/api/
│       │   ├── base/               # 공통 인프라
│       │   │   ├── config/         # Security, Redis, MyBatis, Swagger, S3(MinIO), CORS
│       │   │   ├── jwt/            # JwtTokenProvider, JwtAuthenticationFilter
│       │   │   ├── security/       # AuthoritiesConstants, @PreAuthorize 상수
│       │   │   ├── exception/      # PalmException, GlobalExceptionHandler
│       │   │   └── response/       # ResultDTO, ResInfinityList, OperationResult
│       │   ├── auth/               # 로그인/토큰갱신 (AuthController, AuthService)
│       │   └── v1/
│       │       ├── member/         # controller / service / mapper / repository / model
│       │       ├── content/
│       │       └── statistics/
│       └── resources/
│           ├── application.yml + application-{local,docker}.yml
│           └── mappers/            # MyBatis XML (MemberMapper.xml, ContentMapper.xml, StatMapper.xml)
│
└── streamhub-web/                # Next.js 14 / TS
    ├── package.json
    ├── orval.config.js             # /v3/api-docs → src/apis/query
    ├── auth.ts / auth.options.ts   # NextAuth v5
    └── src/
        ├── app/
        │   ├── login/page.tsx
        │   ├── member/page.tsx + [id]/page.tsx
        │   ├── content/page.tsx + [id]/page.tsx + add/page.tsx
        │   ├── dashboard/page.tsx
        │   └── serverActions/auth.ts
        ├── apis/
        │   ├── query/              # Orval 자동생성 (편집 금지)
        │   └── custom-instance.ts  # Axios + 토큰 갱신 인터셉터
        ├── components/{atoms,molecules,organisms,templates,ui}
        ├── store/                  # Zustand (nav, authority)
        ├── hooks/  └── lib/
```

---

## 5. 인프라 — docker-compose

```yaml
services:
  mysql:    # 8.x, 포트 3306, DB=streamhub
  redis:    # 7.x, 포트 6379
  minio:    # S3 호환, 포트 9000(API)/9001(콘솔), 버킷=streamhub-media
```
- 백엔드는 `application-docker.yml`로 위 서비스에 연결.
- MinIO는 AWS SDK v2의 endpoint-override만 바꾸면 실제 S3 코드와 100% 동일.

---

## 6. 인증 / 권한 설계 (Phase 0)

### 토큰
- **Access Token**: 1시간, HMAC512. Claims: `user_id`, `name`, `role[]`
- **Refresh Token**: 8시간. Redis에 화이트리스트 저장(로그아웃 시 무효화)
- Spring Security: `SessionCreationPolicy.STATELESS`, `JwtAuthenticationFilter`가 모든 요청 검증

### 권한(Role) — 단순화
| Role | 설명 | 접근 범위 |
|---|---|---|
| `SYSTEM` | 시스템 관리자 | 전체 |
| `CHURCH_MANAGER` | 교회 관리자 | 본인 교회 데이터만 |

- 백엔드: `@PreAuthorize("hasAuthority(...SYSTEM)")` 메서드 보안
- 프론트: `session.user.role` 기반 사이드바 메뉴 노출 + 라우트 가드

### 프론트 인증 플로우 (NextAuth v5)
1. `/login` → `useLogin()` → `POST /auth/login` → `{accessToken, refreshToken}`
2. `signIn("credentials")` → JWT 디코드 → 세션에 저장
3. 모든 API 호출은 `custom-instance.ts`가 `Bearer` 헤더 자동 첨부
4. 401 → `/auth/refresh` → 토큰 갱신 후 원요청 재시도 / 실패 시 `signOut()`

---

## 7. 공통 규약

### 응답 래퍼
```java
ResultDTO<T> { String resultCode; String resultMessage; T resultObject; }
ResInfinityList<T> { List<T> contents; long totalCount; int totalPage; }
```
- 성공: `resultCode="0000"`, 실패: 도메인별 코드 + 메시지
- `GlobalExceptionHandler`가 `PalmException` → 적절한 HTTP status + ResultDTO 매핑

### 페이지네이션 요청
```java
ReqInfinityList { int pageNumber; int pageSize; String keyword; /* + 도메인별 필터 */ }
```

---

## 8. ERD

### 공통/회원 도메인
```
COUNTRY (id PK, name, code)
   └─< REGION (id PK, country_id FK, name)
          └─< CHURCH (id PK, region_id FK, name, open_yn, created_at)
                 └─< MEMBER (id PK, church_id FK, email UQ, password, name, phone,
                             user_status[PENDING|CONFIRMED|INACTIVE], live_yn, created_at, updated_at)

ADMIN_ACCOUNT (id PK, login_id UQ, password, name, role[SYSTEM|CHURCH_MANAGER], church_id FK NULL)
   # 관리자 사이트 로그인 계정 (MEMBER=관리 대상, ADMIN_ACCOUNT=운영자, 명확히 분리)
```

### 콘텐츠 도메인
```
CHANNEL (id PK, church_id FK, name, created_at)
CONTENT (id PK, channel_id FK, type[VIDEO|SOUND], title, description,
         thumbnail_key, media_url, duration_sec, view_count,
         status[DRAFT|PUBLISHED], created_at, updated_at)
HASHTAG (id PK, name UQ)
CONTENT_HASHTAG (content_id FK, hashtag_id FK)   # 다대다
CONTENT_FILE (id PK, content_id FK, s3_key, file_type, size_bytes)  # MinIO 업로드 산출물
```

### 통계 도메인 (집계 소스)
```
WATCH_HISTORY (id PK, member_id FK, content_id FK, watched_at, watch_seconds)
   # 대시보드 집계의 원천 데이터. 시드로 충분량 생성.
```

---

## 9. API 명세 (핵심 엔드포인트)

> 모든 응답은 `ResultDTO<T>`. 모든 보호 엔드포인트는 `Bearer` 토큰 필요.

### 인증
| Method | Path | 설명 |
|---|---|---|
| POST | `/auth/login` | 로그인 → accessToken/refreshToken |
| POST | `/auth/refresh` | 토큰 갱신 |
| POST | `/auth/logout` | Redis refresh 무효화 |

### 회원관리 (`/v1/member`)
| Method | Path | 설명 |
|---|---|---|
| POST | `/v1/member/list` | 동적 검색 + 페이지네이션 (MyBatis) |
| GET | `/v1/member/{id}` | 상세 |
| PUT | `/v1/member/{id}` | 수정 |
| POST | `/v1/member/approve` | 일괄 승인 (idList) |
| POST | `/v1/member/deny` | 일괄 거부 |

### 콘텐츠관리 (`/v1/content`)
| Method | Path | 설명 |
|---|---|---|
| POST | `/v1/content/list` | 동적 검색 + 페이지네이션 |
| GET | `/v1/content/{id}` | 상세 (해시태그/파일 조인) |
| POST | `/v1/content` | 등록 |
| PUT | `/v1/content/{id}` | 수정 |
| DELETE | `/v1/content/{id}` | 삭제 |
| POST | `/v1/content/upload` | 파일 업로드 → MinIO → key 반환 |

### 통계 (`/v1/statistics`)
| Method | Path | 설명 |
|---|---|---|
| GET | `/v1/statistics/summary` | 요약 카드 (총회원/신규/총조회수) — Redis 캐싱 |
| GET | `/v1/statistics/member-trend?from&to` | 일별 가입 추이 |
| GET | `/v1/statistics/top-contents?limit` | 조회수 Top N |
| GET | `/v1/statistics/watch-by-channel` | 채널별 시청시간 집계 |

---

## 10. 화면 명세 (streamhub-web)

| 화면 | 경로 | 핵심 컴포넌트/패턴 |
|---|---|---|
| 로그인 | `/login` | RHF+Zod, NextAuth signIn |
| 공통 레이아웃 | layout | 사이드바(role 기반 메뉴) + 헤더 |
| 회원 목록 | `/member` | 검색폼 → AG Grid → 체크박스 일괄 승인/거부 |
| 회원 상세 | `/member/[id]` | RHF 폼, 보기→수정 토글 |
| 콘텐츠 목록 | `/content` | 검색폼 → AG Grid → 상태 뱃지 |
| 콘텐츠 등록/수정 | `/content/add`, `/content/[id]` | RHF + 파일 업로드(드래그앤드롭) + 해시태그 입력 |
| 대시보드 | `/dashboard` | 요약 카드 + ApexCharts(추이/Top N/채널별) |

---

## 11. 구현 로드맵 (Phase별 체크리스트)

각 Phase 끝에서 **실제 실행 + 검증** 후 다음 단계 진행.

### Phase 0 — 기반 (인증 + 공통 인프라)
- [x] 모노레포 + `docker-compose.yml` (MySQL/Redis/MinIO) — Colima로 구동, 3종 healthy
- [x] 백엔드 스캐폴딩: Security, JWT, ResultDTO, GlobalExceptionHandler, Redis, MyBatis, Swagger
- [x] `/auth/login` `/auth/refresh` `/auth/logout` + ADMIN_ACCOUNT 시드 (admin/manager)
- [x] 프론트 스캐폴딩: Next.js 14.2 + NextAuth v5(beta.25) + custom-instance + Orval설정 + 레이아웃/사이드바
- [x] ✅ **백엔드 검증(curl)**: 로그인·/me·미인증401·오답·갱신·로그아웃무효화·Swagger 모두 통과
- [x] ✅ **프론트 통합 검증(브라우저)**: 미인증 리다이렉트·로그인→대시보드·로그아웃→Redis 토큰삭제 통과
  - 토큰 자동 갱신은 표준 패턴(jwt 콜백 선제 갱신)으로 재작성 — refresh 토큰은 클라이언트 미노출, signOut 이벤트로 서버측 revoke

> **Phase 0 완료.** 다음: AWS 배포는 Phase 1(회원관리) 완성 후. 로컬 인프라는 Colima로 구동.

### Phase 1 — 회원관리 ✅ 완료
- [x] DDL + 시드 (country/region/church/member) — Hibernate ddl-auto + DataInitializer(60명, 5교회, 상태/라이브 분산)
- [x] 백엔드: MemberController/Service/Mapper(XML 동적검색)/Repository + ResInfinityList + AdminPrincipal RBAC 스코핑
- [x] `npm run gen`(Orval) → 프론트 목록(AG Grid)/상세(RHF 보기·수정)/일괄처리
- [x] ✅ **검증(curl+Playwright)**: 검색·필터·페이징(off-by-one 수정)·상세수정(saveAndFlush 수정)·일괄승인·RBAC 모두 E2E 통과
  - 발견·수정: 페이지네이션 1-based↔0-based, MyBatis 읽기 전 JPA flush, MySQL 대소문자(네이밍전략), orval ajv 의존성(Node20+ajv8 직접의존)

> **Phase 1 완료.**

### 배포(AWS) — 자산 준비 완료, 적용 대기
- [x] 백엔드 컨테이너화: `streamhub-api/Dockerfile` + `application-prod.yml`(env 기반, 실제 S3/IAM 역할)
- [x] IaC: `deploy/terraform/`(EC2+Redis / RDS MySQL / S3 / ECR / SSM 시크릿 / IAM, 기본 VPC) — `terraform validate` 통과
- [x] 배포 스크립트 `deploy/scripts/deploy-api.sh`(amd64 빌드→ECR→SSM 롤) + CI/CD `.github/workflows/`
- [x] 런북 `deploy/README.md`(새 계정 체크리스트·과금 알람·https(Caddy)·철거)
- [ ] ⏳ **적용**: 사용자가 새 AWS 계정 준비 후 `terraform apply` → `deploy-api.sh` → Vercel
  - 기존 이전 AWS 계정 계정 사용 금지(사용자 지시). 혼합콘텐츠(https→http) 해결 필요 = Caddy+도메인.

### Phase 2 — 콘텐츠(영상) 관리 ✅ 완료
- [x] DDL + 시드 (channel/content/hashtag/content_hashtag/content_file) — 5채널/8해시태그/24콘텐츠
- [x] 백엔드: ContentController/Service/Mapper(해시태그 GROUP_CONCAT) + S3Config(MinIO↔실제S3 분기) + StorageService 업로드/삭제/URL
- [x] 프론트: 목록(AG Grid 썸네일/배지/칩)·상세(보기/수정/삭제)·등록(드래그앤드롭 업로드 + 채널선택 + 해시태그)
- [x] ✅ **검증(curl+Playwright)**: 업로드→MinIO→생성→썸네일 노출, 수정, 삭제(S3 객체까지), 필터/페이징 E2E 통과
  - S3 SDK 코드가 로컬 MinIO에 실제 업로드 동작(운영 전환 시 코드 변경 0). 생성된 useUpload는 멀티파트 깨져서 수동 FormData 헬퍼 사용.

### Phase 3 — 통계 대시보드 ✅ 완료
- [x] 시드: watch_history 800건
- [x] 백엔드: StatService(MyBatis 집계 4종) + **Redis 캐싱**(`@Cacheable`+RedisCacheManager, summary 60s TTL)
- [x] 프론트: 대시보드(요약 카드 4 + ApexCharts 3: 가입추이 area / Top5 bar / 채널별 donut)
- [x] ✅ **검증(curl+Playwright)**: 집계 정확·캐시 히트/미스(로그 1회)·차트 렌더 E2E 통과

### Phase 4 — 마감 (포트폴리오 완성도) ✅ 완료
- [x] README: 아키텍처 다이어그램, 기술 선택 이유, 실행법(`docker-compose up`) — 루트 `README.md`
- [x] 핵심 비즈니스 로직 테스트 (JUnit 5) — JWT 발급/검증/회전 5건 + 회원 RBAC 스코핑·일괄처리 6건, **11/11 통과**
- [x] 시드 데이터로 "클론 후 즉시 데모" 보장 (5교회·60회원·24콘텐츠·800시청이력)

### Phase 5 — Redis + SQS + 감사 로그 ✅ 완료 (확장)
- [x] SQS(LocalStack 로컬↔실제 SQS 운영) + spring-cloud-aws `@SqsListener`
- [x] 감사 로그 도메인: 액션 발행(로그인/회원승인·거부·수정/콘텐츠 생성·수정·삭제) → SQS → 소비 → ACTION_LOG 저장 → 조회
- [x] 프론트: 감사 로그 페이지(필터/검색/페이징, 색상 뱃지) + 사이드바 메뉴(SYSTEM 전용)
- [x] Terraform: SQS 큐+DLQ+IAM 추가
- [x] ✅ **검증(curl+Playwright)**: 로그인/승인 액션이 SQS 거쳐 실시간 적재·조회 E2E 통과

### 배포 적용 — plan 검증 완료, apply는 보류(비용)
- [x] 새 AWS 계정 연결, `terraform plan` 성공 — 18개 자원 검증, **실제 생성 0 / $0**
- [x] 자격증명: `aws login` 임시세션 → env 주입으로 plan (토큰을 Go SDK가 못 읽는 이슈 우회)
- [~] **apply는 안 함** — AWS 프리티어가 2025 개편(6개월/$200 크레딧, 상시 ~$34/월)이라 라이브 불필요 판단. 배포 역량은 plan-verified IaC로 증명.

> **🎉 전체 완료 — Phase 0~5 + 배포자산(plan 검증).** 3개 도메인 + 인증 + 감사로그(SQS) + 문서/테스트. 라이브 배포는 보류(필요 시 env-주입 apply 한 번).

---

## 12. 확정된 결정사항

1. **프로젝트명** — ✅ `streamhub-admin` (api: `streamhub-api`, web: `streamhub-web`)
2. **ADMIN_ACCOUNT vs MEMBER 분리** — ✅ 분리 (운영자 계정 / 관리대상 회원)
3. **AG Grid Community 다운그레이드** — ✅ 확정 (Enterprise 라이선스 회피)
4. **다국어 제외** — ✅ 한국어 단일
5. **테스트 범위** — ✅ 핵심 비즈니스 로직만 (Phase 4)

> **Phase 0 착수 중.**
