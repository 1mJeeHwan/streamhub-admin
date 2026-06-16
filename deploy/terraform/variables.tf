variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "ap-northeast-2" # Seoul
}

variable "aws_profile" {
  description = "AWS CLI profile to deploy with. Use the new account's profile, not the default."
  type        = string
  default     = "streamhub"
}

variable "project" {
  description = "Resource name prefix."
  type        = string
  default     = "streamhub"
}

variable "s3_bucket_name" {
  description = "Globally-unique S3 bucket name for media uploads."
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type. Free-tier in ap-northeast-2 (Seoul) is t2.micro (NOT t3.micro)."
  type        = string
  default     = "t2.micro"
}

variable "db_instance_class" {
  description = "RDS instance class (free tier: db.t3.micro / db.t2.micro)."
  type        = string
  default     = "db.t3.micro"
}

variable "db_name" {
  description = "Initial database name."
  type        = string
  default     = "streamhub"
}

variable "db_username" {
  description = "RDS master username."
  type        = string
  default     = "streamhub"
}

variable "db_password" {
  description = "RDS master password (8+ chars). Provide via tfvars or TF_VAR_db_password."
  type        = string
  sensitive   = true
}

variable "jwt_secret" {
  description = "JWT signing secret (long random string). Provide via tfvars or TF_VAR_jwt_secret."
  type        = string
  sensitive   = true
}

variable "ssh_public_key" {
  description = "SSH public key contents for EC2 access (e.g. file(\"~/.ssh/id_ed25519.pub\"))."
  type        = string
}

variable "ssh_ingress_cidr" {
  description = "CIDR allowed to SSH to EC2. Set to your IP/32. Default is open — tighten it."
  type        = string
  default     = "0.0.0.0/0"
}

variable "api_ingress_cidr" {
  description = "CIDR allowed to reach the API on 8080 (Vercel/browser). Open by default for a public demo."
  type        = string
  default     = "0.0.0.0/0"
}
