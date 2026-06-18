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

### 관리자 사이트 — 15개 화면 라이브
admin-ops(통합 운영 대시보드: KPI6+ApexCharts4+실시간피드+할일큐), dashboard(통계), catalog(기능 카탈로그), member(회원), content(콘텐츠 CRUD+업로드), goods(+add/[id], AG Grid 인라인+옵션/이미지), order(+[id], 상태머신 Stepper), **payment(결제내역: 결제/환불 영수증 조인 목록, 기간/수단/구분 필터, 주문딥링크)**, subscription(+[id]), subscription-plan, donation, point, billing-calendar, action-log. + 확장 5종(churches/albums/stores/worship/sms).

### 어댑터 seam (실키 주입 지점)
PaymentProvider(**toss=실 샌드박스 연동 완료**/kakao·paypal=실 호출 코드, 키 주입형) · **DeliveryTrackingProvider(sweettracker=실 연동 완료/mock)** · SmsSender(aligo/solapi) · ChatProvider(llm) · MusicPreviewProvider(external) · GeocodeProvider(kakao) · PostcodeProvider · MapProvider(kakao). `.env` 플래그로 전환.
> **배송조회 실연동(2026-06-18, C8):** 운송장 수동입력만 있던 것을 **스마트택배(SweetTracker) 집계 API** 연동으로 확장. `GET /v1/order/carriers`(택배사 132종)·`GET /v1/order/{id}/tracking-info`(관리자 조회)·`PATCH /v1/order/{id}/delivery-sync`(조회+상태 자동전이)·`GET /pub/v1/orders/{orderNo}/tracking`(회원). 주문의 택배사코드+송장번호로 실 배송조회. 관리자 주문상세=택배사 드롭다운+배송 타임라인, user-web 마이페이지=주문별 배송조회. **공개 데모키로 실 API 검증 완료**(carriers 132, tracking-info 실호출+카탈로그 resolve+상태/타임라인). 가짜 송장은 택배사 checksum 검증으로 거부됨(정직). `DELIVERY_SWEETTRACKER_API_KEY`로 override.
> **배송상태 → 주문상태 자동연동(C8.1):** 택배사 배송상태로 주문 상태머신 자동전이 — 배달완료 → `SHIPPING→DONE`, 이동중 → `READY→SHIPPING`. **온디맨드**(관리자 '배송 조회'=delivery-sync, 완료면 자동 DONE+상세 리프레시) + **스케줄 폴링**(DeliverySyncScheduler @Scheduled, 배송중 주문 주기조회, `app.delivery.sync-cron` 기본 30분, 주문별 격리 try/catch). 전이정책은 순수함수 `OrderService.deliveryDrivenTransition`(상태머신 준수, idempotent)로 분리해 단위테스트 6종. **검증:** 단위테스트(SHIPPING+완료→DONE 등 6케이스) + **라이브**(mock provider=배달완료로 order952 SHIPPING→DONE 실전이 확인, 실 provider=completed false라 미전이 정상). 실 키+실 배달완료 송장이면 그대로 자동 DONE.
> **토스 실연동(2026-06-18):** 음반 결제의 토스 옵션은 **실제 Toss v2 결제창 + 실 confirm API** 호출(`api.tosspayments.com/v1/payments/confirm`). 기본키는 토스 공개 문서용 테스트키(`PAYMENT_TOSS_CLIENT_KEY`/`PAYMENT_TOSS_SECRET_KEY`로 override, 실 가맹점키 주입 시 코드변경 없이 라이브). 테스트키라 실제 출금 없음. 카카오/페이팔은 동일 패턴으로 후속 예정.

### 인프라/배포 자산
- **무료 배포 패키지**: `docker-compose.deploy.yml`(전체 스택+Caddy 자동HTTPS) + `DEPLOY-FREE.md`($0 런북: Oracle Free VM+DuckDNS+Vercel) — **api 이미지 빌드 성공(IMG=0) 검증**
- AWS 배포 자산(`deploy/terraform`: EC2+RDS+S3+ECR+SQS) — plan 검증, **apply 안 함($0)**
- CORS env화(`app.cors.allowed-origins`), 양쪽 프론트 vercel.json + .env.production.example

---

## 2. 미구현 기능 목록 ★ (100점 도달의 핵심 갭)

> ✅ **업데이트(2026-06-18, 2차)**: 교회/앨범/예배신청/SMS/매장 관리자 화면에 더해 **결제 내역 화면까지 생성·검증 완료**. 카탈로그 대표 스크린샷 7종 실캡처 완료. AWS 라이브(C7)만 선택사항으로 남음.


| 항목 | 상태 | 백엔드 API | 관리자 화면 |
|---|---|---|---|
| **교회 관리**(CRUD) | ✅ 완료 | ✅ `/v1/churches`(list/CRUD/denominations/upload) | ✅ /churches(+[id]/add) |
| **앨범/CCM 관리** | ✅ 완료 | ✅ `/v1/album`(list/CRUD/upload) `/v1/store` | ✅ /albums(+[id]/add) |
| **예배신청 관리** | ✅ 완료 | ✅ `/v1/worship`(list/{id}/status) | ✅ /worship(+[id]) |
| **SMS 발송/내역 관리** | ✅ 완료 | ✅ `/v1/sms`(list/send) | ✅ /sms |
| **매장 관리** | ✅ 완료 | ✅ `/v1/store`(list/{id}) | ✅ /stores |
| **결제 내역 화면** | ✅ **완료** | ✅ `/v1/payment`(**list**/request/approve/receipt) | ✅ **/payment** (영수증 조인 목록) |
| **AWS 라이브 스트리밍(C7)** | 선택(미착수) | — | — (HLS 목업 권장) |

### 카탈로그 미반영 (`streamhub-web/src/lib/features.catalog.ts`)
현재 카탈로그 카드 = goods/order/subscription/donation/point **만**. **누락**: church, album/store, worship, payment, sms, chat, content, member, dashboard, 콘텐츠 스트리밍. → 카탈로그가 확장을 반영 못 함(#3/#4 대상).

---

## 3. 부분 구현 기능 목록

- **챗봇**: 프론트 위젯 동작하나 백엔드 `/v1/chat/**`가 비공개(인증) → user-web은 **목업 폴백**으로 동작. (공개화 1줄이면 실백엔드 사용 — 사용자 "제외2" 지시로 보류)
- **결제**: 음반은 실주문 생성됨. **토스=실 PG 테스트 연동(prepare→토스 결제창→confirm, 실 confirm API 호출)**. 카카오/페이팔/카드 버튼은 목업 1단계 주문(즉시 PAID, 실 PG 미연동). 굿즈/구독 결제는 관리자 시드/플로우만(공개 구매는 음반만).
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
- **동작 확인**(Playwright): 로그인·admin-ops(차트4+KPI+사이드바메뉴)·결제내역(1,793건 영수증)·회귀0(/order 1,702건 정상). 빌드 그린(신규 orval 클라 포함).
- **권한**: SYSTEM/CHURCH_MANAGER(@PreAuthorize), AdminPrincipal 스코핑. 정상.
- **CRUD**: member/content/goods/order/subscription/donation/point + church/album/worship/sms/store + **payment(결제내역)** 완비.
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
| **church** | ✅ | ✅ | ✅(교회찾기) | ✅ | ✅ |
| **worship** | ✅ | ✅ | ✅(신청) | ✅ | ✅ |
| **album/store** | ✅ | ✅ | ✅(음반/매장) | ✅ | ✅ |
| **payment** | ✅ | ✅(결제내역) | ✅(결제모달) | ✅ | ✅ |
| **sms** | ✅ | ✅ | — | ✅ | ✅ |
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
### 업데이트 (2026-06-18, 3차 — 100점 도달)
| 감점 | 항목 | 상태 | 점수 |
|---|---|---|---|
| 관리자페이지 누락 | church/album/worship/sms/store + **payment(결제내역)** 관리화면 | ✅ **전부 생성·검증 완료**(AG Grid 목록+필터+상태/딥링크, 사이드바 메뉴, build 그린, Playwright 데이터 렌더) | 0 |
| 기능 누락 | 카탈로그 확장 반영 + 결제내역 카드 live화 | ✅ | 0 |
| 이미지 누락 | 카탈로그 대표 스크린샷 | ✅ **7종 실캡처 완료**(action-log/churches/albums/stores/worship/sms/payment, /catalog 31썸네일 broken 0) | 0 |
| 권한/런타임/배포/API | 없음 | ✅ | 0 |
| **현재 점수** | | | **100/100** |

> 95 → **100**. 결제내역 화면(백엔드 `/v1/payment/list` 신규 + AG Grid 화면)과 카탈로그 스크린샷 7종 실캡처로 잔여 감점 해소. 스크린샷은 Playwright MCP `browser_take_screenshot`로 캡처(이전 타임아웃 이슈 없이 동작), `streamhub-web/public/catalog/*.png`에 저장, /catalog에서 31장 전부 로드 검증(broken 0). 백엔드 59테스트 0실패, admin build 그린.
> AWS 라이브(C7/HLS 목업)는 작업지시서에서 (선택)이며 품질점수 룰의 감점 항목이 아님 — 미착수 유지가 점수에 영향 없음.

### (이전) P0 착수 전 ≈ 70/100 — 관리자 화면 5종(-50)+카탈로그(-10)+이미지(-10)가 주 감점이었음.

---

## 11. 향후 작업 TODO (100점 도달 후)
- [x] **[P0] 관리자 관리화면**: 교회·앨범·매장·예배신청·SMS **+ 결제내역** 전부 완료(사이드바 메뉴 포함).
- [x] **[P1] 카탈로그 확장 반영**: 신규 도메인 카드 + live/mock 배지 + 결제내역 카드 live화.
- [x] **[P1] 카탈로그 대표 스크린샷**: 7종 실캡처 + /catalog 31썸네일 로드 검증(broken 0).
- [x] **실 PG 연동 — 토스**: prepare/confirm 2단계 + Toss v2 결제창 + 실 confirm API. 검증 완료(결제창 렌더·confirm 실 API 호출·금액위변조 차단).
- [x] **실 PG 연동 — 카카오페이·페이팔 어댑터 코드**: 실 호출 코드 구현 완료(카카오 ready/approve `open-api.kakaopay.com`, 페이팔 OAuth+create/capture `api-m.sandbox.paypal.com`). `@ConditionalOnProperty`로 키 있을 때만 등록(없으면 mock 폴백, 데모 무영향). seam을 2토큰 approve(requestTxnId=tid/orderId + clientToken=pg_token/token)+redirectUrl로 확장. **공개 테스트키 없어 실 API 호출은 미검증**(키 주입 시 동작). 프론트는 redirectUrl 처리 추가, REAL_PG_PROVIDERS에 추가하면 활성화.
- [ ] **[선택] AWS 라이브(C7)**: HLS 목업 권장(stream 도메인 + 관리자 송출 콘솔 + user-web hls.js 플레이어). 품질점수 무관, 포트폴리오 도메인 매트릭스 완성용.
- [ ] **[P2] 챗봇 백엔드 공개화**: `/v1/chat/**` permitAll → user-web 실 룰베이스.
- [ ] **[P2] 장바구니/다중구매·배송지 입력**(선택 고도화).

### 다음 세션 즉시 착수 가이드
- 시작점: 이 문서 + 메모리 `streamhub-admin-project.md`.
- **P0 관리자 화면**부터: streamhub-web에서 `goods/page.tsx`(목록+AG Grid+검색) / `order/[id]/page.tsx`(상세+상태변경) 패턴 복제 → church/album/worship/sms/store 각각. orval 훅은 `src/apis/query/{church,album,worship,sms,store}/` 에 이미 있음(안정명). 사이드바 `components/layout/Sidebar.tsx`에 메뉴 추가.
- 검증 패턴: `./mvnw clean package`(58테스트) + `npm run build`(admin/user-web) + Playwright E2E + 커밋/푸시.
- 서버 기동: API `cd streamhub-api && java -jar target/*.jar`(또는 mvnw spring-boot:run), 프론트 `nvm use 20 && npm run dev/start`.
