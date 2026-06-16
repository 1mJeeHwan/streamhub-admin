# StreamHub User Site — 설계문서 (사용자용 미디어 웹앱)

> **상태:** 설계 확정, 구현 대기 (컨텍스트 초기화 후 이 문서만으로 이어서 진행)
> **작성 시점 기준:** streamhub-admin 프로젝트의 관리자측(Phase 0~5)은 이미 완성·검증됨. 이 문서는 **사용자용 미디어 사이트**를 추가하는 계획.

---

## 0. 핸드오프 컨텍스트 (기존 프로젝트 현황 — 먼저 읽을 것)

**프로젝트 루트:** `/Users/imjihwan/Documents/MyToys/streamhub-admin/` (MyToys 자체는 git repo 아님)

**이미 완성된 것 (관리자측, 전부 로컬 검증 완료):**
- `streamhub-api/` — Spring Boot 3.4 / Java 21. 패키지 `org.streamhub.api`. 구조: `base/`(config·jwt·security·response·exception·storage) + `auth/` + `v1/{admin,member,content,statistics,actionlog}`.
  - 도메인: 인증(JWT stateless), 회원관리, 콘텐츠관리(영상/음원 + 파일업로드), 통계(Redis 캐싱), 감사로그(SQS).
  - 공통: `ResultDTO<T>`{resultCode("0000"=성공),resultMessage,resultObject}, `ResInfinityList<T>`{contents,totalCount,totalPage}, JPA+MyBatis 하이브리드, `@PreAuthorize`+`AdminPrincipal`.
- `streamhub-web/` — Next.js 14 관리자 프론트(NextAuth v5, React Query, Orval, AG Grid, ApexCharts). 포트 3000.
- `deploy/` — Terraform(EC2/RDS/S3/SQS/ECR/SSM, plan 검증 완료·apply 안 함), docker-compose, 런북.
- `docker-compose.yml` — MySQL(3306)·Redis(6379)·MinIO(9000/9001)·LocalStack(SQS,4566).

**실행법:**
```bash
colima start && docker compose up -d                 # 인프라
cd streamhub-api && ./mvnw spring-boot:run           # 백엔드 8080
cd streamhub-web && nvm use 20 && npm run dev        # 관리자 프론트 3000
```
**시드 로그인(관리자):** admin/admin1234(SYSTEM), manager/manager1234(CHURCH_MANAGER).

**⚠️ 툴체인 함정 (재발견 방지):**
- 프론트 npm/orval은 **Node 20 LTS**(`nvm use 20`). Node 25는 orval의 ajv 충돌.
- 새 Next 프로젝트는 `package.json`에 **`ajv@^8.17.1`+`ajv-keywords@^5` 직접 devDependency** + `overrides`로 eslint만 ajv6/ajv-keywords3 격리 (안 그러면 `npm run gen` 크래시). `npm install --legacy-peer-deps`.
- MySQL은 Colima(리눅스)라 **테이블명 대소문자 구분** → JPA `physical-strategy=PhysicalNamingStrategyStandardImpl`(이미 적용됨, 대문자 테이블).
- Playwright가 gstack browse보다 안정적(검증용). combobox는 옵션 라벨로 select, React 제어입력은 네이티브 setter+이벤트.

**기존 CONTENT 데이터 모델 (재사용 대상):**
- `CONTENT`: id, channel_id, type(`VIDEO`|`SOUND`), title, description, thumbnail_key, media_url, duration_sec, view_count, status(`DRAFT`|`PUBLISHED`), created_at, updated_at.
- `CHANNEL`(church_id,name), `HASHTAG`+`CONTENT_HASHTAG`(다대다), `CONTENT_FILE`(s3_key).
- MyBatis: `ContentMapper.selectList(keyword,type,status,channelId,offset,size)` / `countList` / `selectDetail(id)` — 이미 status 필터 지원하므로 **공개측은 status="PUBLISHED" 고정**해서 재사용.
- 썸네일 URL: `StorageService.publicUrl(key)` (MinIO/S3).
- 시드: 5채널·24콘텐츠(VIDEO 16/SOUND 8, PUBLISHED 다수). **단, media_url이 가짜(`https://stream.example.com/...`)라 재생 안 됨 → 아래 U1에서 실제 샘플로 교체 필요.**

---

## 1. 이번 목표 & 범위 (v1)

실제 레퍼런스 서비스의 사용자용 미디어 사이트(`사용자 앱`+`사용자 API`)에 해당하는 **공개 미디어 웹앱**을 핵심 기능만 추려 추가.
- **Flutter 미사용** — 순수 웹앱(Next.js).
- **제공 콘텐츠 3종:** 게시글(텍스트 글), 영상(VIDEO), 음악(SOUND).
- **디자인 자유** (관리자와 똑같을 필요 없음).
- **v1 = 공개 읽기전용:** 로그인·구독·결제·재생목록·시청기록 **없음**. 그냥 둘러보고 재생/읽기.
- 핵심 증명 포인트: **관리자에서 "게시(PUBLISHED)"한 콘텐츠만 사용자 사이트에 노출** → 관리자↔사용자 연동.

**v2 이후(후순위, 이번 범위 아님):** 사용자 로그인(member 테이블 활용), 재생목록, 시청기록(watch_history 쓰기), 구독.

---

## 2. 아키텍처 결정 (확정)

- **새 프론트엔드:** `streamhub-user-web/` (Next.js 14, TS, Tailwind, React Query, Orval). 포트 **3001**(관리자 3000과 공존). 인증 없음(공개).
- **백엔드:** 새 서비스 만들지 않고 **기존 `streamhub-api`에 공개 네임스페이스 `/pub/v1/**` 추가**. `SecurityConfig`의 `PUBLIC_PATHS`에 `/pub/**` 추가(permitAll, JWT 불필요). 공유 DB·기존 Mapper 재사용.
  - (실제 시스템은 사용자 API/ng-admin-api로 서비스 분리. v1은 단순화 — 이유: 컨텍스트/작업량 절약. 문서에 "추후 별도 서비스 분리 가능" 명시.)
- **새 도메인:** `post`(게시글) — `org.streamhub.api.v1.post`.

---

## 3. 새 백엔드 작업 (streamhub-api)

### 3.1 post(게시글) 도메인 — 신규
- 엔티티 `POST`: id, title, body(TEXT, length 5000), thumbnail_key(nullable), status(`DRAFT`|`PUBLISHED` enum), created_at, updated_at.
- `PostRepository`(JPA), `PostMapper`+XML(목록 검색/페이지네이션, PUBLISHED 필터), DTO(PostListItem, PostDetail, PostSearch).
- 시드: DataInitializer에 `seedPosts()` 추가 — 공지/안내성 게시글 ~10건(PUBLISHED), thumbnail은 nullable 허용.
- (관리자측 post 관리 화면은 **선택/후순위** — v1은 시드로 충분. 원하면 streamhub-web에 /post CRUD 추가 가능.)

### 3.2 공개 엔드포인트 `/pub/v1/**` (permitAll)
신규 컨트롤러들(인증/RBAC 없음). 모두 `ResultDTO`로 감쌈. **PUBLISHED만 반환.**
- `GET /pub/v1/contents?type=VIDEO|SOUND&keyword=&pageNumber=&pageSize=` → `ResInfinityList<ContentListItem>` (status=PUBLISHED 고정). 기존 `ContentMapper`/`ContentService` 재사용(공개용 메서드 추가, status 강제).
- `GET /pub/v1/contents/{id}` → `ContentDetail` (+ **view_count +1**; PUBLISHED 아니면 404).
- `GET /pub/v1/posts?keyword=&pageNumber=&pageSize=` → `ResInfinityList<PostListItem>`.
- `GET /pub/v1/posts/{id}` → `PostDetail`.
- `GET /pub/v1/home` → 홈 묶음: 최신 영상 N개 + 최신 음악 N개 + 최신 게시글 N개 (편의용 1콜).
- `SecurityConfig.PUBLIC_PATHS`에 `/pub/**` 추가. CORS 허용 오리진에 `http://localhost:3001` 추가.

### 3.3 실제 재생 가능한 샘플 미디어 시드 (중요)
기존 가짜 media_url을 **공개 샘플**로 교체(시드에서 일부라도):
- 영상(MP4): `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4`, `ForBiggerBlazes.mp4`, `ForBiggerEscapes.mp4` 등 (구글 공개 샘플, 다수 존재).
- 음악(MP3): `https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3` ~ `-16.mp3` (공개 샘플).
- 썸네일: 영상은 공개 썸네일 URL 또는 placeholder; 음악은 앨범아트 placeholder.
- DataInitializer의 콘텐츠 시드 media_url을 위 URL들로 순환 할당하도록 수정(또는 신규 시드 몇 건). HTML5 미디어 재생은 CORS 불필요(태그 재생).

---

## 4. API 계약 요약 (사용자 프론트가 소비)

| Method | Path | 반환(resultObject) |
|---|---|---|
| GET | `/pub/v1/home` | `{videos:ContentListItem[], musics:ContentListItem[], posts:PostListItem[]}` |
| GET | `/pub/v1/contents?type&keyword&pageNumber&pageSize` | `ResInfinityList<ContentListItem>` |
| GET | `/pub/v1/contents/{id}` | `ContentDetail` (mediaUrl·thumbnailUrl·hashtags 포함, viewCount+1) |
| GET | `/pub/v1/posts?keyword&pageNumber&pageSize` | `ResInfinityList<PostListItem>` |
| GET | `/pub/v1/posts/{id}` | `PostDetail` (title·body·createdAt) |

`ContentListItem`/`ContentDetail` 필드는 기존 그대로(streamHubAdminAPI.schemas 참조): id,title,type,status,channelName,thumbnailUrl,mediaUrl,durationSec,viewCount,hashtags,createdAt,description.

---

## 5. 프론트엔드 `streamhub-user-web` (Next.js 14)

- 스택: Next 14 App Router, TS, Tailwind, React Query v5, axios, Orval(공개 API), **인증 없음**. 포트 3001(`next dev -p 3001`).
- Orval: input `http://localhost:8080/v3/api-docs`, /pub 엔드포인트도 swagger에 잡힘. mutator=custom axios(인증 헤더 불필요, baseURL=8080). (관리자 web과 별개 프로젝트.)
- **디자인:** 자유 — 미디어 사이트답게 다크 톤 + 카드/그리드 권장(관리자와 다르게). 깔끔하면 됨.

**페이지:**
- `/` (홈) — useHome: 영상 캐러셀/그리드 + 음악 + 게시글 최신 섹션.
- `/video` 목록(그리드, 검색) · `/video/[id]` — **HTML5 `<video controls>`** 플레이어 + 제목/설명/해시태그/조회수.
- `/music` 목록 · `/music/[id]` — **HTML5 `<audio controls>`** 플레이어 + 썸네일/제목.
- `/posts` 목록 · `/posts/[id]` — 게시글 본문(텍스트/HTML) 렌더.
- `/search` — 키워드로 영상+음악(+게시글) 통합 검색(또는 각 목록의 검색으로 대체).
- 공통: 헤더(로고+네비 영상/음악/게시글+검색), 반응형 그리드, 로딩/빈상태.
- 플레이어 참고: media_url이 .mp4/.mp3면 HTML5 태그로 충분. (HLS .m3u8 쓸 거면 hls.js 추가 — v1은 MP4/MP3 샘플이라 불필요.)

---

## 6. 구현 로드맵 (Phase U0~U3)

- **U0 — 백엔드 공개 API:** post 도메인 + `/pub/v1/**` 컨트롤러 + PUBLIC_PATHS/CORS + 샘플 미디어 시드. → curl 검증(공개 접근, PUBLISHED만, view_count+1).
- **U1 — 프론트 스캐폴딩:** streamhub-user-web 생성(Node20+ajv override), Orval gen, 레이아웃/헤더/홈. → 홈 렌더 확인.
- **U2 — 영상/음악:** 목록+상세+플레이어(실제 재생). → Playwright로 재생/목록 검증.
- **U3 — 게시글 + 검색 + 마감:** posts 목록/상세, 검색, README에 사용자 사이트 추가. → 검증.
- 각 Phase 끝에서 빌드 클린 + 실제 실행 검증(관리자측과 동일 규율).

---

## 7. 확정 결정 / 메모

1. v1 공개 읽기전용(로그인 없음) — ✅ 확정.
2. 백엔드는 streamhub-api에 /pub 추가(별도 서비스 X) — ✅ 확정(추후 분리 가능).
3. 게시글=새 post 도메인, 영상/음악=기존 CONTENT 재사용 — ✅.
4. 실제 재생: 공개 샘플 MP4/MP3로 시드 교체 — ✅.
5. 프론트 포트 3001, 디자인 자유(다크 권장) — ✅.

> **다음 세션 시작점:** 이 문서 + `streamhub-admin-project` 메모리 읽기 → 인프라/백엔드 띄우기 → **Phase U0**(백엔드 공개 API)부터.
