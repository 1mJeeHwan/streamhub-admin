#!/usr/bin/env bash
# Build the API image for the EC2 architecture (linux/amd64), push to ECR,
# and roll the container on the instance via SSM Run Command (no SSH needed).
#
# Prereqs: `terraform apply` done; AWS CLI + Docker (buildx) configured.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TF_DIR="$ROOT/deploy/terraform"
API_DIR="$ROOT/streamhub-api"

cd "$TF_DIR"
ECR_URL="$(terraform output -raw ecr_repository_url)"
REGION="$(terraform output -raw region 2>/dev/null || aws configure get region || echo ap-northeast-2)"
INSTANCE_ID="$(aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=streamhub-api" "Name=instance-state-name,Values=running" \
  --query 'Reservations[0].Instances[0].InstanceId' --output text --region "$REGION")"

echo "ECR:      $ECR_URL"
echo "Region:   $REGION"
echo "Instance: $INSTANCE_ID"

# 1) Authenticate Docker to ECR.
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "${ECR_URL%/*}"

# 2) Build for the EC2 architecture and push.
docker buildx build --platform linux/amd64 -t "$ECR_URL:latest" "$API_DIR" --push

# 3) Roll the container on the instance (reuses the env baked at first boot).
CMD_ID="$(aws ssm send-command \
  --instance-ids "$INSTANCE_ID" \
  --document-name "AWS-RunShellScript" \
  --comment "redeploy streamhub-api" \
  --parameters 'commands=["/usr/local/bin/streamhub-deploy"]' \
  --query 'Command.CommandId' --output text --region "$REGION")"

echo "SSM command sent: $CMD_ID"
echo "API: $(terraform output -raw api_base_url)"
