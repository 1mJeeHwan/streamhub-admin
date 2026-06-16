output "region" {
  description = "AWS region."
  value       = var.aws_region
}

output "api_public_dns" {
  description = "EC2 public DNS — the API base is http://<this>:8080"
  value       = aws_instance.api.public_dns
}

output "api_base_url" {
  description = "Set this as NEXT_PUBLIC_API_BASE_URL in Vercel."
  value       = "http://${aws_instance.api.public_dns}:8080"
}

output "ecr_repository_url" {
  description = "Push the API image here."
  value       = aws_ecr_repository.api.repository_url
}

output "rds_endpoint" {
  description = "RDS MySQL endpoint."
  value       = aws_db_instance.mysql.address
}

output "s3_bucket" {
  description = "Media bucket name."
  value       = aws_s3_bucket.media.bucket
}

output "ssh_command" {
  description = "SSH into the API host."
  value       = "ssh ec2-user@${aws_instance.api.public_dns}"
}
