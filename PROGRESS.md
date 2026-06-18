# StreamHub — 진행상황 / 현황 문서 (2026-06-18)

> 이 문서는 작업 연속성을 위한 **단일 기준점**이다. 컨텍스트 한계 대비 스냅샷.
> 브랜치 `feat/portfolio-admin-extension` (origin 동기화, 최신 `b90e035`).
> 구성: **백엔드** `streamhub-api`(Spring Boot 3.x/Java 21) · **관리자** `streamhub-web`(Next 14) · **운영(공개)** `streamhub-user-web`(Next 14). 인프라 Docker(MySQL/Redis/MinIO/LocalStack), Colima.
> 서버 가동 중: API `:8080`(jar) · user-web `:3001`(next start). 관리자 `:3000`은 필요시 기동.

---

## 1. 구현 완료 기능 목록

### 백엔드 도메인 (18개 패키지, 전부 라이브·`/v3/api-docs` 200)
member, content, post, statistics, **dashboard**, goods, order, subscription/donation, point, **church**, **worship**, **album**, **store**, **payment**, **sms**, **chat**, actionlog, pub.

### 운영(공개) 사이트 — 완비
- 홈(히어로+영상/음악/소식 + **CCM 음반 캐러셀** + **내 주변 교회** 섹션)
- 영상/음악(미디어 재생), 소식(게시판), 통합검색(URL ?q= 동기화)
- 회원 로그인/마이페이지(프로필 + **구매 내역**), 회원 JWT 격리(type:member)
- **교회찾기**(`/churches`): Leaflet+OSM 지도(키0)·Haversine 거리순·교단/반경 필터·Geolocation(거부 시 서울시청 fallback)
- **교회 상세**(`/churches/[id]`): 예배시간·전화·시설·지도·길찾기 딥링크
- **예배/새가족 신청**(`/churches/[id]/register`): 다단 폼+가족 동적배열, 실제 regNo 발급
- **CCM 음반**(`/albums`,`/albums/[id]`): 장르필터·트랙·**전역 단일 30초 HTML5 미리듣기**+미니바
- **오프라인 매장**(`/stores`): 지도+거리순
- **음반 실구매**: 로그인 회원 → 실제 ORDER 생성+목업결제 PAID(관리자 목록에도 노출)
- **챗봇 위젯**(룰베이스 FAQ/주문조회, 백엔드우선+목업폴백), **결제 모달**(테스트모드)
- 챗봇/미리듣기/결제 전부 "데모/테스트" 배지

### 관리자 사이트 — 14개 화면 라이브
admin-ops(통합 운영 대시보드: KPI6+ApexCharts4+실시간피드+할일큐), dashboard(통계), catalog(기능 카탈로그), member(회원), content(콘텐츠 CRUD+업로드), goods(+add/[id], AG Grid 인라인+옵션/이미지), order(+[id], 상태머신 Stepper), subscription(+[id]), subscription-plan, donation, point, billing-calendar, action-log.

### 어댑터 seam (실키 주입 지점, 현재 전부 목업/시드)
PaymentProvider(toss/paypal/kakao/card) · SmsSender(aligo/solapi) · ChatProvider(llm) · MusicPreviewProvider(external) · GeocodeProvider(kakao) · PostcodeProvider · MapProvider(kakao). `.env` 플래그로 전환.

### 인프라/배포 자산
- **무료 배포 패키지**: `docker-compose.deploy.yml`(전체 스택+Caddy 자동HTTPS) + `DEPLOY-FREE.md`($0 런북: Oracle Free VM+DuckDNS+Vercel) — **api 이미지 빌드 성공(IMG=0) 검증**
- AWS 배포 자산(`deploy/terraform`: EC2+RDS+S3+ECR+SQS) — plan 검증, **apply 안 함($0)**
- CORS env화(`app.cors.allowed-origins`), 양쪽 프론트 vercel.json + .env.production.example

---

## 2. 미구현 기능 목록 ★ (100점 도달의 핵심 갭)

> ✅ **업데이트(2026-06-18)**: 아래 표의 교회/앨범/예배신청/SMS/매장 **관리자 화면이 모두 생성·검증 완료**됨(라우트 /churches·/albums·/stores·/worship·/sms + 상세/등록, 사이드바 메뉴, 카탈로그 카드). 결제 내역 화면·AWS 라이브만 미구현 유지.


| 항목 | 상태 | 백엔드 API | 관리자 화면 |
|---|---|---|---|
| **교회 관리**(CRUD) | 미구현 | ✅ `/v1/churches`(list/CRUD/denominations/upload) | ❌ 화면 없음 |
| **앨범/CCM 관리** | 미구현 | ✅ `/v1/album`(list/CRUD/upload) `/v1/store` | ❌ 화면 없음 |
| **예배신청 관리** | 미구현 | ✅ `/v1/worship`(list/{id}/status) | ❌ 화면 없음 |
| **SMS 발송/내역 관리** | 미구현 | ✅ `/v1/sms`(list/send) | ❌ 화면 없음 |
| **매장 관리** | 미구현 | ✅ `/v1/store`(list/{id}) | ❌ 화면 없음 |
| **결제 내역 화면** | 미구현 | ✅ `/v1/payment`(request/approve/receipt) | ❌ 화면 없음 |
| **AWS 라이브 스트리밍(C7)** | 미착수 | — | — (HLS 목업 권장) |

> ⚠️ 위 관리자 화면들은 이전에 사용자가 "제외(1,2,3)" 지시했으나, **본 작업지시서의 100점 기준에서는 -10/건 감점 대상**. → 100점 도달하려면 생성 필요.

### 카탈로그 미반영 (`streamhub-web/src/lib/features.catalog.ts`)
현재 카탈로그 카드 = goods/order/subscription/donation/point **만**. **누락**: church, album/store, worship, payment, sms, chat, content, member, dashboard, 콘텐츠 스트리밍. → 카탈로그가 확장을 반영 못 함(#3/#4 대상).

---

## 3. 부분 구현 기능 목록

- **챗봇**: 프론트 위젯 동작하나 백엔드 `/v1/chat/**`가 비공개(인증) → user-web은 **목업 폴백**으로 동작. (공개화 1줄이면 실백엔드 사용 — 사용자 "제외2" 지시로 보류)
- **결제**: 음반은 실주문 생성됨. 굿즈/구독 결제는 관리자 시드/플로우만(공개 구매는 음반만).
- **장바구니**: "장바구니" 버튼은 데모(담김 표시만), 다중상품 카트→주문은 미구현(단건 즉시구매만).
- **카탈로그**: 페이지·필터·배지 동작하나 카드 데이터가 확장 미반영(위 2 참조).
- **이미지 업로드**: 백엔드 StorageService(MinIO) 동작, 관리자 content/goods 업로드 화면 있음. church/album 업로드 엔드포인트는 있으나 화면 없음.

---

## 4. 발견된 버그 목록

### 수정 완료 ✅
- 굿즈 이미지 URL 이중 prepend(MinIO+picsum) → 절대URL 패스스루
- orval suffix-shift(재생성 시 기존 훅 깨짐) → operationName 안정화(2회 gen diff 0)
- NextAuth v5 prod `UntrustedHost`(next start hang) → `trustHost:true`
- today-KPI=0 → 시드 분포 `1-sqrt` 우상향 보정
- 대시보드 할일큐 딥링크 오타 → /order·/goods
- method-not-supported 500 → 405
- 워십 reg_no 동시성 500 → 재시도, SMS 채널 EUC-KR 일원화, 결제 txnId 정합, null-safety
- SMS sender에 마스킹번호 전달(실키 발송불가) → 원본전달·DB만 마스킹
- 워십 알림 전화 평문로깅 → 마스킹
- 워십 등록이 숨김교회(use_yn=N) 수락 → 차단
- **음반 구매 가짜팝업 → 실제 주문 생성**(POST /pub/v1/orders)
- 매장 거리표기 <1km 버그(0.8km) → 850m

### 미해결(저우선·데모 무영향)
- 시드 SMS 채널 UTF-8 vs 일부 불일치(정정됨, 잔여 미미)
- 시드 타임라인 now() 롤링(형태는 결정론, 절대날짜 변동 — javadoc 정직화됨)
- payment approve가 발급 txnId 미대조 → **수정됨**(pay_txn_id 저장+검증)

---

## 5. 관리자 기능 현황 (streamhub-web :3000)
- **동작 확인**(Playwright): 로그인·admin-ops(차트4+KPI+사이드바9메뉴)·회귀0. 빌드 그린(신규 orval 클라 포함).
- **권한**: SYSTEM/CHURCH_MANAGER(@PreAuthorize), AdminPrincipal 스코핑. 정상.
- **CRUD**: member/content/goods/order/subscription/donation/point 완비. **church/album/worship/sms/store CRUD 화면 없음**(API만).
- **이미지 관리**: content/goods 업로드 화면 O. church/album 업로드 화면 X.
- 사이드바 그룹: 운영/회원·콘텐츠/커머스/후원·구독.

## 6. 운영 사이트 기능 현황 (streamhub-user-web :3001)
- **메뉴**: TabBar(홈/영상/음악/소식/MY) + AppBar(교회찾기·음반·검색) — 정상.
- **API**: 공개 `/pub/v1/**`(home/contents/posts/churches/albums/stores/worship/orders/auth) 전부 200.
- **이미지**: 콘텐츠 썸네일=교회/예배 Unsplash, 굿즈=picsum, 앨범커버·교회=데이터. 정상 렌더(검증).
- **권한**: 비로그인 공개열람, 구매/마이페이지/구매내역=회원토큰 필요(미로그인 401·리다이렉트). 정상.
- **에러**: Playwright 스윕 콘솔에러 0.

## 7. Stream Hub 기능 현황 (백엔드 도메인 매트릭스)
| 도메인 | API | 관리자화면 | 운영화면 | 권한 | 메뉴연결 |
|---|---|---|---|---|---|
| member | ✅ | ✅ | ✅(로그인/마이) | ✅ | ✅ |
| content | ✅ | ✅ | ✅(영상/음악) | ✅ | ✅ |
| post | ✅ | (콘텐츠계열) | ✅(소식) | ✅ | ✅ |
| dashboard/statistics | ✅ | ✅(admin-ops) | — | ✅ | ✅ |
| goods | ✅ | ✅ | (음반으로 노출) | ✅ | ✅ |
| order | ✅ | ✅ | ✅(구매/내역) | ✅ | ✅ |
| subscription/donation | ✅ | ✅ | (마이페이지 일부) | ✅ | ✅ |
| point | ✅ | ✅ | — | ✅ | ✅ |
| **church** | ✅ | ❌ | ✅(교회찾기) | ✅ | 부분 |
| **worship** | ✅ | ❌ | ✅(신청) | ✅ | 부분 |
| **album/store** | ✅ | ❌ | ✅(음반/매장) | ✅ | 부분 |
| **payment** | ✅ | ❌ | ✅(결제모달) | ✅ | 부분 |
| **sms** | ✅ | ❌ | — | ✅ | ❌ |
| **chat** | ✅(인증) | ❌ | ✅(위젯,목업폴백) | ✅ | ✅ |
| catalog | (프론트) | ✅ | — | ✅ | ✅ |

---

## 8. DB 스키마 특이사항
- **ddl-auto=update**(dev/docker 프로파일) → 부팅 시 스키마 자동생성. prod=validate(JPA_DDL_AUTO로 1회 update).
- **테이블명 대문자 SNAKE**(MEMBER, ORDERS 등) — Colima 리눅스 대소문자 구분 때문 `PhysicalNamingStrategyStandardImpl` + @Column snake. MyBatis `map-underscore-to-camel-case`.
- `ORDER`는 예약어 → 테이블 `ORDERS`.
- **앨범↔GOODS_ITEM 브리지**: ON_SALE 앨범 1개당 GOODS_ITEM 1행(category=음반), 구매는 주문도메인 흡수. 앨범 삭제=GOODS_ITEM use_yn=N 파킹(과거 주문 스냅샷 보존).
- **자가시드**: DataInitializer(@Order1: 관리자2·교회40+예배시간134·회원60·콘텐츠24·게시글10·시청800) + PortfolioSeeder(@Order2: 굿즈64/옵션80/이미지127·플랜4·구독24·후원1400·포인트원장1306·주문1700·앨범24/트랙180·매장8·SMS120). 멱등(count>0 skip), 결정론적(now() 롤링).
- 신규 컬럼: Member(grade,point_balance) · Order(pay_provider,pay_status,pay_txn_id) · OrderReceipt(provider,txn_id) · Church(denomination,lat,lng,phone,pastor,facilities…).
- 재시드 방법: `docker exec streamhub-mysql mysql -uroot -proot -e "DROP DATABASE streamhub; CREATE DATABASE streamhub..."` 후 재기동, 또는 특정 테이블 TRUNCATE+회원 리셋.

## 9. 배포 특이사항
- **무료($0) 권장**: 단일 무료 VM(Oracle Always Free)에서 `docker compose -f docker-compose.yml -f docker-compose.deploy.yml up -d --build`(self-seed라 관리형DB 불필요, 상시가동) + Caddy 자동HTTPS(DuckDNS 도메인) + 프론트2개 Vercel(무료). 런북=`DEPLOY-FREE.md`.
- **혼합콘텐츠**: Vercel(HTTPS)→백엔드 HTTPS 필수(Caddy 해결).
- **CORS**: 배포 시 `APP_CORS_ALLOWED_ORIGINS`에 Vercel 도메인.
- **NextAuth**: trustHost 적용됨, prod에 NEXTAUTH_SECRET 필요.
- **프론트 env**: admin `NEXT_PUBLIC_API_BASE_URL`, user-web `NEXT_PUBLIC_API_BASE`.
- AWS apply 시 비용 ~$34/월($200 크레딧 차감) — 사용자 보류.
- **검증**: api Docker 이미지 빌드 성공, deploy compose config 유효.

---

## 10. 품질 점수 (100점 기준, 작업지시서 §5 룰)
### 업데이트 (2026-06-18, P0 완료 후)
| 감점 | 항목 | 상태 | 점수 |
|---|---|---|---|
| 관리자페이지 누락 | church/album/worship/sms/store 관리화면 | ✅ **생성·검증 완료**(AG Grid 목록+CRUD+상태, 사이드바 5메뉴, build 그린, Playwright 데이터 렌더) | 0 |
| 기능 누락 | 카탈로그 확장 반영 | ✅ **신규 도메인 카드 추가** | 0 |
| 이미지 누락 | 카탈로그 대표 스크린샷 이미지 미생성 | 🟡 카드 메타는 있으나 실제 스크린샷 파일 미캡처(스크린샷 도구 환경 타임아웃) | -5 |
| 권한/런타임/배포/API | 없음 | ✅ | 0 |
| **현재 점수** | | | **≈ 95/100** |

> 70 → **95**. 남은 -5는 카탈로그 카드의 실제 대표 이미지(스크린샷). 스크린샷 도구가 이 환경에서 타임아웃이라 보류(기능엔 영향 없음, placeholder/그라데이션으로 렌더).

### (이전) P0 착수 전 ≈ 70/100 — 관리자 화면 5종(-50)+카탈로그(-10)+이미지(-10)가 주 감점이었음.

---

## 11. 향후 작업 TODO (100점 경로, 우선순위)
1. **[P0] 관리자 관리화면 5종 생성** (각 -10): 교회·앨범(+매장)·예배신청·SMS·(결제내역). orval 클라이언트 이미 생성됨(church/album/worship/sms/store/payment) → 기존 goods/order 화면 패턴 재사용. 각 list+검색+CRUD/상태변경 + 사이드바 메뉴.  → +50
2. **[P1] 카탈로그 확장 반영**: features.catalog.ts에 신규 도메인 카드(church/album/worship/payment/sms/chat/store/content/dashboard) 추가 + 상태배지(live/mock) + 대표 스크린샷/이미지.  → +10
3. **[P1] 카탈로그 이미지(관리자/운영 분리)**: 관리자 카탈로그 대표이미지 + 운영 노출용 이미지 분리 관리, 누락분 자동 추가.  → +5~10
4. **[P2] 챗봇 백엔드 공개화**(원하면): `/v1/chat/**` permitAll → user-web이 실 룰베이스 사용.
5. **[P2] 장바구니/다중구매·배송지 입력**(선택 고도화).
6. **[P3] 최종 배포 검증**: build/test/migration/운영/관리자/StreamHub/이미지/권한 재점검 후 보고서.

### 다음 세션 즉시 착수 가이드
- 시작점: 이 문서 + 메모리 `streamhub-admin-project.md`.
- **P0 관리자 화면**부터: streamhub-web에서 `goods/page.tsx`(목록+AG Grid+검색) / `order/[id]/page.tsx`(상세+상태변경) 패턴 복제 → church/album/worship/sms/store 각각. orval 훅은 `src/apis/query/{church,album,worship,sms,store}/` 에 이미 있음(안정명). 사이드바 `components/layout/Sidebar.tsx`에 메뉴 추가.
- 검증 패턴: `./mvnw clean package`(58테스트) + `npm run build`(admin/user-web) + Playwright E2E + 커밋/푸시.
- 서버 기동: API `cd streamhub-api && java -jar target/*.jar`(또는 mvnw spring-boot:run), 프론트 `nvm use 20 && npm run dev/start`.
