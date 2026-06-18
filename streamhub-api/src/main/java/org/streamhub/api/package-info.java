/**
 * StreamHub API — 외부 연동 seam(실키 활성화) & 배치(스케줄러) 운영 가이드 (단일 기준 주석).
 *
 * <p><b>seam 패턴.</b> 모든 외부 연동은 어댑터 인터페이스 + 기본 구현(mock/seed/공개 데모키) +
 * 실 구현({@code @ConditionalOnProperty}로 키가 있을 때만 등록)으로 격리돼 있다. 실키는 코드에
 * 두지 않고 <b>환경변수로만 주입</b>(.env / 시스템 env)하며, 기본값이 데모용이라 키 없이도 전 기능이
 * 동작한다. 실 가맹점/서비스 키를 주입하면 코드 변경 없이 실연동으로 전환된다.
 *
 * <h2>외부 연동 seam · 실키 활성화</h2>
 * <ul>
 *   <li><b>결제 (C4)</b> — {@code app.payment.provider}(기본 mock).
 *       <ul>
 *         <li>toss = <b>실 샌드박스 연동 완료</b>. 기본값이 토스 공개 문서용 테스트키이므로 즉시 동작.
 *             override: {@code PAYMENT_TOSS_CLIENT_KEY} / {@code PAYMENT_TOSS_SECRET_KEY}.</li>
 *         <li>kakao = 실 호출 코드(ready/approve). 활성화: {@code PAYMENT_KAKAO_SECRET_KEY}
 *             (+ {@code PAYMENT_KAKAO_CID}, 기본 TC0ONETIME). 공개 테스트키 없음 → 본인 키 필요.</li>
 *         <li>paypal = 실 호출 코드(OAuth→create/capture). 활성화: {@code PAYMENT_PAYPAL_CLIENT_ID}
 *             / {@code PAYMENT_PAYPAL_SECRET} (+ {@code PAYMENT_PAYPAL_CURRENCY}). 본인 sandbox 키 필요.</li>
 *         <li>{@code PAYMENT_TEST_MODE}(기본 true)는 mock 승인 메모 표기에만 영향.</li>
 *       </ul></li>
 *   <li><b>배송조회 (C8)</b> — {@code app.delivery.provider}(기본 sweettracker, <b>실연동</b>).
 *       기본값이 스마트택배 공개 데모키라 택배사 목록/배송조회 즉시 동작. override:
 *       {@code DELIVERY_SWEETTRACKER_API_KEY}. mock = 오프라인 가짜 타임라인.</li>
 *   <li><b>SMS/LMS (C6)</b> — {@code app.sms.sender}(기본 mock=로그만). 활성화: aligo
 *       ({@code SMS_ALIGO_API_KEY}/{@code SMS_ALIGO_USER_ID}) 또는 solapi
 *       ({@code SMS_SOLAPI_API_KEY}/{@code SMS_SOLAPI_API_SECRET}).</li>
 *   <li><b>챗봇 (C6)</b> — {@code app.chat.provider}(기본 rule=키워드 FAQ). 활성화: llm
 *       ({@code CHAT_LLM_API_KEY}).</li>
 *   <li><b>교회 지오코딩 (C1)</b> — {@code app.church.geocode.provider}(기본 seed=결정론 좌표).
 *       활성화: kakao ({@code CHURCH_GEOCODE_KAKAO_REST_KEY}).</li>
 *   <li><b>음악 미리듣기 (C3)</b> — {@code app.music.provider}(기본 seed=SoundHelix 샘플). 활성화:
 *       external ({@code MUSIC_EXTERNAL_API_KEY}/{@code MUSIC_EXTERNAL_BASE_URL}).</li>
 *   <li><b>예배신청 부가 (C2)</b> — 우편번호 {@code app.worship.postcode.provider}(기본 mock),
 *       알림 {@code app.worship.sms.provider}(기본 noop). 실연동은 위 SMS seam과 동일 패턴.</li>
 * </ul>
 *
 * <h2>배치 / 스케줄러 ({@code @EnableScheduling} on StreamhubApiApplication)</h2>
 * <ul>
 *   <li><b>정기결제 청구</b> {@code donation.BillingScheduler} — ACTIVE 구독의 도래분을 1회차씩
 *       청구(구독별 독립 트랜잭션). cron {@code app.billing.cron}(기본 5분). 수동 트리거
 *       {@code POST /v1/donation/run-billing}.</li>
 *   <li><b>포인트 만료</b> {@code member.PointExpiryScheduler} — 만료 도래 적립분 회수 + 만료원장
 *       기록. 매일 04:00(고정 cron).</li>
 *   <li><b>배송상태 동기화</b> {@code delivery.DeliverySyncScheduler} — SHIPPING 주문을 택배사
 *       조회해 배달완료면 자동 {@code DONE} 전이(이동중이면 {@code SHIPPING}). cron
 *       {@code app.delivery.sync-cron}(기본 30분). 온디맨드 트리거 {@code PATCH
 *       /v1/order/{id}/delivery-sync}. 전이정책은 순수함수
 *       {@code OrderService.deliveryDrivenTransition}(상태머신 준수·idempotent).</li>
 * </ul>
 *
 * <p>실제 env 자리(기본값 포함)는 {@code application.yml}의 {@code app:} 블록에, 각 어댑터의
 * 실키 주입 지점은 해당 {@code *Provider}/{@code *Sender} 클래스 주석에 있다.
 */
package org.streamhub.api;
