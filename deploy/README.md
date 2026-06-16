# StreamHub Admin — 배포 가이드 (AWS + Vercel)

로컬은 개발용(`docker-compose`), AWS는 라이브 데모/배포용입니다.
프론트는 **Vercel**, API는 **EC2**(+컨테이너 Redis), DB는 **RDS MySQL**, 파일은 **실제 S3**.
인프라는 **Terraform**으로 만들고 `terraform destroy`로 한 번에 내립니다(비용 안전).

```
[브라우저] → (https) Vercel: streamhub-web ──(https)──▶ EC2: streamhub-api ──┬─▶ RDS MySQL
                                                          └ Redis(컨테이너)  ├─▶ S3 (미디어)
                                                                            └ SSM(시크릿/배포)
```

> 로컬 S3는 MinIO, 운영은 실제 S3 — **AWS SDK 코드는 동일**. 코드 변경 없이 전환됩니다.

---

## 0. 새 AWS 계정 — 먼저 할 일 (중요)

1. 새 계정 생성 → **루트 계정에 MFA** 설정.
2. **예산 알람 필수**: Billing → Budgets → 월 $1(또는 $5) 예산 + 이메일 알림. 무료 티어 초과를 즉시 감지.
   - 콘솔: *Billing and Cost Management → Budgets → Create budget → Zero spend budget* 추천.
3. **무료 티어 확인**: 신규 계정만 12개월 무료(EC2 t2.micro 750h, RDS db.t3.micro 750h, S3 5GB). 12개월 후 과금.
4. **배포용 IAM 사용자** 생성(루트 키 쓰지 말 것):
   - 데모 단계: `AdministratorAccess`로 간단히 시작(편함) → 나중에 최소권한으로 축소 권장.
   - 액세스 키 발급 → 아래 CLI 프로필로 등록.
5. 안 쓸 땐 `terraform destroy` 또는 최소 EC2/RDS **중지**로 과금 차단.

```bash
aws configure --profile streamhub   # 새 계정의 액세스 키 입력, region=ap-northeast-2
export AWS_PROFILE=streamhub
aws sts get-caller-identity          # 새 계정인지 확인!
```

---

## 1. 사전 준비 (로컬 도구)

- Terraform ≥ 1.6, AWS CLI v2, Docker(+buildx)
- SSH 키: `ssh-keygen -t ed25519 -f ~/.ssh/streamhub` (공개키를 tfvars에 넣음)

---

## 2. 인프라 생성 (Terraform)

```bash
cd deploy/terraform
cp terraform.tfvars.example terraform.tfvars
# terraform.tfvars 편집:
#   s3_bucket_name : 전세계 유일한 이름
#   db_password    : 강한 비밀번호
#   jwt_secret     : 긴 랜덤 문자열 (openssl rand -base64 48)
#   ssh_public_key : "$(cat ~/.ssh/streamhub.pub)"
#   ssh_ingress_cidr / api_ingress_cidr : 가능하면 내 IP/32 로 제한

terraform init
terraform apply        # 생성될 자원 검토 후 yes
```

출력(outputs)에서 `ecr_repository_url`, `api_base_url`, `rds_endpoint`, `s3_bucket`을 확인.
EC2는 첫 부팅 때 이미지가 아직 없어 API 컨테이너가 안 뜹니다 → 다음 단계에서 푸시.

---

## 3. API 이미지 빌드 & 배포

```bash
cd deploy/scripts
./deploy-api.sh        # amd64로 빌드 → ECR push → SSM으로 EC2에서 컨테이너 기동
```

확인:
```bash
curl http://<api_public_dns>:8080/v3/api-docs    # 200이면 OK
```
첫 부팅 시 Hibernate가 스키마 생성 + 시드 계정(admin/admin1234)이 들어갑니다.

---

## 4. 프론트엔드 (Vercel)

1. GitHub에 `streamhub-admin`을 푸시(레포 루트가 `streamhub-admin`).
2. Vercel → New Project → 레포 선택 → **Root Directory = `streamhub-web`**.
3. 환경변수:
   - `NEXT_PUBLIC_API_BASE_URL` = `https://api.<당신의도메인>` (아래 5번 참고)
   - `NEXTAUTH_URL` = Vercel 배포 URL (예: `https://streamhub-admin.vercel.app`)
   - `NEXTAUTH_SECRET` = `openssl rand -base64 32`
4. Deploy.

---

## 5. ⚠️ HTTPS 필수 (혼합 콘텐츠 해결)

Vercel(https) 페이지가 **http API를 직접 호출하면 브라우저가 차단**합니다. API도 https여야 합니다.
무료로 해결하는 권장 방법: **EC2에 Caddy + 무료 도메인(DuckDNS) → 자동 Let's Encrypt**.

```bash
# EC2 접속 (SSM 또는 ssh -i ~/.ssh/streamhub ec2-user@<dns>)
sudo dnf install -y 'dnf-command(copr)'; sudo dnf copr enable -y @caddy/caddy; sudo dnf install -y caddy
# /etc/caddy/Caddyfile
api.<당신>.duckdns.org {
    reverse_proxy localhost:8080
}
sudo systemctl enable --now caddy
```
- DuckDNS에 `api.<당신>` → EC2 퍼블릭 IP 등록.
- 보안그룹에 443 인바운드 추가(`api_ingress_cidr`는 443용으로도 열기).
- 그러면 `NEXT_PUBLIC_API_BASE_URL=https://api.<당신>.duckdns.org`, 백엔드 CORS의 허용 오리진을 Vercel URL로 맞추세요(`SecurityConfig`의 `corsConfigurationSource`).

> 빠른 임시 데모만 원하면: 프론트도 EC2에서 같이 서빙(동일 오리진)하거나, Cloudflare Tunnel(무료)로 API를 https로 노출하는 방법도 있습니다.

---

## 6. CI/CD (GitHub Actions)

- `.github/workflows/ci.yml` — PR/푸시마다 백엔드 `mvn verify` + 프론트 `npm run build`.
- `.github/workflows/deploy.yml` — 수동 실행 시 이미지 빌드→ECR push→SSM 재배포.
  필요한 레포 시크릿: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`, `ECR_REPOSITORY`(=`streamhub-api`), `EC2_INSTANCE_ID`.

---

## 7. 비용 & 철거

- 무료 티어 내: EC2 t2.micro + RDS db.t3.micro + S3 5GB ≈ $0 (12개월). 데이터 전송/초과 시 과금.
- **안 쓸 때**: `terraform destroy` (전부 삭제) — 가장 확실한 비용 차단.
- 데이터 보존하며 잠깐 끄기: EC2/RDS 콘솔에서 *중지*(RDS는 7일 후 자동 재시작 주의).

```bash
cd deploy/terraform && terraform destroy
```

---

## 변경 배포 흐름 요약

- 코드 수정 → `deploy/scripts/deploy-api.sh` (또는 GitHub Actions `Deploy API` 수동 실행).
- 인프라/환경변수 변경 → `terraform apply`.
- 프론트 → Vercel이 git push에 자동 배포.
