# ---------------------------------------------------------------------------
# Networking: reuse the account's default VPC/subnets (no NAT gateway = no cost).
# ---------------------------------------------------------------------------
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

# AWS-managed prefix list of CloudFront's origin-facing IP ranges. Used to lock the
# API port (8080) so only CloudFront can reach the origin, not the whole internet.
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

# ---------------------------------------------------------------------------
# Security groups
# ---------------------------------------------------------------------------
resource "aws_security_group" "ec2" {
  name        = "${var.project}-ec2"
  description = "API host: SSH + API port"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_ingress_cidr]
  }
  ingress {
    description = "API (CloudFront origin-facing IPs only)"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    # Reachable ONLY from CloudFront's IP ranges — direct plaintext access bypassing the CDN is blocked.
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "rds" {
  name        = "${var.project}-rds"
  description = "MySQL from the API host only"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "MySQL from EC2"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ---------------------------------------------------------------------------
# S3 media bucket (real S3 — the AWS SDK code that targets MinIO locally works
# unchanged against this in prod).
# ---------------------------------------------------------------------------
resource "aws_s3_bucket" "media" {
  bucket = var.s3_bucket_name
}

resource "aws_s3_bucket_public_access_block" "media" {
  bucket                  = aws_s3_bucket.media.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_cors_configuration" "media" {
  bucket = aws_s3_bucket.media.id
  cors_rule {
    # Media reads only need GET/HEAD; uploads go through the API host, not the browser.
    allowed_methods = ["GET", "HEAD"]
    # Tightened to the known Vercel frontend origins (was "*").
    allowed_origins = ["https://streamhub-user.vercel.app", "https://streamhub-admin.vercel.app"]
    allowed_headers = ["*"]
    max_age_seconds = 3000
  }
}

# Lets CloudFront (OAC) read HLS segments from the private bucket. This is a
# service-principal policy scoped to this distribution's ARN (AWS:SourceArn) — it is
# NOT a public policy, so block_public_policy = true above still permits it.
data "aws_iam_policy_document" "media_cdn" {
  statement {
    sid       = "AllowCloudFrontReadHls"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.media.arn}/hls/*"]
    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.api.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "media_cdn" {
  bucket = aws_s3_bucket.media.id
  policy = data.aws_iam_policy_document.media_cdn.json
}

# ---------------------------------------------------------------------------
# ECR repository for the API image
# ---------------------------------------------------------------------------
resource "aws_ecr_repository" "api" {
  name                 = "${var.project}-api"
  image_tag_mutability = "MUTABLE"
  force_delete         = true
  image_scanning_configuration {
    scan_on_push = true
  }
}

# ---------------------------------------------------------------------------
# SQS — audit-log queue + dead-letter queue
# ---------------------------------------------------------------------------
resource "aws_sqs_queue" "action_log_dlq" {
  name = "${var.project}-action-log-dlq"
}

resource "aws_sqs_queue" "action_log" {
  name = "${var.project}-action-log"
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.action_log_dlq.arn
    maxReceiveCount     = 5
  })
}

# ---------------------------------------------------------------------------
# Secrets in SSM Parameter Store (SecureString) — fetched by EC2 at boot.
# ---------------------------------------------------------------------------
resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project}/db_password"
  type  = "SecureString"
  value = var.db_password
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/${var.project}/jwt_secret"
  type  = "SecureString"
  value = var.jwt_secret
}

# ---------------------------------------------------------------------------
# RDS MySQL (free tier: db.t3.micro, 20 GB)
# ---------------------------------------------------------------------------
resource "aws_db_subnet_group" "main" {
  name       = "${var.project}-db"
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_db_instance" "mysql" {
  identifier             = "${var.project}-mysql"
  engine                 = "mysql"
  engine_version         = "8.0"
  instance_class         = var.db_instance_class
  allocated_storage      = 20
  storage_type           = "gp2"
  db_name                = var.db_name
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false
  skip_final_snapshot    = true
  deletion_protection    = false
}

# ---------------------------------------------------------------------------
# IAM role for EC2: pull from ECR, read its two SSM secrets, use the S3 bucket.
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2" {
  name               = "${var.project}-ec2"
  assume_role_policy = data.aws_iam_policy_document.assume.json
}

data "aws_iam_policy_document" "ec2" {
  statement {
    sid = "EcrAuth"
    # GetAuthorizationToken is account-scoped; AWS requires resources = ["*"].
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }
  statement {
    sid = "EcrPull"
    actions = [
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [aws_ecr_repository.api.arn]
  }
  statement {
    sid       = "ReadSecrets"
    actions   = ["ssm:GetParameter", "ssm:GetParameters"]
    resources = [aws_ssm_parameter.db_password.arn, aws_ssm_parameter.jwt_secret.arn]
  }
  statement {
    sid       = "S3Bucket"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket"]
    resources = [aws_s3_bucket.media.arn, "${aws_s3_bucket.media.arn}/*"]
  }
  statement {
    sid = "ActionLogQueue"
    actions = [
      "sqs:SendMessage",
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueUrl",
      "sqs:GetQueueAttributes",
      "sqs:CreateQueue",
    ]
    resources = [aws_sqs_queue.action_log.arn, aws_sqs_queue.action_log_dlq.arn]
  }
}

resource "aws_iam_role_policy" "ec2" {
  name   = "${var.project}-ec2"
  role   = aws_iam_role.ec2.id
  policy = data.aws_iam_policy_document.ec2.json
}

# Enables SSM Session Manager + Run Command (deploys without SSH).
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.project}-ec2"
  role = aws_iam_role.ec2.name
}

# ---------------------------------------------------------------------------
# EC2 API host
# ---------------------------------------------------------------------------
resource "aws_key_pair" "main" {
  key_name   = "${var.project}-key"
  public_key = var.ssh_public_key
}

resource "aws_instance" "api" {
  ami                    = data.aws_ssm_parameter.al2023.value
  instance_type          = var.instance_type
  subnet_id              = data.aws_subnets.default.ids[0]
  vpc_security_group_ids = [aws_security_group.ec2.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name
  key_name               = aws_key_pair.main.key_name

  # Enforce IMDSv2 (token-required). The live instance already enforces this;
  # pinning it here prevents drift on relaunch. hop_limit 2 lets containers reach IMDS.
  metadata_options {
    http_tokens                 = "required"
    http_endpoint               = "enabled"
    http_put_response_hop_limit = 2
  }

  # A small swap file relieves heap pressure on the 1 GB instance.
  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    region            = var.aws_region
    ecr_url           = aws_ecr_repository.api.repository_url
    ecr_registry      = split("/", aws_ecr_repository.api.repository_url)[0]
    db_host           = aws_db_instance.mysql.address
    db_name           = var.db_name
    db_user           = var.db_username
    s3_bucket         = aws_s3_bucket.media.bucket
    sqs_queue         = aws_sqs_queue.action_log.name
    db_password_param = aws_ssm_parameter.db_password.name
    jwt_secret_param  = aws_ssm_parameter.jwt_secret.name
  })

  tags = { Name = "${var.project}-api" }
}
