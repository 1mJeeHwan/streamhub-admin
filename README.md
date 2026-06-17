# StreamHub

**한국어** · [English](README.en.md)

교회/스트리밍 플랫폼을 **실제 운영 서비스의 프로덕션 스택 그대로** 재현한 풀스택 포트폴리오 프로젝트입니다.
하나의 백엔드(Spring Boot) 위에 **운영자용 관리자 콘솔**과 **공개 사용자 미디어 사이트** 두 개의 프론트엔드가 올라가며,
인증·RBAC·파일 업로드·통계 캐싱·비동기 감사로그·회원 로그인까지 **동작하는 수직 슬라이스**로 구성됩니다.
로컬은 `docker-compose`, 배포는 AWS(Terraform)로 바로 띄울 수 있습니다.

> **데모 계정**
> - 관리자 콘솔 — `admin` / `admin1234` (시스템), `manager` / `manager1234` (교회 관리자)
> - 사용자 사이트 — `member01@streamhub.test` / `member1234`

---

## 스크린샷

### 사용자 사이트 (모바일 · 다크)
<p>
  <img src="docs/screenshots/user-home.png" width="200" alt="홈" />
  <img src="docs/screenshots/user-video.png" width="200" alt="영상 상세" />
  <img src="docs/screenshots/user-search.png" width="200" alt="통합 검색" />
  <img src="docs/screenshots/user-mypage.png" width="200" alt="마이페이지" />
</p>

### 관리자 콘솔 (데스크탑)
<img src="docs/screenshots/admin-dashboard.png" width="860" alt="통계 대시보드" />

| 회원관리 (AG Grid) | 콘텐츠관리 |
|---|---|
| <img src="docs/screenshots/admin-members.png" width="420" alt="회원관리" /> | <img src="docs/screenshots/admin-content.png" width="420" alt="콘텐츠관리" /> |

---

## 아키텍처

```
┌─────────────────────────────┐   ┌─────────────────────────────┐
│ streamhub-web  (관리자 콘솔)  │   │ streamhub-user-web (사용자)   │
│ Next14·NextAuth v5·React Query│   │ Next14·React Query·모바일 UI  │
│ AG Grid·ApexCharts·RHF+Zod   │   │ 공개(읽기전용)+회원 로그인     │
└──────────────┬──────────────┘   └──────────────┬──────────────┘
   /v1/** (Bearer JWT, 관리자)        /pub/v1/** (공개) · /pub/v1/auth (회원)
               └───────────────┬───────────────────┘
                               ▼
        ┌──────────────────────────────────────────────┐
        │       streamhub-api (Spring Boot 3.4)          │
        │  SecurityFilterChain (stateless JWT)           │
        │   └ 관리자 토큰 ↔ 회원 토큰 격리 (role 클레임)   │
        │  Controller → Service                          │
        │   ├ Repository (JPA, 단순 CRUD)                │
        │   └ Mapper (MyBatis, 동적 검색·조인·집계)       │
        └────┬──────────┬───────────┬──────────┬─────────┘
          MySQL 8     Redis      S3 / MinIO   SQS / LocalStack
          (주 DB)    (캐시)    (미디어 저장)  (감사로그 큐)
```

**핵심 설계 결정**
- **JPA + MyBatis 하이브리드** — 단순 CRUD는 JPA, 동적 검색·조인·집계는 MyBatis XML.
- **Stateless JWT + 토큰 격리** — 관리자 토큰(role 클레임)과 회원 토큰(`type:member`, role 없음)을 분리해, 회원 토큰으로는 관리자 API에 절대 닿지 못하게 필터에서 차단.
- **토큰 자동 회전** — NextAuth jwt 콜백에서 만료 전 선제 갱신, refresh 토큰은 Redis 화이트리스트로 로그아웃 시 무효화.
- **S3 SDK 무전환** — 로컬 MinIO ↔ 운영 S3를 `storage.endpoint` 유무로만 분기, **코드 변경 0**.
- **비동기 감사로그** — 주요 액션을 SQS로 발행 → `@SqsListener`가 소비해 영속화(best-effort, 실패해도 본 트랜잭션 무영향).
- **RBAC** — `@PreAuthorize` + JWT 클레임 기반 `AdminPrincipal`로 교회 관리자를 본인 교회 데이터로 스코핑(DB 조회 없음).
- **API 계약 자동화** — 백엔드 Swagger → Orval → 타입 안전 React Query 훅(관리자측).

---

## 무엇이 들어있나

### 관리자 콘솔 (`streamhub-web`)
| 도메인 | 보여주는 기술 |
|---|---|
| **회원관리** | 동적 검색/필터/페이지네이션(MyBatis) · 상세/수정 · 일괄 승인/거부 · RBAC 교회 스코핑 |
| **콘텐츠관리** | CRUD · 해시태그 다대다 · **파일 업로드(드래그앤드롭 → MinIO/S3)** · 복잡 조인 |
| **통계 대시보드** | MyBatis 집계 · **Redis 캐싱(@Cacheable)** · ApexCharts(추이/Top N/도넛) |
| **감사 로그** | 관리자 액션을 SQS로 발행/소비해 기록, 시스템 관리자만 조회 |

### 사용자 사이트 (`streamhub-user-web`)
실제 운영 서비스의 사용자 앱 톤을 따른 모바일 우선 공개 미디어 사이트.
- **영상 · 음악**(HTML5 플레이어) · **게시글** 열람 — 관리자가 `PUBLISHED`한 콘텐츠만 노출
- **URL 기반 통합 검색**(`?q=`, 공유·새로고침 유지, 제목 기준) · 페이지네이션
- **회원 로그인 + 마이페이지** — 회원 전용 JWT, localStorage 세션, 보호 라우트

---

## 포트폴리오 어드민 확장 (Goods · Orders · Membership · Dashboard)

그누보드5/영카트5 관리자의 **운영 기능 폭**을 PalmPlus(교회·스트리밍) 도메인으로 재해석해,
단순 CRUD를 넘어 **주문 상태머신 · 정기빌링 · 포인트 원장 · 통합 운영 대시보드**까지 동작하는 신규 어드민 도메인을 추가했습니다.
설계 의도·도메인 매핑·시드 전략은 [`docs/portfolio-admin-design.md`](docs/portfolio-admin-design.md) 참고.

### 추가된 도메인 / 화면

| 도메인 | 어필 키워드 | 라우트 |
|---|---|---|
| **굿즈샵** | 상품·카테고리(트리)·옵션/재고·갤러리 이미지 · Pareto 판매분포 | `/goods` · `/catalog` |
| **주문** | **상태머신** `PLACED→PAID→READY→SHIPPING→DONE` (분기 `CANCEL`/`RETURN`), 전이 시 재고·합계 재계산 | `/order` |
| **정기후원·멤버십 구독** | **빌링 CRON 시뮬**(`@Scheduled`, 5분 주기 스캔→회차 청구), 플랜·등급(브론즈/실버/골드/후원천사) | `/subscription` · `/subscription-plan` · `/billing-calendar` |
| **후원 내역** | 정기/단건 후원 집계, 캠페인 연동 | `/donation` |
| **포인트 원장** | **append-only 원장**(delta·balanceAfter), 후원 적립·만료 스케줄러 | `/point` |
| **통합 운영 대시보드** | KPI 스트립 + ApexCharts(추이/Top N/도넛) + 실시간 활동 피드 + **할 일 큐** · MyBatis 집계 + Redis 캐시 | `/admin-ops` · `/dashboard` |
| **기능 카탈로그** | 만든 화면 전시, `live` / `mock` 상태 **정직 배지** | `/catalog` |

### 시드 데이터 규모 (실측)

결정론적 `PortfolioSeeder`(고정 seed, 리셋해도 동일)가 **최근 6개월 운영 패턴**으로 생성:

| 굿즈 | 주문 | 후원 | 포인트 원장 | 구독 | 멤버십 플랜 |
|---|---|---|---|---|---|
| 64 | 1,700 | 1,400 | 1,306 | 24 | 4 |

- **현실적 분포** — 주문 70/15/10/5 (DONE/SHIPPING·READY/PAID/CANCEL·RETURN), 굿즈 Pareto 롱테일, 일부 품절·재고경고.
- **저작권 안전 이미지** — Picsum `seed` URL(검증된 HTTP 200 호스트)만 사용.
- **마스킹 PII** — `faker(ko)` + 마스킹(`김O준`, `010-****-1234`), 실 개인정보 0건.
- **"데모 · 테스트 모드" 배지** — 실결제/실발송 없음을 명시.

### 검증 상태

- ✅ 백엔드 — 컴파일 / 부팅 / 신규 엔드포인트 200 응답.
- ✅ 프론트 — 빌드 그린.
- ✅ 라이브 UI 스모크 — 대시보드 차트 4종 · 굿즈 실이미지 렌더 통과.

> **알려진 개선점** — 일부 today-KPI 집계가 0으로 표시되는 등 폴리시가 남아 있습니다(데모 데이터 분포 기준일 정렬).

---

## 위치기반 · 커머스 확장 (교회찾기 · 예배신청 · CCM 커머스 · 결제 · SMS · 챗봇)

실제 운영 서비스(교회114·두란노몰·토스페이먼츠·알리고 등)를 **웹 리서치**한 뒤,
교회찾기·예배/새가족 신청·CCM 음반 커머스·결제·SMS·챗봇을 추가했습니다.
핵심 제약은 **외부 API 키 0** — 모든 데이터는 결정론적 **목업/시드**로 채우고,
추후 실연동으로 갈아끼울 수 있도록 도메인마다 **어댑터 seam**(인터페이스 + Provider + `.env` 플래그)을 두었습니다.
런타임 외부 호출은 없으며 화면에는 **"데모 · 테스트 데이터" 배지**를 명시합니다.
조사 결과·교단 데이터 한계·단계별 플랜은 [`docs/expansion-research-and-plan.md`](docs/expansion-research-and-plan.md) 참고.

### 추가된 기능

| 도메인 | 어필 키워드 | 라우트 / 엔드포인트 |
|---|---|---|
| **교회찾기** | **Leaflet + OSM**(키 없는 지도 타일) · **Haversine 거리** 거리순 정렬 · 교단/반경/키워드 필터 · 브라우저 **Geolocation**(거부 시 fallback) · 마커·예배시간·길찾기 | 사용자 `/churches` · `/churches/[id]` · `GET /pub/v1/churches`, `/pub/v1/churches/{id}` / 관리자 `/v1/churches/**`(CRUD) |
| **예배 · 새가족 신청** | 다단계 등록 폼(인적/주소/신앙배경) · **동적 가족 배열(최대 5)** · RHF + Zod 검증 · 관리자 신청 목록·상태 전이 | 사용자 `/churches/[id]/register` · `POST /pub/v1/worship` / 관리자 `POST /v1/worship/list`, `PATCH /v1/worship/{id}/status` |
| **CCM 음반** | **앨범 ↔ `GOODS_ITEM` 브리지**(구매=기존 주문 도메인 흡수) · `Album→Track(1:N)` · **30초 HTML5 `<audio>` 미리듣기**(단일 전역 오디오) · 오프라인 직영점 **매장지도** | 사용자 `/albums` · `/albums/[id]` · `/stores` · `GET /pub/v1/albums`, `/pub/v1/albums/{id}/tracks/{trackId}/preview`, `/pub/v1/stores` / 관리자 `/v1/album/**`, `/v1/store/**` |
| **결제** | **`PaymentProvider` seam**(`mock`/`toss`/`paypal`/`kakao`/`card`) · `request → approve → receipt` 플로우 · `testMode` 영수증 | `POST /v1/payment/request`, `/v1/payment/approve` · `GET /v1/payment/{orderId}/receipt` |
| **SMS** | **`SmsSender` seam**(`mock`/`aligo`/`solapi`) · 주문/예배신청 알림 · 관리자 **커스텀 발송** · 발송 이력 | `POST /v1/sms/send`, `/v1/sms/list` |
| **챗봇** | **`ChatProvider` seam**(`rule`/`llm`) · 룰베이스 **의도 분류**(상품문의/FAQ/주문조회) · 세션·히스토리 영속 | 사용자 위젯 `ChatbotWidget` · `POST /v1/chat/send` · `GET /v1/chat/{sessionKey}/history` |

### 어댑터 seam — 실키 주입 지점

실제 연동은 코드 분기가 아니라 **빈 교체 + `.env` 플래그**로 전환됩니다(서비스는 인터페이스에만 의존).

| seam (인터페이스) | 플래그 | 기본(목업) → 실연동 |
|---|---|---|
| `PaymentProvider` | `app.payment.provider`, `app.payment.test-mode` | `mock` → `toss`/`paypal`/`kakao` (테스트키 주입) |
| `SmsSender` | `app.sms.sender` | `mock` → `aligo`/`solapi` (API 키·발신번호) |
| `ChatProvider` | `app.chat.provider`, `app.chat.llm.api-key` | `rule` → `llm` |
| `MusicPreviewProvider` | `app.music.provider` | `seed` → `external` (음원 API) |
| `GeocodeProvider` | `church.geocode.provider`, `church.geocode.kakao-rest-key` | `seed` → `kakao` (Kakao Local) |
| `PostcodeProvider` | `app.worship.postcode.provider` | `mock` → 우편번호 검색 API |
| `MapProvider` (프론트) | — | Leaflet/OSM → Kakao Maps SDK 교체 가능하도록 추상화 |

### 데이터 / 공공데이터 결론

**교단별 교회 공공데이터는 사실상 부재** — data.go.kr·LOCALDATA·Kakao 어디에도 교단 구분 필드와 좌표를 함께 주는 데이터셋이 없습니다.
그래서 위치검색은 "데이터 보유"가 아닌 **지도 API 호출** 방식이 정답이며(실서비스 전제: Kakao Local + Geolocation),
본 포트폴리오는 키 없이 **결정론적 시드 ~40곳**(서울 28 + 경기 12, 좌표·교단 휴리스틱·예배시간)으로 근사합니다.
실 상호·주소가 특정되지 않도록 좌표는 인덱스 기반 ±오프셋으로 가공합니다. 상세 근거는 위 리서치 문서 참고.

### 검증 상태

- ✅ 백엔드 — **48테스트 통과**, DB **재시드 부팅** 정상, 신규 엔드포인트 **200 응답**(churches/worship/albums/stores/payment/sms/chat).
- ✅ 사용자 사이트 — **prod 빌드 그린**, **Playwright**(교회 지도·앨범 미리듣기·챗봇) 통과.
- ⚠️ **정직 고지** — 결제·SMS·챗봇·지도 모두 **데모/테스트 모드**(실결제·실발송·실외부호출 없음). **관리자 관리화면(교회/예배/앨범/매장/SMS) 일부와 AWS 라이브 연동은 미구현(향후)**.

---

## 기술 스택

**백엔드** — Spring Boot 3.4 · Java 21 · MySQL 8 · Redis · JPA(Hibernate) + MyBatis · Spring Security + JWT(auth0) · AWS SDK v2(S3/SQS) · spring-cloud-aws · springdoc OpenAPI · Lombok · JUnit 5 + Mockito

**프론트엔드** — Next.js 14(App Router) · React 18 · TypeScript · TanStack React Query v5 · NextAuth v5(관리자) · Zustand · Orval · AG Grid Community · ApexCharts · React Hook Form + Zod · Tailwind CSS

**인프라** — Docker Compose(로컬) · Terraform(AWS: EC2/RDS/S3/SQS/ECR/SSM) · Vercel(프론트) · GitHub Actions(CI/CD)

---

## 로컬 실행

사전 준비: Docker(또는 Colima), JDK 21, Node 20.

```bash
# 1) 인프라 (MySQL + Redis + MinIO + LocalStack)
docker compose up -d

# 2) 백엔드 (localhost:8080) — 첫 기동 시 스키마 생성 + 데모 데이터 시드
cd streamhub-api && ./mvnw spring-boot:run

# 3) 관리자 콘솔 (localhost:3000)
cd streamhub-web && npm install --legacy-peer-deps && npm run dev

# 4) 사용자 사이트 (localhost:3001)
cd streamhub-user-web && npm install --legacy-peer-deps && npm run dev
```

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- MinIO 콘솔: http://localhost:9001 (streamhub / streamhub123)
- 관리자 API 클라이언트 재생성(백엔드 기동 상태에서): `cd streamhub-web && npm run gen`

> Node 20 LTS 권장. 시드: 5교회·60회원·24콘텐츠·10게시글·800 시청이력.

---

## 테스트

```bash
cd streamhub-api && ./mvnw test
```
JUnit 5 + Mockito 단위 테스트 — JWT 발급/검증/회전 + **관리자↔회원 토큰 격리**, 회원 RBAC 스코핑·상태 전이,
회원 로그인(상태 게이트·실패 경로), 공개 게시글의 `PUBLISHED` 강제 노출.

---

## 배포 (AWS)

`deploy/README.md` 참고 — Terraform으로 EC2/RDS/S3/SQS/ECR를 만들고, `deploy/scripts/deploy-api.sh`로
이미지를 ECR에 푸시 후 SSM으로 무중단 배포, 프론트는 Vercel. `terraform destroy`로 한 번에 정리(비용 안전).

---

## 프로젝트 구조

```
streamhub-admin/
├── streamhub-api/        # Spring Boot (org.streamhub.api: base/ · auth/ · v1/{admin,member,content,statistics,actionlog,post,pub,goods,order,donation,dashboard})
│                         #   포트폴리오 확장: v1/{goods,order,donation,dashboard} + member/Point* · base/config/PortfolioSeeder
├── streamhub-web/        # 관리자 Next.js (src/app/(protected)/{admin-ops,goods,order,donation,subscription,subscription-plan,point,catalog,billing-calendar} · src/apis/query[Orval])
├── streamhub-user-web/   # 사용자 Next.js (src/app · src/components · src/lib[수동 fetch+RQ])
├── deploy/               # Terraform IaC · 배포 스크립트 · 런북
├── docker-compose.yml    # MySQL · Redis · MinIO · LocalStack
└── PLAN.md / USER-SITE-PLAN.md  # 설계문서 + 구현 로드맵
```
