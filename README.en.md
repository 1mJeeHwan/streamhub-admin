# StreamHub

[한국어](README.md) · **English**

A full-stack portfolio project that reproduces a church/streaming platform **using the same production
stack as the real service**. A single Spring Boot backend powers two frontends — an **operator admin
console** and a **public user media site** — covering authentication, RBAC, file upload, cached
statistics, asynchronous audit logging, and member login as working vertical slices. It runs locally
with `docker-compose` and deploys to AWS via Terraform.

> **Demo accounts**
> - Admin console — `admin` / `admin1234` (system), `manager` / `manager1234` (church manager)
> - User site — `member01@streamhub.test` / `member1234`

---

## Screenshots

### User site (mobile · dark)
<p>
  <img src="docs/screenshots/user-home.png" width="200" alt="Home" />
  <img src="docs/screenshots/user-video.png" width="200" alt="Video detail" />
  <img src="docs/screenshots/user-search.png" width="200" alt="Unified search" />
  <img src="docs/screenshots/user-mypage.png" width="200" alt="My page" />
</p>

### Admin console (desktop)
<img src="docs/screenshots/admin-dashboard.png" width="860" alt="Statistics dashboard" />

| Members (AG Grid) | Content management |
|---|---|
| <img src="docs/screenshots/admin-members.png" width="420" alt="Members" /> | <img src="docs/screenshots/admin-content.png" width="420" alt="Content" /> |

---

## Architecture

```
┌─────────────────────────────┐   ┌─────────────────────────────┐
│ streamhub-web (admin console)│   │ streamhub-user-web (user)    │
│ Next14·NextAuth v5·ReactQuery│   │ Next14·React Query·mobile UI │
│ AG Grid·ApexCharts·RHF+Zod   │   │ public (read-only)+ login    │
└──────────────┬──────────────┘   └──────────────┬──────────────┘
   /v1/** (Bearer JWT, admin)         /pub/v1/** (public) · /pub/v1/auth (member)
               └───────────────┬───────────────────┘
                               ▼
        ┌──────────────────────────────────────────────┐
        │       streamhub-api (Spring Boot 3.4)          │
        │  SecurityFilterChain (stateless JWT)           │
        │   └ admin token ↔ member token isolation       │
        │  Controller → Service                          │
        │   ├ Repository (JPA, simple CRUD)              │
        │   └ Mapper (MyBatis, dynamic search/joins/agg) │
        └────┬──────────┬───────────┬──────────┬─────────┘
          MySQL 8     Redis      S3 / MinIO   SQS / LocalStack
```

**Key design decisions**
- **JPA + MyBatis hybrid** — JPA for simple CRUD, MyBatis XML for dynamic search/joins/aggregation.
- **Stateless JWT + token isolation** — admin tokens carry a role claim; member tokens (`type:member`,
  no role) are structurally blocked from admin endpoints in the auth filter.
- **Proactive token rotation** — NextAuth refreshes before expiry; refresh tokens are whitelisted in
  Redis so logout invalidates them.
- **No-swap S3 SDK** — local MinIO ↔ prod S3 switched only by `storage.endpoint`, zero code change.
- **Async audit log** — key actions published to SQS, consumed by `@SqsListener` (best-effort).
- **RBAC** — `@PreAuthorize` + JWT-claim `AdminPrincipal` scopes church managers to their own church.
- **API contract automation** — backend Swagger → Orval → type-safe React Query hooks (admin).

---

## What's inside

### Admin console (`streamhub-web`)
| Domain | Techniques shown |
|---|---|
| **Members** | dynamic search/filter/pagination (MyBatis) · detail/edit · bulk approve/deny · church-scoped RBAC |
| **Content** | CRUD · many-to-many hashtags · **drag-and-drop upload → MinIO/S3** · complex joins |
| **Dashboard** | MyBatis aggregation · **Redis caching (@Cacheable)** · ApexCharts (trend/Top N/donut) |
| **Audit log** | admin actions published/consumed via SQS, visible to system admins only |

### User site (`streamhub-user-web`)
A mobile-first public media site in the tone of a real production user app.
- **Video / music** (HTML5 players) and **posts** — only `PUBLISHED` content is exposed
- **URL-based unified search** (`?q=`, shareable/refresh-safe, title match) · pagination
- **Member login + my page** — member-scoped JWT, localStorage session, protected routes

---

## Portfolio admin expansion (Goods · Orders · Membership · Dashboard)

Reinterprets the **operational breadth of the gnuboard5 / youngcart5 admin** into the PalmPlus
(church/streaming) domain — going beyond simple CRUD to working **order state machines, recurring
billing, a point ledger, and a unified operations dashboard**. See
[`docs/portfolio-admin-design.md`](docs/portfolio-admin-design.md) for the design intent, domain
mapping, and seed strategy.

### Domains / screens added

| Domain | Highlights | Routes |
|---|---|---|
| **Goods shop** | products · category tree · options/stock · gallery images · Pareto sale distribution | `/goods` · `/catalog` |
| **Orders** | **state machine** `PLACED→PAID→READY→SHIPPING→DONE` (branches `CANCEL`/`RETURN`), stock & total recompute on transition | `/order` |
| **Recurring donation / membership subscription** | **billing CRON sim** (`@Scheduled`, 5-min scan → cycle charge), plans & grades (Bronze/Silver/Gold/Angel) | `/subscription` · `/subscription-plan` · `/billing-calendar` |
| **Donation history** | recurring / one-off aggregation, campaign-linked | `/donation` |
| **Point ledger** | **append-only ledger** (delta · balanceAfter), donation accrual & expiry scheduler | `/point` |
| **Unified ops dashboard** | KPI strip + ApexCharts (trend/Top N/donut) + live activity feed + **to-do queue** · MyBatis aggregation + Redis cache | `/admin-ops` · `/dashboard` |
| **Feature catalog** | showcases built screens with honest `live` / `mock` status badges | `/catalog` |

### Seed data scale (measured)

A deterministic `PortfolioSeeder` (fixed seed, reset-stable) generates a **6-month operational pattern**:

| Goods | Orders | Donations | Point ledger | Subscriptions | Membership plans |
|---|---|---|---|---|---|
| 64 | 1,700 | 1,400 | 1,306 | 24 | 4 |

- **Realistic distribution** — orders 70/15/10/5 (DONE/SHIPPING·READY/PAID/CANCEL·RETURN), Pareto goods
  long-tail, some sold-out / low-stock items.
- **Copyright-safe images** — Picsum `seed` URLs only (verified HTTP 200 host).
- **Masked PII** — `faker(ko)` + masking (`Kim O-jun`, `010-****-1234`), zero real personal data.
- **"Demo · test mode" badge** — no real payments or dispatch.

### Verification status

- ✅ Backend — compiles / boots / new endpoints return 200.
- ✅ Frontend — build green.
- ✅ Live UI smoke — 4 dashboard charts and real goods images render.

> **Known polish** — a few today-KPI aggregates still read 0 (demo data distribution vs. reference-date alignment).

---

## Location & commerce expansion (Church finder · Worship registration · CCM commerce · Payment · SMS · Chatbot)

After **researching real production services** (ch114, Duranno mall, Toss Payments, Aligo, etc.), this
adds church finder, worship/new-family registration, CCM album commerce, payment, SMS, and a chatbot.
The hard constraint is **zero external API keys** — all data is filled by deterministic **mocks/seeds**,
and every domain has an **adapter seam** (interface + Provider + `.env` flag) so a real integration is a
bean swap later. There are **no runtime external calls**, and screens carry a **"demo · test data" badge**.
See [`docs/expansion-research-and-plan.md`](docs/expansion-research-and-plan.md) for the research,
the denomination-data limits, and the phased plan.

### Features added

| Domain | Highlights | Routes / endpoints |
|---|---|---|
| **Church finder** | **Leaflet + OSM** (key-free map tiles) · **Haversine** distance sort · denomination/radius/keyword filters · browser **Geolocation** (fallback on deny) · markers, worship times, directions | User `/churches` · `/churches/[id]` · `GET /pub/v1/churches`, `/pub/v1/churches/{id}` / Admin `/v1/churches/**` (CRUD) |
| **Worship · new-family registration** | multi-step form (personal/address/faith) · **dynamic family array (max 5)** · RHF + Zod validation · admin submission list & status transition | User `/churches/[id]/register` · `POST /pub/v1/worship` / Admin `POST /v1/worship/list`, `PATCH /v1/worship/{id}/status` |
| **CCM albums** | **album ↔ `GOODS_ITEM` bridge** (purchase reuses the order domain) · `Album→Track (1:N)` · **30s HTML5 `<audio>` preview** (single global audio) · offline store **map** | User `/albums` · `/albums/[id]` · `/stores` · `GET /pub/v1/albums`, `/pub/v1/albums/{id}/tracks/{trackId}/preview`, `/pub/v1/stores` / Admin `/v1/album/**`, `/v1/store/**` |
| **Payment** | **`PaymentProvider` seam** (`mock`/`toss`/`paypal`/`kakao`/`card`) · `request → approve → receipt` flow · `testMode` receipt | `POST /v1/payment/request`, `/v1/payment/approve` · `GET /v1/payment/{orderId}/receipt` |
| **SMS** | **`SmsSender` seam** (`mock`/`aligo`/`solapi`) · order/registration alerts · admin **custom send** · send history | `POST /v1/sms/send`, `/v1/sms/list` |
| **Chatbot** | **`ChatProvider` seam** (`rule`/`llm`) · rule-based **intent classification** (product Q&A / FAQ / order lookup) · session & history persisted | User widget `ChatbotWidget` · `POST /v1/chat/send` · `GET /v1/chat/{sessionKey}/history` |

### Adapter seams — where real keys plug in

Going live is a **bean swap + `.env` flag**, not a code branch (services depend only on the interface).

| Seam (interface) | Flag | Default (mock) → real |
|---|---|---|
| `PaymentProvider` | `app.payment.provider`, `app.payment.test-mode` | `mock` → `toss`/`paypal`/`kakao` (inject test key) |
| `SmsSender` | `app.sms.sender` | `mock` → `aligo`/`solapi` (API key + sender number) |
| `ChatProvider` | `app.chat.provider`, `app.chat.llm.api-key` | `rule` → `llm` |
| `MusicPreviewProvider` | `app.music.provider` | `seed` → `external` (music API) |
| `GeocodeProvider` | `church.geocode.provider`, `church.geocode.kakao-rest-key` | `seed` → `kakao` (Kakao Local) |
| `PostcodeProvider` | `app.worship.postcode.provider` | `mock` → postcode-search API |
| `MapProvider` (frontend) | — | abstracted so Leaflet/OSM can swap to Kakao Maps SDK |

### Data / public-data conclusion

**There is effectively no public dataset of churches by denomination** — none of data.go.kr, LOCALDATA, or
Kakao expose a denomination field together with coordinates. So location search is best built as a **map
API call**, not as owned data (the real-service premise: Kakao Local + Geolocation). This portfolio
approximates that key-free with a **deterministic seed of ~40 churches** (28 Seoul + 12 Gyeonggi, with
coordinates, heuristic denomination labels, and worship times). Coordinates are nudged by an index-based
±offset so no row pinpoints a real address. See the research doc above for details.

### Verification status

- ✅ Backend — **48 tests pass**, **reseed boot** clean, new endpoints return **200** (churches/worship/albums/stores/payment/sms/chat).
- ✅ User site — **prod build green**, **Playwright** (church map · album preview · chatbot) passes.
- ⚠️ **Honest note** — payment, SMS, chatbot, and map are all **demo/test mode** (no real charge, dispatch, or external call). **Some admin management screens (church/worship/album/store/SMS) and AWS live streaming are not yet built (future work)**.

---

## Tech stack

**Backend** — Spring Boot 3.4 · Java 21 · MySQL 8 · Redis · JPA(Hibernate) + MyBatis · Spring Security +
JWT(auth0) · AWS SDK v2 (S3/SQS) · spring-cloud-aws · springdoc OpenAPI · Lombok · JUnit 5 + Mockito

**Frontend** — Next.js 14 (App Router) · React 18 · TypeScript · TanStack React Query v5 · NextAuth v5
(admin) · Zustand · Orval · AG Grid Community · ApexCharts · React Hook Form + Zod · Tailwind CSS ·
Vitest

**Infra** — Docker Compose (local) · Terraform (AWS: EC2/RDS/S3/SQS/ECR/SSM) · Vercel · GitHub Actions

---

## Run locally

Prerequisites: Docker (or Colima), JDK 21, Node 20.

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
- Regenerate admin API client (backend running): `cd streamhub-web && npm run gen`

> Node 20 LTS recommended. Seed: 5 churches · 60 members · 24 contents · 10 posts · 800 watch events.

---

## Tests

```bash
cd streamhub-api && ./mvnw test          # 24 JUnit/Mockito tests
cd streamhub-user-web && npm test        # 15 Vitest tests
```
Backend: JWT issue/verify/rotation + **admin↔member token isolation**, member RBAC scoping & state
transitions, member login (status gate / failure paths), public-post `PUBLISHED` enforcement, and a
standalone MockMvc slice for the public controller. Frontend: formatting helpers and the typed fetch
wrapper (success unwrap, `ApiError` status mapping, bearer header).

---

## Deploy (AWS)

See `deploy/README.md` — Terraform provisions EC2/RDS/S3/SQS/ECR, `deploy/scripts/deploy-api.sh`
pushes the image to ECR and rolls it out via SSM, frontend goes to Vercel. `terraform destroy` tears
everything down in one shot (cost safety).

---

## Project structure

```
streamhub-admin/
├── streamhub-api/        # Spring Boot (base/ · auth/ · v1/{admin,member,content,statistics,actionlog,post,pub,goods,order,donation,dashboard})
│                         #   portfolio expansion: v1/{goods,order,donation,dashboard} + member/Point* · base/config/PortfolioSeeder
├── streamhub-web/        # Admin Next.js (src/app/(protected)/{admin-ops,goods,order,donation,subscription,subscription-plan,point,catalog,billing-calendar} · src/apis/query[Orval])
├── streamhub-user-web/   # User Next.js (src/app · src/components · src/lib[manual fetch+RQ])
├── deploy/               # Terraform IaC · deploy scripts · runbook
├── docker-compose.yml    # MySQL · Redis · MinIO · LocalStack
└── PLAN.md / USER-SITE-PLAN.md  # design docs + roadmap
```
