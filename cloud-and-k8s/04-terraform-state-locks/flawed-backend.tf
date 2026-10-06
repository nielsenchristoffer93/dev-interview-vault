# THE FLAW:
# S3 Backend deklarerad UTAN dynamodb_table.
# Ingen tillståndslåsning är aktiverad!
# Två pipelines kan köra 'terraform apply' exakt samtidigt
# och skriva sönder tfstate-filen.

terraform {
  backend "s3" {
    bucket = "my-company-terraform-states"
    key    = "prod/app.tfstate"
    region = "eu-north-1"
    # SAKNAS: dynamodb_table = "..."
  }
}
