# streamhub 배포 런북 (self-service)

저(Claude) 없이 직접 **코드 배포 / 환경변수·시크릿 주입 / 로컬 실행**을 할 수 있도록 정리한 문서입니다.
모든 명령은 그대로 복사해서 실행할 수 있게 적었고, **`<...>` 부분만** 본인 값으로 바꾸면 됩니다.

---

## 0. 핵심 식별자 (한 번 보고 외울 필요 없음, 여기서 복사)

| 항목 | 값 |
|------|-----|
| GitHub 레포 | `1mJeeHwan/graceon` (구 streamhub-admin) · 배포 브랜치 **`main`** |
| AWS 계정 | **`<AWS_ACCOUNT_ID>`** (포트폴리오용) — ⚠️ default 프로파일(`<OTHER_AWS_ACCOUNT_ID>`)은 **다른 계정** |
| AWS 프로파일 | **`deployAccount`** (`~/.aws/credentials`) · 리전 `ap-northeast-2` |
| 라이브 EC2 | `<INSTANCE_ID>` (tag `streamhub-api`) |
| 라이브 API(CDN) | `https://dpdtwguq8ke3x.cloudfront.net` |
| VM env 파일 | `/etc/streamhub/api.env` (chmod 600, 컨테이너가 `--env-file`로 읽음) |
| VM 롤 스크립트 | `/usr/local/bin/streamhub-deploy` (ECR pull → `docker run`) |
| user-web (Vercel) | project `streamhub-user` · `https://streamhub-user.vercel.app` |
| admin-web (Vercel) | project `streamhub-web` · `https://streamhub-admin.vercel.app` |

---

## 1. 배포는 어떻게 흘러가나 (멘탈 모델)

**백엔드(API)** — `main`에 push하면 GitHub Actions가 자동 배포:
```
git push → Actions "Deploy API"(deploy.yml) → 이미지 빌드(linux/amd64) → ECR push
        → SSM Run Command로 EC2에서 streamhub-deploy 실행
        → docker run --env-file /etc/streamhub/api.env <ecr>:latest   (롤, ~30초 다운타임)
```
- ⚠️ **`streamhub-api/**` 또는 `deploy.yml`이 바뀐 push만** Deploy API를 트리거합니다. 프론트만 바뀐 push는 API 배포 안 함.
- VM은 **docker compose를 쓰지 않습니다.** `streamhub-deploy`(docker run)로 돕니다. → 라이브 env는 **`/etc/streamhub/api.env`**가 진실의 원천.

**프론트엔드(user-web / admin-web)** — Vercel이 `main` push에 자동 배포.
- ⚠️ `NEXT_PUBLIC_*` 변수는 **빌드 타임에 코드에 박힙니다.** → Vercel에 먼저 env를 넣고 **재배포**해야 반영됨.

---

## 2. 코드 변경 배포 (매일 쓰는 흐름)

```bash
cd ~/Documents/MyToys/streamhub-admin

# 1) 변경 커밋 (내 파일만 path 지정 권장 — 다른 작업 섞임 방지)
git add <바뀐_파일들>
git commit -m "<메시지>"

# 2) main으로 push (= 배포 트리거)
git push origin HEAD:main          # 현재 브랜치를 main에 올림(보통 feat 브랜치 작업)

# 3) 배포 진행 보기
gh run list --limit 3
gh run watch <RUN_ID> --exit-status   # 위 목록의 Deploy API run id

# 4) 라이브 검증 (예시)
curl -s -o /dev/null -w "%{http_code}\n" \
  "https://dpdtwguq8ke3x.cloudfront.net/pub/v1/churches?lat=37.5&lng=127&radiusKm=0.5&pageSize=1"
```
- 잘못 올렸을 때 롤 단계 전에 막기: `gh run cancel <RUN_ID>` (Build 단계에서 취소하면 라이브 무사).

---

## 3. 라이브에 환경변수/시크릿 넣기 (예: 새 API 키)

### 방법 A — 즉시 반영 (SSM, 인프라 재생성 없음) ← 보통 이거
EC2의 `/etc/streamhub/api.env`에 줄을 넣고 컨테이너만 롤. (카카오·Gemini 키를 이렇게 넣었음)

```bash
export AWS_PROFILE=deployAccount AWS_DEFAULT_REGION=ap-northeast-2

aws ssm send-command \
  --instance-ids <INSTANCE_ID> \
  --document-name AWS-RunShellScript \
  --comment "set <KEY_NAME>" \
  --parameters 'commands=[
    "set -e",
    "F=/etc/streamhub/api.env",
    "sed -i \"/^<KEY_NAME>=/d\" $F",
    "printf \"<KEY_NAME>=%s\\n\" \"<값>\" >> $F",
    "/usr/local/bin/streamhub-deploy"
  ]' \
  --query 'Command.CommandId' --output text
# → 나온 CommandId로 결과 확인:
aws ssm get-command-invocation --command-id <CMD_ID> --instance-id <INSTANCE_ID> \
  --query 'StandardOutputContent' --output text
```
- ⚠️ 시크릿이 SSM 커맨드 로그(CloudTrail)에 남습니다. 본인 계정이라 위험은 낮지만, 민감하면 SecureString 파라미터(방법 B)로.
- `api.env`는 EC2 디스크 파일이라 **컨테이너 롤·재부팅에도 유지**됩니다. **인스턴스가 재생성될 때만** 사라짐 → 그건 방법 B로 영구화.

### 방법 B — 영구화 (terraform 베이크인, 인스턴스 교체에도 유지)
`deploy/terraform`에 4곳 추가(카카오/Gemini가 이 패턴):
1. `variables.tf` — `variable "<name>" { sensitive = true }`
2. `main.tf` — `aws_ssm_parameter "<name>"`(SecureString) + IAM **ReadSecrets** resources에 그 arn 추가 + templatefile에 `<name>_param` 전달
3. `user_data.sh.tftpl` — SSM에서 fetch + `api.env` heredoc에 `KEY=$VALUE` 추가
4. `terraform.tfvars`(gitignore) — 실제 값
```bash
cd deploy/terraform && export PATH="$HOME/.local/bin:$PATH"
export AWS_PROFILE=deployAccount AWS_DEFAULT_REGION=ap-northeast-2
terraform fmt && terraform validate
terraform plan        # 변경 검토
# ⚠️ terraform apply 는 user_data 변경 시 EC2를 "교체"합니다(다운타임+새 IP). 데모에선 보통 방법 A로 즉시 넣고,
#    베이크인은 코드로만 둬서 "다음 신규 인스턴스부터" 적용되게 함. 정말 교체해도 되면:
# terraform apply
```

---

## 4. Vercel 환경변수 (프론트 `NEXT_PUBLIC_*`)

**대시보드(권장):** vercel.com → 프로젝트(`streamhub-user`) → Settings → Environment Variables
→ Key/Value 추가, **Production** 체크 → Save → **Deployments → 최신 → ⋯ → Redeploy**.

**CLI/API(토큰 필요):**
```bash
# 토큰: vercel.com/account/tokens 에서 생성(짧은 만료 권장, 쓰고 Delete)
VTOKEN='<vercel_token>'
curl -s -X POST \
  "https://api.vercel.com/v10/projects/prj_ZYeUQYlpivcUKLzvv9RQkEVKuxw2/env?teamId=team_XCQ04d1Ftg3nAKbvMc6Acjr9" \
  -H "Authorization: Bearer $VTOKEN" -H "Content-Type: application/json" \
  -d '{"key":"<KEY>","value":"<값>","type":"encrypted","target":["production","preview"]}'
# 그다음 빈 커밋 push로 재빌드 트리거:
git commit --allow-empty -m "chore: trigger vercel rebuild"   # ⚠️ staged 변경 없을 때만!
git push origin HEAD:main
```

---

## 5. 로컬 실행

```bash
cd ~/Documents/MyToys/streamhub-admin

# 인프라(mysql/redis/minio/localstack)
docker compose up -d

# 로컬 시크릿은 streamhub-admin/.env (gitignore, compose가 읽음). 예:
#   CHAT_PROVIDER=llm
#   CHAT_LLM_API_KEY=<gemini_key>
#   CHURCH_DISCOVERY_PROVIDER=kakao
#   CHURCH_GEOCODE_KAKAO_REST_KEY=<kakao_rest_key>

# API (8080을 호스트로 노출하는 localdev override 포함)
docker compose -f docker-compose.yml -f docker-compose.deploy.yml -f docker-compose.localdev.yml up -d
#   또는 호스트에서: ./mvnw -f streamhub-api/pom.xml spring-boot:run

# user-web (:3001) — NEXT_PUBLIC 키는 streamhub-user-web/.env.local 에
cd streamhub-user-web && npm run dev
```

---

## 6. 자주 밟는 지뢰 (겪은 것들)

- 🚫 **`npm run dev` 떠 있을 때 `npm run build` 금지** — 프로덕션 `.next`가 dev와 충돌 → CSS/JS 404 → "디자인 깨짐"처럼 보임. **해결:** dev 종료 → `rm -rf .next` → `npm run dev` 재기동.
- 🚫 **staged 변경이 있을 때 `git commit --allow-empty` 금지** — 빈 커밋이 아니라 **staged를 통째로 삼켜** 커밋함. 빈/트리거 커밋은 `git status`로 staged 없음 확인 후.
- 🔒 **`.git/index.lock` 에러:** 활성 git 작업이 없으면 `find .git -name '*.lock' -delete` 후 재시도.
- ☁️ **AWS는 프로파일 주의:** `export AWS_PROFILE=deployAccount` (default는 다른 계정!). 세션 만료 시 재인증 필요.
- 🗺️ **카카오 지도(JS SDK):** REST 키 아님 — **JavaScript 키** + 카카오 디벨로퍼스 Web 플랫폼에 도메인(`localhost:3001`, `streamhub-user.vercel.app`) 등록 필요. 키는 Vercel `NEXT_PUBLIC_KAKAO_MAP_KEY`.
- 🤖 **챗봇:** `CHAT_PROVIDER=llm` + `CHAT_LLM_API_KEY`(Gemini) 주입 시 LLM, 키 비거나 실패하면 **rule 자동 폴백**(사이트 안 깨짐). 모델 `gemini-2.5-flash`.

---

## 7. 시크릿이 사는 곳 (값은 여기 안 적음)

| 시크릿 | 라이브 | 로컬 | IaC |
|--------|--------|------|-----|
| DB/JWT | SSM `/streamhub/*` → api.env | .env | terraform.tfvars |
| Kakao REST | api.env `CHURCH_GEOCODE_KAKAO_REST_KEY` | .env | tfvars `kakao_rest_key` |
| Gemini | api.env `CHAT_LLM_API_KEY` | .env | tfvars `chat_llm_api_key` |
| Kakao JS(map) | — (프론트) | .env.local | Vercel env `NEXT_PUBLIC_KAKAO_MAP_KEY` |

> `terraform.tfvars`, `.env`, `.env.local`은 전부 **gitignore** — 커밋되지 않습니다.
