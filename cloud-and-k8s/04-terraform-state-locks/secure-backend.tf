# THE FIX:
# S3 Backend med kryptering, versionshantering och DynamoDB State Locking.
# När 'terraform apply' initieras skapas en rad i DynamoDB-tabellen med LockID.
# Samtidiga körningar stoppas omedelbart med felkod.

terraform {
  required_version = ">= 1.5.0"

  backend "s3" {
    bucket         = "vault-terraform-state-prod"
    key            = "production/terraform.tfstate"
    region         = "eu-north-1"
    encrypt        = true
    dynamodb_table = "terraform-locks" # FIX: Låser tillståndet atomärt!
  }
}

# Terraform-kod för att skapa själva lås-tabellen och S3-hinken
resource "aws_dynamodb_table" "terraform_locks" {
  name         = "terraform-locks"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID" # Obligatorisk primärnyckel för Terraform

  attribute {
    name = "LockID"
    type = "S"
  }

  tags = {
    Environment = "Management"
    Purpose     = "Terraform State Locking"
  }
}

resource "aws_s3_bucket" "terraform_state" {
  bucket = "vault-terraform-state-prod"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "state_versioning" {
  bucket = aws_s3_bucket.terraform_state.id
  versioning_configuration {
    status = "Enabled" # Möjliggör återställning vid misstag
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state_encryption" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
    }
  }
}
