# GraceOn (은혜온)

[한국어](README.md) · **English**

A full-stack portfolio that recreates a church worship/media platform (worship videos, praise music,
CCM album commerce, church finder, donations, membership) on the same production stack a real service
would use. A single Spring Boot backend serves an admin console and a public user site, with an AWS
deployment pipeline on top.
> Music streams over HLS (AES-128); video uses YouTube embeds (replay) — no live broadcast yet.

> **The full showcase (features, screens, stats) lives on the roadmap page → https://streamhub-user.vercel.app/roadmap**
> This README deliberately avoids that overlap and covers **developer-facing info only** (architecture, how to run).

> **🔗 Live**
> - Roadmap (showcase) — https://streamhub-user.vercel.app/roadmap
> - User site — https://streamhub-user.vercel.app
> - Admin console — https://streamhub-admin.vercel.app
>     - `viewer` / `viewer1234` (read-only — public demo account to browse every screen)
>     - RBAC: SYSTEM / CHURCH_MANAGER (church-scoped) / VIEWER implemented (write-capable accounts are private)
> - User demo account — `member01@streamhub.test` / `member1234`

---

## Screenshots

### User site (mobile · dark)
<p>
  <img src="docs/screenshots/user-home.png" width="200" alt="Home" />
  <img src="docs/screenshots/user-albums.png" width="200" alt="CCM albums · 30s preview" />
  <img src="docs/screenshots/user-churches.png" width="200" alt="Church finder (Leaflet map)" />
  <img src="docs/screenshots/user-video.png" width="200" alt="Worship video" />
</p>

### Admin console (desktop)
<img src="docs/screenshots/admin-dashboard.png" width="860" alt="Ops dashboard — KPIs + charts (watch-time by channel is aggregated from real tracking events)" />

| Feature catalog (honest live/demo/external badges) | Orders (AG Grid · state machine) |
|---|---|
| <img src="docs/screenshots/admin-catalog.png" width="420" alt="Feature catalog" /> | <img src="docs/screenshots/admin-orders.png" width="420" alt="Orders" /> |

---

## Architecture

```
┌─────────────────────────────┐   ┌─────────────────────────────┐
│ streamhub-web  (admin)       │   │ streamhub-user-web (user)    │
│ Next14·NextAuth v5·React Query│   │ Next14·React Query·mobile UI  │
│ AG Grid·ApexCharts·RHF+Zod   │   │ public (read-only) + member   │
└──────────────┬──────────────┘   └──────────────┬──────────────┘
   /v1/** (Bearer JWT, admin)         /pub/v1/** (public) · /pub/v1/auth (member)
               └───────────────┬───────────────────┘
                               ▼
        ┌──────────────────────────────────────────────┐
        │       streamhub-api (Spring Boot 4.1)          │
        │  SecurityFilterChain (stateless JWT)           │
        │   └ admin token ↔ member token isolation       │
        │  Controller → Service                          │
        │   ├ Repository (JPA, simple CRUD)              │
        │   └ Mapper (MyBatis, dynamic search/join/agg)  │
        └────┬──────────┬───────────┬──────────┬─────────┘
          MySQL 8.4   Redis      S3 / MinIO   SQS / LocalStack
          (main DB)   (cache)    (media)      (audit-log queue)
```

**Key design decisions**
- **JPA + MyBatis hybrid** — JPA for simple CRUD, MyBatis XML for dynamic search / joins / aggregation.
- **Stateless JWT + token isolation** — admin tokens (role claim) and member tokens (`type:member`, no role) are separated so a member token can never reach an admin API (blocked in the filter).
- **Automatic token rotation** — NextAuth jwt callback refreshes pre-expiry; refresh tokens are whitelisted in Redis and revoked on logout.
- **No-switch S3 SDK** — local MinIO vs. prod S3 differ only by presence of `storage.endpoint`, **zero code change**.
- **Async audit log** — key actions are published to SQS and consumed by `@SqsListener` (best-effort; failures never affect the main transaction).
- **RBAC + multi-tenancy** — `@PreAuthorize` + `AdminPrincipal` from JWT claims scope church managers to their own church's data (no DB lookup).
- **Contract automation** — backend Swagger → Orval → type-safe React Query hooks (admin side).

---

## Adapter seams (where real keys plug in)

External services switch via **bean swap + `.env` flag**, not code branches (services depend only on the
interface). Defaults are key-free deterministic mocks/seeds; inject a key to go live.

| seam (interface) | flag | default → live |
|---|---|---|
| `PaymentProvider` | `app.payment.provider`, `iamport.*` | `mock` → `portone` (Iamport, `IAMPORT_*`) / `toss`·`kakao`·`paypal` (key-gated) |
| `DeliveryProvider` | `app.delivery.provider` | `sweettracker` (default, demo key) ↔ `mock` |
| `SmsSender` | `app.sms.sender` | `mock` → `aligo`/`solapi` (API key + sender no.) |
| `ChatProvider` | `app.chat.provider`, `app.chat.llm.api-key` | `rule` → `llm` |
| `MusicPreviewProvider` | `app.music.provider` | `seed` → `external` (music API) |
| `GeocodeProvider` | `church.geocode.provider`, `church.geocode.kakao-rest-key` | `seed` → `kakao` (Kakao Local) |

> e.g. PortOne (Iamport) payments go live by injecting `IAMPORT_IMP`/`IAMPORT_APIKEY`/`IAMPORT_SECRET`
> + `PAYMENT_PROVIDER=PORTONE` — the real PG (imp_uid verification + refund) works with no code change.
> Toss sandbox switches on the same way via `PAYMENT_TOSS_*`.

---

## Run locally

Prereqs: Docker (or Colima), JDK 21, Node 20.

```bash
# 1) Infra (MySQL + Redis + MinIO + LocalStack)
docker compose up -d

# 2) Backend (localhost:8080) — creates schema + seeds demo data on first boot
cd streamhub-api && ./mvnw spring-boot:run

# 3) Admin console (localhost:3000)
cd streamhub-web && npm install --legacy-peer-deps && npm run dev

# 4) User site (localhost:3001)
cd streamhub-user-web && npm install --legacy-peer-deps && npm run dev
```

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- MinIO console: http://localhost:9001 (streamhub / streamhub123)
- Regenerate admin API client (with backend running): `cd streamhub-web && npm run gen`

---

## Tests

```bash
cd streamhub-api && ./mvnw test
```
JUnit 5 + Mockito unit tests — JWT issue/verify/rotation + admin↔member token isolation, member RBAC
scoping & status transitions, coupon discount math, order/subscription state machines, recurring-billing
idempotency & failure handling.

---

## Deploy

- **Backend** — Terraform provisions AWS (EC2 + RDS + S3 + SQS + ECR + SSM); the EC2 container sits behind CloudFront (HTTPS). Pushing `streamhub-api/**` to `main` triggers GitHub Actions to build → ECR → SSM zero-downtime roll. Manual: `gh workflow run deploy.yml`.
- **Frontend** — Vercel (admin + user) connected to GitHub, auto-redeploys on `main` push.
- Details `deploy/README.md` · low-cost single-VM alt `DEPLOY-FREE.md` · teardown `terraform destroy`.

---

## Project structure

```
graceon/                  # repo root (formerly streamhub-admin) — monorepo
├── streamhub-api/        # Spring Boot (org.streamhub.api: base/ · auth/ · v1/{admin,member,content,statistics,actionlog,post,pub,goods,order,donation,dashboard,coupon,...})
├── streamhub-web/        # Admin Next.js (src/app/(protected)/** · src/apis/query[Orval-generated])
├── streamhub-user-web/   # User Next.js (src/app · src/components · src/lib[manual fetch + React Query])
├── deploy/               # Terraform IaC · deploy scripts · runbooks
├── docker-compose.yml    # MySQL · Redis · MinIO · LocalStack
└── docs/                 # domain design specs (specs/) · design docs · screenshots
```
