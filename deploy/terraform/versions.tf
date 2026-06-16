terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
  # Use the named profile when set; when empty, fall back to the default chain
  # (env vars) — needed for `aws login` temporary sessions exported to env.
  profile = var.aws_profile != "" ? var.aws_profile : null
  default_tags {
    tags = {
      Project   = var.project
      ManagedBy = "terraform"
    }
  }
}
