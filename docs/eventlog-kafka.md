# 감사로그 비동기 파이프라인 — SQS ↔ Kafka 전환 (event transport seam)

> 관리자 액션(로그인·승인·삭제 등)을 **비동기로 발행 → 소비 → 영속화**하는 감사로그 파이프라인을,
> 코드 변경 없이 **SQS ↔ Apache Kafka**로 갈아끼울 수 있게 만든 seam 문서.
> 전환 플래그: `app.eventlog.transport = sqs | kafka` (env `EVENTLOG_TRANSPORT`).

---

## 1. 구조 (Architecture)

```
[비즈니스 서비스]                         [발행]              [브로커]            [소비]            [영속화]
OrderService.changeStatus()  ─┐
ContentService.delete()       ─┼─▶ ActionLogPublisher ─▶ ActionLogTransport ─▶  SQS  or  Kafka ─▶ (Sqs|Kafka)Consumer ─▶ ActionLogWriter ─▶ ACTION_LOG (MySQL)
CouponService.release()       ─┘        (메시지 조립           (인터페이스/seam)                        (@SqsListener /          (공유 영속화)
                                         + 호출자 IP)                                                    @KafkaListener)
```

핵심은 **호출부(수십 개 서비스)는 그대로**라는 점이다. `actionLogPublisher.publish(...)` 호출은 한 줄도 안 바뀐다. 바뀌는 건 `ActionLogTransport` 구현 빈 하나뿐.

**구성 요소**
- `ActionLogPublisher` — 메시지(`ActionLogMessage`) 조립 + 요청 스레드에서 클라이언트 IP 캡처 → `ActionLogTransport.send()`에 위임. (전송 수단을 모름)
- `ActionLogTransport` (인터페이스, **seam**) — `send(ActionLogMessage)`. 비throwing(실패 삼킴) 규약 → 메시징 장애가 본 트랜잭션을 깨지 않음(best-effort).
  - `SqsActionLogTransport` — `@ConditionalOnProperty(transport=sqs, matchIfMissing=true)` → **기본값**. `SqsTemplate.send(queue, msg)`.
  - `KafkaActionLogTransport` — `@ConditionalOnProperty(transport=kafka)`. `KafkaTemplate.send(topic, key, msg)`.
- 소비자 (전송별 조건부 활성)
  - `ActionLogConsumer` — `@SqsListener` (sqs 모드)
  - `KafkaActionLogConsumer` — `@KafkaListener` (kafka 모드)
- `ActionLogWriter` — **두 소비자 공유**. `ActionLogMessage` → `ACTION_LOG` 행 저장 + 운영자명 보강. 영속화 경로가 전송수단과 무관하게 동일.
- `SqsConfig` — 큐 생성 보장. sqs 모드에서만 활성(kafka 모드는 SQS 불필요).

> 이 패턴은 프로젝트의 다른 seam(PaymentProvider, GeocodeProvider, ChatProvider…)과 동일한 사상이다: **빈 교체 + env 플래그**.

---

## 2. 원리 (Kafka 핵심 개념)

**토픽(Topic)·파티션(Partition).** 메시지는 `streamhub-action-log` 토픽에 쌓인다. 토픽은 여러 파티션으로 나뉘어 병렬 처리되며, **순서는 "파티션 안에서만" 보장**된다. 그래서 우리는 **메시지 키를 `adminId`로** 준다(`KafkaActionLogTransport`). 같은 운영자의 액션은 같은 파티션으로 가서 **운영자별 순서가 보존**된다.

**컨슈머 그룹(Consumer Group)·오프셋(Offset).** 같은 `group-id`(`streamhub-action-log`)의 컨슈머들이 파티션을 나눠 읽어 **수평 확장**한다. 어디까지 읽었는지는 오프셋으로 기록되어, 재시작 시 이어 읽는다. 인스턴스를 늘리면 자동으로 파티션이 재분배(rebalance)된다.

**전달 보장 = at-least-once(최소 한 번).** 오프셋은 처리(=DB 저장) 성공 후 커밋된다. 따라서 리밸런싱/재시도 시 **같은 메시지가 다시 올 수 있다**(중복). 감사로그는 중복 행이 치명적이지 않고, 기존 SQS 경로도 동일한 at-least-once라 의미론이 일치한다.
> 멱등(idempotency)이 필요하면: 메시지에 이벤트 UUID를 실어 `ACTION_LOG`에 unique 제약 + upsert. (현재 범위 밖, 향후 개선)

**보존(retention)·리플레이.** SQS는 소비 후 메시지가 사라지지만, Kafka는 보존 기간 동안 **남아 있어 재처리/리플레이**가 가능하다. (장애 후 재집계 등)

**SQS vs Kafka 한눈에**

| | SQS | Kafka |
|---|---|---|
| 모델 | 큐(소비 후 삭제) | 로그(보존·리플레이) |
| 순서 | FIFO 큐만 보장 | 파티션 내 보장(키로 제어) |
| 확장 | 컨슈머 수평 확장 | 파티션×컨슈머그룹 |
| 처리량 | 중 | 매우 높음 |
| 운영 | 완전관리(AWS) | 브로커 직접 운영(또는 MSK) |

---

## 3. 설정 방법 (Setup)

### 3.1 로컬에서 Kafka로 돌려보기

```bash
# 1) 인프라 기동 (kafka 컨테이너 포함 — docker-compose.yml에 추가됨)
docker compose up -d            # mysql/redis/minio/localstack/kafka

# 2) 백엔드를 kafka 모드로 실행 (호스트에서 ./mvnw)
cd streamhub-api
EVENTLOG_TRANSPORT=kafka KAFKA_BOOTSTRAP_SERVERS=localhost:9092 ./mvnw spring-boot:run
#   (기본은 sqs라, 아무것도 안 주면 기존처럼 SQS로 동작)
```

- 호스트의 `./mvnw`는 Kafka의 **EXTERNAL 리스너 `localhost:9092`** 로 붙는다.
- 토픽 `streamhub-action-log`는 `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`라 첫 발행/소비 시 자동 생성된다.
- 관리자 콘솔에서 아무 액션(회원 승인 등)을 하면 → Kafka로 발행 → 소비 → `ACTION_LOG` 적재 → '감사 로그' 화면에 보임.

### 3.2 env 한 장 요약

| env | 기본값 | 의미 |
|---|---|---|
| `EVENTLOG_TRANSPORT` | `sqs` | `sqs` \| `kafka` 전송수단 선택 |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | 브로커 주소. 백엔드가 **컨테이너 안**이면 `kafka:29092` |
| `KAFKA_ACTION_LOG_TOPIC` | `streamhub-action-log` | 토픽명 |
| `KAFKA_GROUP_ID` | `streamhub-action-log` | 컨슈머 그룹 |

> 백엔드를 도커 컨테이너로 띄우는 경우(`docker-compose.deploy.yml`)엔 `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`(INTERNAL 리스너)로 줘야 한다. 호스트 실행이면 `localhost:9092`.

### 3.3 듀얼 리스너가 필요한 이유

Kafka는 "접속해서 받은 주소(advertised listener)"로 클라이언트가 재접속한다. 호스트(`localhost`)와 컨테이너 네트워크(`kafka`)는 주소가 달라, 두 리스너를 둔다:
- `EXTERNAL://localhost:9092` — 호스트의 `./mvnw`용
- `INTERNAL://kafka:29092` — 같은 도커 네트워크 컨테이너용

### 3.4 배포(라이브)

라이브는 **SQS 유지**가 기본이다(seam이라 무관). Kafka를 운영에 올리려면 관리형(AWS MSK)이나 별도 브로커가 필요하므로, 포트폴리오에선 **로컬 데모로 "Kafka 대응" 경험을 시연**하고 라이브는 SQS로 두는 구성을 권장한다.

---

## 4. 검증 체크리스트

- [ ] `./mvnw test` — 빌드/단위테스트 통과 (Java 21)
- [ ] `docker compose up -d` 후 `docker compose ps`에서 kafka `healthy`
- [ ] `EVENTLOG_TRANSPORT=kafka`로 기동 → 로그에 `@KafkaListener` 시작, SQS 리스너 없음
- [ ] 관리자 액션 → '감사 로그'에 적재 확인
- [ ] `EVENTLOG_TRANSPORT` 미설정(또는 `sqs`) → 기존 SQS 경로 그대로 동작(회귀 없음)
- [ ] 토픽 확인: `docker exec streamhub-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic streamhub-action-log --from-beginning`

## 5. 향후 개선 (면접 어필 포인트)
- **DLT(Dead-Letter Topic)** + `DefaultErrorHandler` 재시도 정책
- **멱등 소비**(이벤트 UUID + unique 제약)로 정확히-한-번에 근접
- **파티션 수 늘리고** 컨슈머 인스턴스 확장 → 처리량/리밸런싱 시연
- 스키마 진화(**Avro + Schema Registry**)
