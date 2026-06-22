# MSA 분리 — 감사로그 서비스 추출 (이벤트 기반)

> 단일 Spring Boot(`streamhub-api`)에서 **감사로그 소비**를 별도 마이크로서비스
> (`streamhub-audit-service`)로 떼어낸 사례. #1에서 만든 **Kafka 이벤트 버스를 서비스 간 통신
> 백본으로 재활용**한다. "전부 쪼개기"가 아니라, 경계가 깨끗한 한 조각을 안전하게(strangler-fig)
> 떼는 **권장 방식**의 시연.

---

## 1. 왜 / 무엇을 / 왜 그것부터

**MSA를 왜?** 도메인별 독립 배포·확장·장애격리·기술선택. 단, 분산 시스템 비용(네트워크·데이터 일관성·운영)이 따라오므로 **경계가 분명하고 결합이 약한 것부터** 떼는 게 정석.

**StreamHub 도메인 후보**: 회원/인증 · 커머스(주문·굿즈·쿠폰·결제) · 콘텐츠/스트리밍 · 후원/구독 · 소통(게시판·문의·챗봇) · **감사로그/관측** · 알림.

**감사로그를 1순위로 고른 이유**
- 이미 **비동기 이벤트**로 분리돼 있다(발행 → 메시지브로커 → 소비). 동기 호출 결합이 없음.
- 쓰기 경로가 단순하고(이벤트 → 영속화) 다른 도메인 로직과 안 얽힘.
- 장애가 본 비즈니스(주문 등)를 안 깨는 best-effort라 분리 리스크가 낮음.
- #1의 Kafka가 그대로 **서비스 간 통신 채널**이 된다.

## 2. 분리 구조 (Before → After)

```
[Before] 모놀리스 한 덩어리
streamhub-api : 발행(ActionLogPublisher) → Kafka → 소비(KafkaActionLogConsumer) → ACTION_LOG

[After] 두 서비스, Kafka(쓰기)·REST(읽기), DB-per-service
streamhub-api(프로듀서 전용)  ──produce──▶  Kafka topic  ──consume──▶  streamhub-audit-service ──▶ [streamhub_audit DB]
   EVENTLOG_OUTBOX=true (원자 발행)                                          (자체 스키마 소유)        ▲
   ACTIONLOG_SOURCE=remote ───────GET /v1/action-logs (REST read)────────────────────────────────────┘
```

- **쓰기(비동기):** 모놀리스 `EVENTLOG_TRANSPORT=kafka`로 **발행**, `app.eventlog.consume=false`로 **자기 컨슈머를 끔**(프로듀서 전용). audit-service가 `streamhub-action-log` 토픽을 단독 소비 → 영속. (`EVENTLOG_OUTBOX=true`면 발행이 원자적·무유실 — §4-5)
- **읽기(동기 REST):** audit-service가 **자체 스키마 `streamhub_audit`** 를 소유(DB-per-service). 모놀리스 '감사 로그' 화면은 테이블을 직조회하지 않고 `ACTIONLOG_SOURCE=remote`로 audit-service의 **`GET /v1/action-logs`** read API를 호출(`RemoteActionLogReader`). 공유 DB 안티패턴 제거.
- 쓰기는 **비동기 이벤트**(브로커만 안다, 느슨한 결합), 읽기는 **동기 REST**(모놀리스가 audit-service URL을 안다 = 호출 결합). MSA에서 read는 동기 조회가 흔한 현실적 절충.

## 3. 실행 (로컬 데모)

```bash
cd ~/Documents/MyToys/streamhub-admin
docker compose up -d                 # mysql, kafka 등 (mysql-init이 streamhub_audit 스키마 생성)
# 기존 볼륨이라 init이 안 돈 경우 1회 수동 생성:
#   docker exec -i streamhub-mysql mysql -uroot -proot < deploy/mysql-init/01-create-service-dbs.sql

# 1) 모놀리스 = 프로듀서 전용(소비 끔) + 읽기는 audit-service로 + 원자 발행(outbox)
cd streamhub-api
EVENTLOG_TRANSPORT=kafka EVENTLOG_CONSUME=false EVENTLOG_OUTBOX=true \
  ACTIONLOG_SOURCE=remote AUDIT_SERVICE_URL=http://localhost:8090 \
  KAFKA_BOOTSTRAP_SERVERS=localhost:9092 ./mvnw spring-boot:run

# 2) (새 터미널) 추출된 감사 서비스 = 소비자 + read API (자체 DB streamhub_audit)
cd streamhub-audit-service
DB_NAME=streamhub_audit ./mvnw spring-boot:run    # :8090, Kafka 소비 + GET /v1/action-logs

# 3) 관리자 콘솔에서 액션 하나 → audit-service 로그 "audit recorded: ..." →
#    '감사 로그' 화면(모놀리스가 audit-service read API 호출)에 행이 보이면 end-to-end 성공.
#    read API 직접 확인: curl 'http://localhost:8090/v1/action-logs?pageSize=5'
```

> `EVENTLOG_CONSUME` → `app.eventlog.consume`(기본 true, MSA에선 false). `ACTIONLOG_SOURCE`
> → `app.actionlog.source`(기본 local=직조회, MSA에선 remote=read API). `EVENTLOG_OUTBOX`
> → `app.eventlog.outbox.enabled`(기본 false, MSA 운영 경로에선 true).

## 4. 트레이드오프 · 배운 점 (면접 핵심)

이 분리가 드러내는 **분산 시스템 이슈들**과 본 데모의 선택:

1. **데이터 오너십 / 공유 DB → DB-per-service로 해소(구현됨).** 초기 분리는 공유 `ACTION_LOG`에 썼다(과도기 안티패턴). 이제 audit-service가 **자체 스키마 `streamhub_audit`** 를 소유하고 쓰기(컨슈머)·읽기(`GET /v1/action-logs`)를 모두 거기서 처리한다. 모놀리스는 `ACTIONLOG_SOURCE=remote`일 때 테이블 직조회 대신 그 read API를 호출(`RemoteActionLogReader`, RestClient). 읽기는 동기 REST 결합이 생기지만(모놀리스가 audit URL을 앎), 데이터 오너십 경계는 명확해진다.
2. **이벤트 자급성(self-containment).** 소비자는 admin 도메인 DB에 접근할 수 없다 → `adminName`을 컨슈머가 보강하던 로직이 깨진다. 올바른 해법: **프로듀서가 발행 시점에 `adminName`을 이벤트에 실어** 보낸다(이벤트가 필요한 데이터를 모두 담는다). 현재는 메시지에 있는 값만 저장(없으면 null)하고 이 점을 명시.
3. **크로스서비스 직렬화 계약.** Spring Kafka JsonSerializer는 `__TypeId__` 헤더에 **프로듀서 클래스 FQCN**을 박는다. 소비자(다른 패키지)에선 그 클래스가 없다 → audit-service는 `spring.json.use.type.headers=false` + `value.default.type`으로 **헤더 무시, 자기 타입으로 역직렬화**. (서비스 간 스키마 계약의 실제 예)
4. **at-least-once / 멱등.** 소비 후 오프셋 커밋이라 재전달 시 중복 가능 → 감사로그는 수용. 정확히-한-번이 필요하면 이벤트 UUID + unique 제약.
5. **분산 트랜잭션 부재 → Transactional Outbox로 해소(구현됨).** 기본 경로는 비즈니스 트랜잭션과 발행이 원자적이지 않다(발행 실패 = 이벤트 유실 가능, best-effort). `EVENTLOG_OUTBOX=true`로 켜면 **Transactional Outbox 패턴**이 활성화된다: `ActionLogPublisher` → `OutboxActionLogEmitter`가 이벤트를 **비즈 트랜잭션과 같은 DB 트랜잭션**으로 `ACTION_OUTBOX`에 기록(커밋되면 durable 큐잉, 롤백되면 이벤트도 사라짐) → `ActionOutboxRelay`(스케줄러)가 미발행 행을 **확정 발행**(broker ack 대기)으로 Kafka에 보내고 published 플래그를 일괄 갱신. 발행 실패 행은 다음 틱에 재시도(at-least-once, 감사 소비자가 중복 수용). 트레이드오프: outbox insert가 실패하면 비즈 트랜잭션도 롤백(원자성 ↔ best-effort의 의도적 교환). 코드: `v1/actionlog/outbox/`.

## 5. 향후 (전체 MSA로 가는 로드맵)
- ✅ **Transactional Outbox** — 발행 원자성/무유실 (`EVENTLOG_OUTBOX=true`, `v1/actionlog/outbox/`). §4-5 참고
- ✅ **DB-per-service + audit read API** — audit-service가 `streamhub_audit` 소유, 모놀리스는 `GET /v1/action-logs` 호출(`ACTIONLOG_SOURCE=remote`). 공유 DB 제거
- **API Gateway**(Spring Cloud Gateway) — 단일 진입점·인증·라우팅
- **Service Discovery**(Eureka/Consul) 또는 K8s Service DNS
- **Config Server** / 중앙 설정
- **Saga** — 다중 서비스에 걸친 분산 트랜잭션 보상
- 서비스별 **독립 파이프라인·관측성**([[observability]])·K8s 배포([[kubernetes]])
- 다음 추출 후보: 알림 서비스(이미 채널별 발송 로그로 분리 가능)

## 6. 관련 문서
- 이벤트 버스: [[eventlog-kafka]] (이 분리의 통신 기반)
- 배포: [[kubernetes]] / 관측성: [[observability]]
